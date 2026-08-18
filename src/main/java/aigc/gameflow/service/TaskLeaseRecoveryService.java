package aigc.gameflow.service;

import aigc.gameflow.image.GenerationEventType;
import aigc.gameflow.image.GenerationStatus;
import aigc.gameflow.mapper.GenTaskMapper;
import aigc.gameflow.model.entity.GenTask;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 租约恢复器：扫描心跳超时的 RUNNING 任务，将其重新排队或标记为最终失败。
 */
@Service
@Slf4j
public class TaskLeaseRecoveryService {

    private final GenTaskMapper genTaskMapper;
    private final GenerationOutboxService outboxService;
    private final GenerationEventService generationEventService;
    private final CallbackService callbackService;
    private final TaskCacheService taskCacheService;
    private final TransactionTemplate transactionTemplate;
    private final boolean recoveryEnabled;
    private final int batchSize;
    private final int maxRetries;

    public TaskLeaseRecoveryService(
            GenTaskMapper genTaskMapper,
            GenerationOutboxService outboxService,
            GenerationEventService generationEventService,
            CallbackService callbackService,
            TaskCacheService taskCacheService,
            PlatformTransactionManager transactionManager,
            @Value("${generation.lease.recovery-enabled:true}") boolean recoveryEnabled,
            @Value("${generation.lease.recovery-batch-size:50}") int batchSize,
            @Value("${generation.retry.max-attempts:3}") int maxRetries
    ) {
        this.genTaskMapper = genTaskMapper;
        this.outboxService = outboxService;
        this.generationEventService = generationEventService;
        this.callbackService = callbackService;
        this.taskCacheService = taskCacheService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.recoveryEnabled = recoveryEnabled;
        this.batchSize = batchSize;
        this.maxRetries = maxRetries;
    }

    @Scheduled(
            initialDelayString = "${generation.lease.recovery-initial-delay-ms:30000}",
            fixedDelayString = "${generation.lease.recovery-fixed-delay-ms:10000}"
    )
    public void recoverScheduled() {
        if (recoveryEnabled) {
            recoverExpiredTasks();
        }
    }

    public int recoverExpiredTasks() {
        // 这里只筛选候选任务，真正恢复时还会再次使用 workerId 和过期时间做条件更新。
        List<GenTask> expired = genTaskMapper.selectList(
                new QueryWrapper<GenTask>()
                        .eq("status", GenerationStatus.RUNNING.code())
                        .isNotNull("worker_id")
                        .lt("lease_expire_time", LocalDateTime.now())
                        .orderByAsc("lease_expire_time")
                        .last("limit " + Math.max(1, batchSize))
        );

        int recovered = 0;
        for (GenTask candidate : expired) {
            // 状态迁移、事件记录和重新写 Outbox 必须在同一事务内完成。
            RecoveryResult result = transactionTemplate.execute(status -> recoverOne(candidate));
            if (result == null || result.task() == null) {
                continue;
            }
            recovered++;
            taskCacheService.put(result.task());
            if (result.finalFailure()) {
                callbackService.notifyIfNeeded(result.task());
            }
        }
        return recovered;
    }

    private RecoveryResult recoverOne(GenTask candidate) {
        int currentRetries = candidate.getRetryCount() == null ? 0 : candidate.getRetryCount();
        boolean finalFailure = currentRetries >= maxRetries;
        GenerationStatus target = finalFailure ? GenerationStatus.FAILED : GenerationStatus.RETRYING;
        int nextRetryCount = finalFailure ? currentRetries : currentRetries + 1;
        String error = finalFailure
                ? "Task lease expired and retry limit was reached"
                : "Task lease expired; execution will be retried";

        // 乐观条件更新失败表示任务已被心跳续租或被其他恢复线程处理。
        int updated = genTaskMapper.recoverExpiredLease(
                candidate.getTaskUuid(),
                candidate.getWorkerId(),
                GenerationStatus.RUNNING.code(),
                target.code(),
                error,
                nextRetryCount,
                LocalDateTime.now()
        );
        if (updated != 1) {
            return null;
        }

        GenTask latest = findTask(candidate.getTaskUuid());
        generationEventService.record(latest, GenerationEventType.TASK_LEASE_EXPIRED, error);
        if (finalFailure) {
            generationEventService.record(latest, GenerationEventType.TASK_FAILED, error);
        } else {
            generationEventService.record(latest, GenerationEventType.TASK_RETRY_SCHEDULED,
                    "Expired task execution lease recovered by scheduler");
            outboxService.enqueueExecution(latest);
        }
        log.warn("Expired task lease recovered, taskUuid={}, workerId={}, targetStatus={}",
                candidate.getTaskUuid(), candidate.getWorkerId(), target);
        return new RecoveryResult(latest, finalFailure);
    }

    private GenTask findTask(String taskUuid) {
        return genTaskMapper.selectOne(new QueryWrapper<GenTask>().eq("task_uuid", taskUuid));
    }

    private record RecoveryResult(GenTask task, boolean finalFailure) {
    }
}
