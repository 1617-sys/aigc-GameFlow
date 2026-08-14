package aigc.gameflow.service;

import aigc.gameflow.mapper.GenerationOutboxMapper;
import aigc.gameflow.model.entity.GenTask;
import aigc.gameflow.model.entity.GenerationOutbox;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class GenerationOutboxService {

    public static final String EVENT_EXECUTE_TASK = "EXECUTE_TASK";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_SENT = "SENT";

    private final GenerationOutboxMapper generationOutboxMapper;

    public GenerationOutboxService(GenerationOutboxMapper generationOutboxMapper) {
        this.generationOutboxMapper = generationOutboxMapper;
    }

    public GenerationOutbox enqueueExecution(GenTask task) {
        LocalDateTime now = LocalDateTime.now();
        GenerationOutbox outbox = GenerationOutbox.builder()
                .eventId(UUID.randomUUID().toString())
                .taskUuid(task.getTaskUuid())
                .traceId(task.getTraceId())
                .eventType(EVENT_EXECUTE_TASK)
                .payload(task.getTaskUuid())
                .status(STATUS_PENDING)
                .retryCount(0)
                .nextAttemptTime(now)
                .createTime(now)
                .updateTime(now)
                .build();
        generationOutboxMapper.insert(outbox);
        return outbox;
    }

    public List<GenerationOutbox> findDue(int batchSize) {
        return generationOutboxMapper.selectList(
                new QueryWrapper<GenerationOutbox>()
                        .and(wrapper -> wrapper
                                .and(pending -> pending
                                        .eq("status", STATUS_PENDING)
                                        .le("next_attempt_time", LocalDateTime.now()))
                                .or(expired -> expired
                                        .eq("status", STATUS_PROCESSING)
                                        .lt("locked_until", LocalDateTime.now())))
                        .orderByAsc("id")
                        .last("limit " + Math.max(1, batchSize))
        );
    }

    public boolean claim(String eventId, String workerId, LocalDateTime lockedUntil) {
        return generationOutboxMapper.claim(eventId, workerId, lockedUntil) == 1;
    }

    public boolean markSent(String eventId, String workerId) {
        return generationOutboxMapper.markSent(eventId, workerId) == 1;
    }

    public boolean scheduleRetry(
            String eventId,
            String workerId,
            LocalDateTime nextAttemptTime,
            String lastError
    ) {
        return generationOutboxMapper.scheduleRetry(
                eventId,
                workerId,
                nextAttemptTime,
                truncate(lastError)
        ) == 1;
    }

    private String truncate(String value) {
        String message = value == null ? "Unknown outbox publish error" : value;
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
