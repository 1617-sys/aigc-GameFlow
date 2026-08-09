package aigc.gameflow.service;

import aigc.gameflow.config.RabbitConfig;
import aigc.gameflow.image.GenerationEventType;
import aigc.gameflow.mapper.GenTaskMapper;
import aigc.gameflow.model.entity.GenTask;
import aigc.gameflow.model.entity.GenerationOutbox;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class OutboxRelayService {

    private final GenerationOutboxService outboxService;
    private final GenTaskMapper genTaskMapper;
    private final RabbitTemplate rabbitTemplate;
    private final GenerationEventService generationEventService;
    private final boolean relayEnabled;
    private final int batchSize;
    private final long lockSeconds;
    private final long confirmTimeoutSeconds;
    private final long maxBackoffSeconds;
    private final String relayWorkerId = UUID.randomUUID().toString();

    public OutboxRelayService(
            GenerationOutboxService outboxService,
            GenTaskMapper genTaskMapper,
            RabbitTemplate rabbitTemplate,
            GenerationEventService generationEventService,
            @Value("${generation.outbox.relay-enabled:true}") boolean relayEnabled,
            @Value("${generation.outbox.batch-size:50}") int batchSize,
            @Value("${generation.outbox.lock-seconds:30}") long lockSeconds,
            @Value("${generation.outbox.confirm-timeout-seconds:5}") long confirmTimeoutSeconds,
            @Value("${generation.outbox.max-backoff-seconds:300}") long maxBackoffSeconds
    ) {
        this.outboxService = outboxService;
        this.genTaskMapper = genTaskMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.generationEventService = generationEventService;
        this.relayEnabled = relayEnabled;
        this.batchSize = batchSize;
        this.lockSeconds = lockSeconds;
        this.confirmTimeoutSeconds = confirmTimeoutSeconds;
        this.maxBackoffSeconds = maxBackoffSeconds;
    }

    @Scheduled(
            initialDelayString = "${generation.outbox.initial-delay-ms:1000}",
            fixedDelayString = "${generation.outbox.fixed-delay-ms:500}"
    )
    public void relayScheduled() {
        if (relayEnabled) {
            publishBatch();
        }
    }

    public int publishBatch() {
        List<GenerationOutbox> dueEvents = outboxService.findDue(batchSize);
        int sent = 0;
        for (GenerationOutbox event : dueEvents) {
            if (!outboxService.claim(
                    event.getEventId(),
                    relayWorkerId,
                    LocalDateTime.now().plusSeconds(lockSeconds)
            )) {
                continue;
            }

            try {
                publish(event);
                if (outboxService.markSent(event.getEventId(), relayWorkerId)) {
                    sent++;
                    GenTask task = findTask(event.getTaskUuid());
                    generationEventService.record(task, GenerationEventType.TASK_QUEUED,
                            "Generation task published from transactional outbox");
                }
            } catch (Exception e) {
                long delay = retryDelaySeconds(event.getRetryCount());
                outboxService.scheduleRetry(
                        event.getEventId(),
                        relayWorkerId,
                        LocalDateTime.now().plusSeconds(delay),
                        e.getMessage()
                );
                log.warn("Outbox publish failed, eventId={}, taskUuid={}, retryInSeconds={}, error={}",
                        event.getEventId(), event.getTaskUuid(), delay, e.getMessage());
            }
        }
        return sent;
    }

    private void publish(GenerationOutbox event) throws Exception {
        if (!GenerationOutboxService.EVENT_EXECUTE_TASK.equals(event.getEventType())) {
            throw new IllegalArgumentException("Unsupported outbox event type: " + event.getEventType());
        }

        CorrelationData correlationData = new CorrelationData(event.getEventId());
        rabbitTemplate.convertAndSend(
                RabbitConfig.GENERATION_EXCHANGE,
                RabbitConfig.EXECUTE_ROUTING_KEY,
                event.getPayload(),
                message -> {
                    message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    message.getMessageProperties().setMessageId(event.getEventId());
                    message.getMessageProperties().setHeader("traceId", event.getTraceId());
                    message.getMessageProperties().setHeader("outboxEventId", event.getEventId());
                    return message;
                },
                correlationData
        );

        CorrelationData.Confirm confirm = correlationData.getFuture()
                .get(confirmTimeoutSeconds, TimeUnit.SECONDS);
        if (!confirm.isAck()) {
            throw new IllegalStateException("RabbitMQ rejected outbox event: " + confirm.getReason());
        }
        if (correlationData.getReturned() != null) {
            throw new IllegalStateException(
                    "RabbitMQ returned unroutable outbox event: "
                            + correlationData.getReturned().getReplyText()
            );
        }
    }

    private long retryDelaySeconds(Integer retryCount) {
        int exponent = Math.min(retryCount == null ? 0 : retryCount, 20);
        long delay = 1L << exponent;
        return Math.min(Math.max(1, delay), maxBackoffSeconds);
    }

    private GenTask findTask(String taskUuid) {
        return genTaskMapper.selectOne(new QueryWrapper<GenTask>().eq("task_uuid", taskUuid));
    }
}
