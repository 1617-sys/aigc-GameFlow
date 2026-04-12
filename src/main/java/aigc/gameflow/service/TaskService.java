package aigc.gameflow.service;

import aigc.gameflow.config.RabbitConfig;
import aigc.gameflow.mapper.GenTaskMapper;
import aigc.gameflow.mapper.SysUserMapper;
import aigc.gameflow.model.entity.GenTask;
import aigc.gameflow.model.entity.SysUser;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j

public class TaskService {
    private static final int DEFAULT_TASK_PAGE_SIZE = 20;

    @Autowired
    private GenTaskMapper genTaskMapper;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    public Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new IllegalArgumentException("未登录，无法访问任务");
        }
        return userId;
    }

    public GenTask getCurrentUserTask(String uuid) {
        GenTask task = genTaskMapper.selectOne(
                new QueryWrapper<GenTask>()
                        .eq("task_uuid", uuid)
                        .eq("user_id", getCurrentUserId())
                        .eq("is_deleted", 0)
        );

        if (task == null) {
            throw new IllegalArgumentException("任务不存在或无权限访问");
        }

        return task;
    }

    public List<GenTask> listCurrentUserTasks() {
        return genTaskMapper.selectList(
                new QueryWrapper<GenTask>()
                        .eq("user_id", getCurrentUserId())
                        .eq("is_deleted", 0)
                        .orderByDesc("create_time")
                        .last("limit " + DEFAULT_TASK_PAGE_SIZE)
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public String submitTask(String prompt) {
        Long userId = getCurrentUserId();

        String limitKey = "limit:submit:" + userId;
        Boolean isAllowed = redisTemplate.opsForValue().setIfAbsent(limitKey, "1", 5, TimeUnit.SECONDS);

        if (Boolean.FALSE.equals(isAllowed)) {
            throw new IllegalArgumentException("操作过于频繁，请 5 秒后再试");
        }

        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("当前用户不存在");
        }

        Integer balance = user.getBalance();
        if (balance == null || balance <= 0) {
            throw new IllegalArgumentException("余额不足");
        }
        user.setBalance(user.getBalance() - 1);
        sysUserMapper.updateById(user);

        String taskUuid = UUID.randomUUID().toString();
        GenTask genTask = GenTask.builder()
                .taskUuid(taskUuid)
                .prompt(prompt.trim())
                .status(0)
                .userId(userId)
                .createTime(LocalDateTime.now())
                .build();

        genTaskMapper.insert(genTask);
        log.info("✅ 任务已入库, ID: {}", taskUuid);

        rabbitTemplate.convertAndSend(RabbitConfig.TASK_QUEUE, taskUuid);
        log.info("🚀 任务已发送至 MQ队列: {}", RabbitConfig.TASK_QUEUE);

        return taskUuid;
    }

}
