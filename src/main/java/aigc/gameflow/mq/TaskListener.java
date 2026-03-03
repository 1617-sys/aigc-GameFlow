package aigc.gameflow.mq;

import aigc.gameflow.mapper.GenTaskMapper;
import aigc.gameflow.model.entity.GenTask;
import aigc.gameflow.service.ComfyUiService;
import aigc.gameflow.service.GameAssetService;
import aigc.gameflow.service.MinioService;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
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
    private RedisTemplate redisTemplate;

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
        genTaskMapper.updateById(task);

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
                try {
                    // A. 构造 ComfyUI 的下载链接
                    String downloadUrl = "http://127.0.0.1:8000/view?filename=" + filename;

                    // B. 打开网络流 (这里用 Java 原生 URL 读取最简单)
                    java.net.URL url = new java.net.URL(downloadUrl);
                    java.io.InputStream inputStream = url.openStream();

                    // C. 传给 MinIO，拿到永久链接
                    String minioUrl = minioService.uploadImage(inputStream, filename);

                    // D. 记得关闭流！(虽然 MinIO SDK 可能会关，但手动关是个好习惯)
                    inputStream.close();

                    // E. 存入数据库
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
            // 注意：这里不要再 setImageUrl 了，因为上面循环里已经 set 过了
            genTaskMapper.updateById(task);
            log.info("🎉 任务数据库状态更新完成");
        } else {
            // 超时处理
            throw new RuntimeException("ComfyUI 生成超时 (300s)，请检查 ComfyUI 控制台是否有报错");
        }
    }

    // 辅助方法：更新状态
    private void updateTaskStatus(String uuid, int status, String msg) {
        GenTask task = GenTask.builder()
                .status(status)
                .errorMsg(msg)
                .build();
        genTaskMapper.update(task,new LambdaUpdateWrapper<GenTask>().eq(GenTask::getTaskUuid,uuid));
    }

    // 在 TaskListener.java 中注入 RedisTemplate

    private void updateTaskStatus(GenTask task) {
        // 1. 更新数据库 (持久化兜底)
        genTaskMapper.updateById(task);

        // 2. 更新 Redis (热数据)
        // Key: task:status:{uuid}  过期时间: 30分钟 (任务完成后没必要一直存Redis)
        String cacheKey = "task:info:" + task.getTaskUuid();
        redisTemplate.opsForValue().set(cacheKey, task, 30, TimeUnit.MINUTES);
    }
}
