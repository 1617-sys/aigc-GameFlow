package aigc.gameflow.service;

import aigc.gameflow.config.RabbitConfig;
import aigc.gameflow.mapper.GenTaskMapper;
import aigc.gameflow.mapper.SysUserMapper;
import aigc.gameflow.model.entity.GenTask;
import aigc.gameflow.model.entity.SysUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j

public class TaskService {
    @Autowired
    private GenTaskMapper genTaskMapper;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    //获取当前用户：从 SecurityContextHolder 获取当前登录的 userId。
    //扣费逻辑：先查余额 -> 余额>0 -> 扣1分 -> 存任务。如果余额不足，直接抛异常。
    //入库逻辑：task.setUserId(currentUserId)。


    /**
     * 根据UUID查询任务信息
     * @param uuid 任务UUID
     * @return GenTask 任务对象
     */
    public GenTask getByUuid(String uuid) {
        return genTaskMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<GenTask>()
                .eq("task_uuid", uuid)
                .eq("is_deleted", 0)
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public String submitTask(String prompt) {
        // 获取当前用户：从 SecurityContextHolder 获取当前登录的 userId
        SecurityContext context = SecurityContextHolder.getContext();
        Long userId = (Long) context.getAuthentication().getPrincipal();

        if (userId == null) {
            throw new RuntimeException("未登录，无法提交任务");
        }

        // === 🛑 Redis 限流开始 ===
        // Key 格式规范：业务前缀:用户ID
        String limitKey = "limit:submit:" + userId;

        // SETNX (Set If Not Exists)
        // 尝试设置 Key，如果不存在则成功并设置 5秒过期；如果存在则失败
        Boolean isAllowed = redisTemplate.opsForValue().setIfAbsent(limitKey, "1", 5, TimeUnit.SECONDS);

        if (Boolean.FALSE.equals(isAllowed)) {
            // 抛出异常，Controller 会捕获并返回错误信息
            throw new RuntimeException("操作过于频繁，请 5 秒后再试！");
        }

        // === 💰 扣费逻辑 ===
        SysUser user = sysUserMapper.selectById(userId);
        Integer balance = user.getBalance();
        if (balance <= 0) {
            throw new RuntimeException("余额不足");
        }
        user.setBalance(user.getBalance() - 1);
        sysUserMapper.updateById(user);

        // === 📦 入库逻辑 ===
        String taskUuid = UUID.randomUUID().toString();
        GenTask genTask = GenTask.builder()
                .taskUuid(taskUuid)
                .prompt(prompt)
                .status(0)
                .createTime(LocalDateTime.now())
                .build();

        genTaskMapper.insert(genTask);
        log.info("✅ 任务已入库, ID: {}", taskUuid);

        // === 🚀 发送消息到队列 ===
        rabbitTemplate.convertAndSend(RabbitConfig.TASK_QUEUE, taskUuid);
        log.info("🚀 任务已发送至 MQ队列: {}", RabbitConfig.TASK_QUEUE);

        return taskUuid;
    }

}