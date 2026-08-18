package aigc.gameflow.service;

import aigc.gameflow.config.RabbitConfig;
import aigc.gameflow.exception.ServiceOverloadedException;
import com.rabbitmq.client.AMQP;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * RabbitMQ 背压保护：后台定时采样队列积压，提交线程只读取内存快照。
 */
@Service
@Slf4j
public class QueueBackpressureGuard {

    private final RabbitTemplate rabbitTemplate;
    private final boolean enabled;
    private final long maxBacklog;
    private final AtomicLong backlog = new AtomicLong();
    private final AtomicBoolean available = new AtomicBoolean(false);

    public QueueBackpressureGuard(
            RabbitTemplate rabbitTemplate,
            @Value("${generation.backpressure.enabled:true}") boolean enabled,
            @Value("${generation.backpressure.max-backlog:5000}") long maxBacklog
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.enabled = enabled;
        this.maxBacklog = maxBacklog;
    }

    @Scheduled(
            initialDelayString = "${generation.backpressure.initial-delay-ms:0}",
            fixedDelayString = "${generation.backpressure.refresh-ms:1000}"
    )
    public void refresh() {
        if (!enabled) {
            available.set(true);
            return;
        }
        try {
            // 被动声明只读取已有队列状态，不会创建或修改队列。
            Long currentBacklog = rabbitTemplate.execute(channel -> {
                AMQP.Queue.DeclareOk execute = channel.queueDeclarePassive(RabbitConfig.TASK_QUEUE);
                AMQP.Queue.DeclareOk retry = channel.queueDeclarePassive(RabbitConfig.RETRY_QUEUE);
                return (long) execute.getMessageCount() + retry.getMessageCount();
            });
            if (currentBacklog == null) {
                available.set(false);
                return;
            }
            backlog.set(currentBacklog);
            available.set(true);
        } catch (Exception e) {
            available.set(false);
            log.warn("RabbitMQ backlog sampling failed: {}", e.getMessage());
        }
    }

    public void checkAcceptingNewTasks() {
        // 提交请求不直接访问 RabbitMQ，避免 Broker 波动拖慢所有 HTTP 请求。
        if (!enabled) {
            return;
        }
        if (!available.get()) {
            throw new ServiceOverloadedException("Task queue is temporarily unavailable");
        }
        if (backlog.get() >= maxBacklog) {
            throw new ServiceOverloadedException("Task queue is full, retry later");
        }
    }

    void updateSnapshot(long currentBacklog, boolean queueAvailable) {
        backlog.set(currentBacklog);
        available.set(queueAvailable);
    }
}
