package aigc.gameflow.mq;

import aigc.gameflow.image.GenerationStatus;
import aigc.gameflow.image.GenerationEventType;
import aigc.gameflow.image.ImageGenerationResult;
import aigc.gameflow.mapper.GenTaskMapper;
import aigc.gameflow.model.entity.GenTask;
import aigc.gameflow.service.CallbackService;
import aigc.gameflow.service.GenerationEventService;
import aigc.gameflow.service.ImageGenerationService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class TaskListener {

    private final GenTaskMapper genTaskMapper;
    private final ImageGenerationService imageGenerationService;
    private final CallbackService callbackService;
    private final GenerationEventService generationEventService;
    private final RedisTemplate<String, Object> redisTemplate;

    public TaskListener(
            GenTaskMapper genTaskMapper,
            ImageGenerationService imageGenerationService,
            CallbackService callbackService,
            GenerationEventService generationEventService,
            RedisTemplate<String, Object> redisTemplate
    ) {
        this.genTaskMapper = genTaskMapper;
        this.imageGenerationService = imageGenerationService;
        this.callbackService = callbackService;
        this.generationEventService = generationEventService;
        this.redisTemplate = redisTemplate;
    }

    @RabbitListener(queues = "aigc.task.queue", ackMode = "MANUAL")
    public void onMessage(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        String taskUuid = new String(message.getBody());
        log.info("Received generation task, taskUuid={}", taskUuid);

        try {
            processTask(taskUuid);
            channel.basicAck(deliveryTag, false);
            log.info("Generation task acked, taskUuid={}", taskUuid);
        } catch (Exception e) {
            log.error("Generation task failed, taskUuid={}, error={}", taskUuid, e.getMessage(), e);
            GenTask failedTask = updateTaskStatus(taskUuid, GenerationStatus.FAILED, e.getMessage());
            if (failedTask != null) {
                generationEventService.record(failedTask, GenerationEventType.TASK_FAILED, e.getMessage());
                callbackService.notifyIfNeeded(failedTask);
            }
            channel.basicNack(deliveryTag, false, false);
        }
    }

    private void processTask(String taskUuid) {
        GenTask task = genTaskMapper.selectOne(
                new QueryWrapper<GenTask>().eq("task_uuid", taskUuid)
        );

        if (task == null) {
            log.warn("Generation task not found, taskUuid={}", taskUuid);
            return;
        }

        if (GenerationStatus.SUCCESS.code() == safeStatus(task)) {
            log.info("Generation task already completed, taskUuid={}", taskUuid);
            return;
        }

        if (GenerationStatus.CANCELED.code() == safeStatus(task)) {
            generationEventService.record(task, GenerationEventType.TASK_CANCELED, "Canceled task skipped by consumer");
            log.info("Generation task canceled before running, taskUuid={}", taskUuid);
            return;
        }

        task.setStatus(GenerationStatus.RUNNING.code());
        task.setUpdateTime(LocalDateTime.now());
        updateTaskStatus(task);
        generationEventService.record(task, GenerationEventType.TASK_RUNNING, "Generation task started");

        ImageGenerationResult result = imageGenerationService.generateAndStore(task);

        GenTask latest = genTaskMapper.selectOne(new QueryWrapper<GenTask>().eq("task_uuid", taskUuid));
        if (latest != null && GenerationStatus.CANCELED.code() == safeStatus(latest)) {
            generationEventService.record(latest, GenerationEventType.TASK_CANCELED, "Generation result ignored because task was canceled");
            return;
        }

        task.setStatus(GenerationStatus.SUCCESS.code());
        task.setImageUrl(result.getRemoteImageUrl());
        task.setProvider(result.getProvider().name());
        task.setModel(result.getModel());
        task.setProviderJobId(result.getProviderJobId());
        task.setLatencyMs(result.getLatencyMs());
        task.setErrorMsg(null);
        task.setUpdateTime(LocalDateTime.now());
        updateTaskStatus(task);
        generationEventService.record(task, GenerationEventType.TASK_SUCCESS, "Generation task completed", result);
        callbackService.notifyIfNeeded(task);
    }

    private GenTask updateTaskStatus(String uuid, GenerationStatus status, String msg) {
        GenTask task = genTaskMapper.selectOne(
                new QueryWrapper<GenTask>().eq("task_uuid", uuid)
        );

        if (task == null) {
            genTaskMapper.update(
                    GenTask.builder()
                            .status(status.code())
                            .errorMsg(msg)
                            .updateTime(LocalDateTime.now())
                            .build(),
                    new LambdaUpdateWrapper<GenTask>().eq(GenTask::getTaskUuid, uuid)
            );
            return null;
        }

        task.setStatus(status.code());
        task.setErrorMsg(msg);
        task.setUpdateTime(LocalDateTime.now());
        updateTaskStatus(task);
        return task;
    }

    private void updateTaskStatus(GenTask task) {
        genTaskMapper.updateById(task);
        String cacheKey = "task:info:" + task.getUserId() + ":" + task.getTaskUuid();
        redisTemplate.opsForValue().set(cacheKey, task, 30, TimeUnit.MINUTES);
    }

    private int safeStatus(GenTask task) {
        return task.getStatus() == null ? GenerationStatus.PENDING.code() : task.getStatus();
    }
}
