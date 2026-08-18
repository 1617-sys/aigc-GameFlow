package aigc.gameflow.mq;

import aigc.gameflow.config.RabbitConfig;
import aigc.gameflow.image.GenerationEventType;
import aigc.gameflow.image.GenerationStatus;
import aigc.gameflow.image.ImageGenerationResult;
import aigc.gameflow.mapper.GenTaskMapper;
import aigc.gameflow.model.entity.GenTask;
import aigc.gameflow.service.CallbackService;
import aigc.gameflow.service.GenerationEventService;
import aigc.gameflow.service.ImageGenerationService;
import aigc.gameflow.service.TaskCacheService;
import aigc.gameflow.service.TaskLeaseService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 生成任务消费者：领取数据库租约、执行图片生成，并根据结果 ACK、重试或进入死信队列。
 */
@Component
@Slf4j
public class TaskListener {

    private final GenTaskMapper genTaskMapper;
    private final ImageGenerationService imageGenerationService;
    private final CallbackService callbackService;
    private final GenerationEventService generationEventService;
    private final TaskCacheService taskCacheService;
    private final TaskLeaseService taskLeaseService;
    private final RabbitTemplate rabbitTemplate;
    private final int maxRetries;

    public TaskListener(
            GenTaskMapper genTaskMapper,
            ImageGenerationService imageGenerationService,
            CallbackService callbackService,
            GenerationEventService generationEventService,
            TaskCacheService taskCacheService,
            TaskLeaseService taskLeaseService,
            RabbitTemplate rabbitTemplate,
            @Value("${generation.retry.max-attempts:3}") int maxRetries
    ) {
        this.genTaskMapper = genTaskMapper;
        this.imageGenerationService = imageGenerationService;
        this.callbackService = callbackService;
        this.generationEventService = generationEventService;
        this.taskCacheService = taskCacheService;
        this.taskLeaseService = taskLeaseService;
        this.rabbitTemplate = rabbitTemplate;
        this.maxRetries = maxRetries;
    }

    @RabbitListener(queues = RabbitConfig.TASK_QUEUE, ackMode = "MANUAL")
    public void onMessage(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        String taskUuid = new String(message.getBody(), StandardCharsets.UTF_8);
        String workerId = UUID.randomUUID().toString();

        try {
            processTask(taskUuid, workerId);
            // 只有处理流程正常结束后才确认消息，进程中途退出时 RabbitMQ 可重新投递。
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            GenTask runningTask = findTask(taskUuid);
            int databaseRetries = runningTask == null || runningTask.getRetryCount() == null
                    ? 0
                    : runningTask.getRetryCount();
            int retries = Math.max(retryCount(message), databaseRetries);
            String error = truncate(e.getMessage());
            log.error("Generation task failed, taskUuid={}, retry={}, error={}",
                    taskUuid, retries, error, e);

            if (retries < maxRetries) {
                GenTask retrying = transitionFromRunning(
                        taskUuid,
                        workerId,
                        GenerationStatus.RETRYING,
                        error,
                        retries + 1
                );
                if (retrying == null) {
                    channel.basicAck(deliveryTag, false);
                    return;
                }
                generationEventService.record(retrying, GenerationEventType.TASK_RETRY_SCHEDULED,
                        "Retry " + (retries + 1) + " scheduled after provider failure");
                channel.basicNack(deliveryTag, false, false);
                return;
            }

            GenTask failed = transitionFromRunning(
                    taskUuid,
                    workerId,
                    GenerationStatus.FAILED,
                    error,
                    retries
            );
            if (failed == null) {
                channel.basicAck(deliveryTag, false);
                return;
            }
            generationEventService.record(failed, GenerationEventType.TASK_FAILED, error);
            generationEventService.record(failed, GenerationEventType.TASK_DEAD_LETTERED,
                    "Retry limit exceeded; message moved to DLQ");
            callbackService.notifyIfNeeded(failed);
            message.getMessageProperties().setHeader("finalError", error);
            rabbitTemplate.send(RabbitConfig.DLX_EXCHANGE, RabbitConfig.DLQ_ROUTING_KEY, message);
            channel.basicAck(deliveryTag, false);
        }
    }

    private void processTask(String taskUuid, String workerId) {
        // 数据库条件更新相当于“抢占执行权”，防止重复消息被多个消费者同时执行。
        if (!taskLeaseService.claim(taskUuid, workerId)) {
            log.info("Task message skipped because task was already claimed or completed, taskUuid={}", taskUuid);
            return;
        }

        GenTask task = findTask(taskUuid);
        if (task == null) {
            throw new IllegalStateException("Generation task not found after claim");
        }
        cacheTask(task);
        generationEventService.record(task, GenerationEventType.TASK_RUNNING, "Generation task started");

        ImageGenerationResult result;
        // 图片生成可能耗时较长，心跳持续延长租约；try-with-resources 保证结束后停止心跳。
        try (TaskLeaseService.LeaseHeartbeat ignored = taskLeaseService.startHeartbeat(taskUuid, workerId)) {
            result = imageGenerationService.generateAndStore(task);
        }

        // 成功结果必须仍由当前 worker 持有且租约未过期，迟到结果不能覆盖恢复后的状态。
        int completed = genTaskMapper.update(
                null,
                new LambdaUpdateWrapper<GenTask>()
                        .set(GenTask::getStatus, GenerationStatus.SUCCESS.code())
                        .set(GenTask::getImageUrl, result.getRemoteImageUrl())
                        .set(GenTask::getProvider, result.getProvider().name())
                        .set(GenTask::getModel, result.getModel())
                        .set(GenTask::getProviderJobId, result.getProviderJobId())
                        .set(GenTask::getLatencyMs, result.getLatencyMs())
                        .set(GenTask::getErrorMsg, null)
                        .set(GenTask::getWorkerId, null)
                        .set(GenTask::getLeaseExpireTime, null)
                        .set(GenTask::getLastHeartbeatTime, null)
                        .set(GenTask::getUpdateTime, LocalDateTime.now())
                        .setSql("version = version + 1")
                        .eq(GenTask::getTaskUuid, taskUuid)
                        .eq(GenTask::getStatus, GenerationStatus.RUNNING.code())
                        .eq(GenTask::getWorkerId, workerId)
                        .gt(GenTask::getLeaseExpireTime, LocalDateTime.now())
        );
        GenTask latest = findTask(taskUuid);
        if (completed != 1) {
            generationEventService.record(latest, GenerationEventType.TASK_RESULT_IGNORED,
                    "Late generation result ignored because task ownership or status changed");
            cacheTask(latest);
            return;
        }

        cacheTask(latest);
        generationEventService.record(latest, GenerationEventType.TASK_SUCCESS,
                "Generation task completed", result);
        callbackService.notifyIfNeeded(latest);
    }

    private GenTask transitionFromRunning(
            String taskUuid,
            String workerId,
            GenerationStatus target,
            String error,
            int retries
    ) {
        int updated = genTaskMapper.transitionOwnedStatus(
                taskUuid,
                workerId,
                GenerationStatus.RUNNING.code(),
                target.code(),
                error,
                retries,
                LocalDateTime.now()
        );
        if (updated != 1) {
            log.info("Task failure result ignored because status changed, taskUuid={}", taskUuid);
            return null;
        }
        GenTask task = findTask(taskUuid);
        cacheTask(task);
        return task;
    }

    private int retryCount(Message message) {
        List<Map<String, ?>> deaths = message.getMessageProperties().getXDeathHeader();
        if (deaths == null) {
            return 0;
        }
        return deaths.stream()
                .filter(entry -> RabbitConfig.TASK_QUEUE.equals(entry.get("queue")))
                .map(entry -> entry.get("count"))
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .mapToInt(Number::intValue)
                .max()
                .orElse(0);
    }

    private GenTask findTask(String taskUuid) {
        return genTaskMapper.selectOne(new QueryWrapper<GenTask>().eq("task_uuid", taskUuid));
    }

    private void cacheTask(GenTask task) {
        taskCacheService.put(task);
    }

    private String truncate(String value) {
        String message = value == null ? "Unknown generation error" : value;
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
