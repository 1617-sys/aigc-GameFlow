package aigc.gameflow.mq;

import aigc.gameflow.mapper.GenTaskMapper;
import aigc.gameflow.model.entity.GenTask;
import aigc.gameflow.service.ComfyUiService;
import aigc.gameflow.service.GameAssetService;
import aigc.gameflow.service.MinioService;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class TaskListener {

    @Autowired
    private GenTaskMapper genTaskMapper; // 操作数据库

    @Autowired
    private GameAssetService gameAssetService; // 负责调 DeepSeek + 拼 JSON

    @Autowired
    private ComfyUiService comfyUiService; // 负责调 ComfyUI 接口

    @Autowired
    private MinioService minioService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // 监听队列：aigc.task.queue
    @RabbitListener(queues = "aigc.task.queue", ackMode = "MANUAL")
    public void onMessage(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        String taskUuid = new String(message.getBody());

        log.info("🔔 收到 MQ 消息，开始处理任务: {}", taskUuid);

        try {
            // === 核心业务逻辑开始 ===
            processTask(taskUuid);
            // === 核心业务逻辑结束 ===

            // ✅ 成功签收：告诉 MQ 这条消息处理完了，可以删了
            channel.basicAck(deliveryTag, false);
            log.info("✅ 任务完成，消息已 ACK");

        } catch (Exception e) {
            log.error("❌ 任务处理失败: {}", e.getMessage(), e);

            // 更新数据库为失败状态
            updateTaskStatus(taskUuid, 3, "执行异常: " + e.getMessage());

            // ❌ 拒绝签收：false 表示不重回队列（直接丢弃或进死信）
            // 生产环境通常设为 false，防止毒消息死循环导致系统瘫痪
            channel.basicNack(deliveryTag, false, false);
        }
    }

    // ... 下面是具体的处理逻辑方法
    private void processTask(String taskUuid) throws InterruptedException {
        // 1. 查数据库，确保任务存在
        GenTask task = genTaskMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<GenTask>()
                        .eq("task_uuid", taskUuid)
        );

        if (task == null) {
            log.warn("数据库没找到这个任务: {}, 直接跳过", taskUuid);
            return;
        }

        // 2. 更新状态：排队中(0) -> 生成中(1)
        task.setStatus(1);
        task.setUpdateTime(LocalDateTime.now());
        updateTaskStatus(task);

        // 3. 调用 AI + ComfyUI 发起生图
        String promptId = gameAssetService.generateByText(task.getPrompt());
        log.info("ComfyUI 已接单，Prompt ID: {}", promptId);

        // 4. 真实轮询逻辑
        boolean isSuccess = false;
        long startTime = System.currentTimeMillis();
        // 设置超时时间 300秒 (5分钟)，适应更复杂的生成任务
        while ((System.currentTimeMillis() - startTime) < 300 * 1000) {

            // 4.1 去问 ComfyUI 好了没
            String filename = comfyUiService.getImageFilename(promptId);

            if (filename != null) {
                log.info("✅ ComfyUI 生成完毕，文件名: {}", filename);

                // === 核心搬运逻辑开始 ===
                try (java.io.InputStream inputStream =
                             new java.net.URL(comfyUiService.buildImageViewUrl(filename)).openStream()) {
                    String minioUrl = minioService.uploadImage(inputStream, filename);
                    task.setImageUrl(minioUrl); // 存入 MinIO 的链接
                } catch (Exception e) {
                    log.error("图片搬运失败", e);
                    throw new RuntimeException("图片上传 MinIO 失败");
                }
                // === 核心搬运逻辑结束 ===

                isSuccess = true;
                break;
            }

            // 4.3 没好，睡 2 秒再问 (减少轮询频率，降低服务器压力)
            Thread.sleep(2000);
            log.info("⏳ 正在等待 ComfyUI 生成...");
        }

        // 5. 结果处理
        if (isSuccess) {
            // 更新数据库 -> 成功(2)
            task.setStatus(2);
            task.setUpdateTime(LocalDateTime.now());
            updateTaskStatus(task);
            log.info("🎉 任务数据库状态更新完成");
        } else {
            // 超时处理
            throw new RuntimeException("ComfyUI 生成超时 (300s)，请检查 ComfyUI 控制台是否有报错");
        }
    }

    // 辅助方法：更新状态
    private void updateTaskStatus(String uuid, int status, String msg) {
        GenTask task = genTaskMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<GenTask>()
                        .eq("task_uuid", uuid)
        );

        if (task == null) {
            genTaskMapper.update(
                    GenTask.builder().status(status).errorMsg(msg).build(),
                    new LambdaUpdateWrapper<GenTask>().eq(GenTask::getTaskUuid, uuid)
            );
            return;
        }

        task.setStatus(status);
        task.setErrorMsg(msg);
        task.setUpdateTime(LocalDateTime.now());
        updateTaskStatus(task);
    }

    // 在 TaskListener.java 中注入 RedisTemplate

    private void updateTaskStatus(GenTask task) {
        genTaskMapper.updateById(task);

        String cacheKey = "task:info:" + task.getUserId() + ":" + task.getTaskUuid();
        redisTemplate.opsForValue().set(cacheKey, task, 30, TimeUnit.MINUTES);
    }
}
