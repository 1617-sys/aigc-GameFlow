package aigc.gameflow.service;

import aigc.gameflow.image.GenerationStatus;
import aigc.gameflow.mapper.GenTaskMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 任务租约服务：用数据库条件更新分配执行权，并通过独立线程定期续租。
 */
@Service
@Slf4j
public class TaskLeaseService {

    private final GenTaskMapper genTaskMapper;
    private final long leaseSeconds;
    private final long heartbeatSeconds;
    private final ScheduledExecutorService heartbeatExecutor;

    public TaskLeaseService(
            GenTaskMapper genTaskMapper,
            @Value("${generation.lease.duration-seconds:60}") long leaseSeconds,
            @Value("${generation.lease.heartbeat-seconds:20}") long heartbeatSeconds,
            @Value("${generation.lease.heartbeat-threads:2}") int heartbeatThreads
    ) {
        if (heartbeatSeconds <= 0 || leaseSeconds <= heartbeatSeconds) {
            throw new IllegalArgumentException("Task lease duration must be greater than heartbeat interval");
        }
        this.genTaskMapper = genTaskMapper;
        this.leaseSeconds = leaseSeconds;
        this.heartbeatSeconds = heartbeatSeconds;
        this.heartbeatExecutor = Executors.newScheduledThreadPool(
                Math.max(1, heartbeatThreads),
                new LeaseThreadFactory()
        );
    }

    public boolean claim(String taskUuid, String workerId) {
        LocalDateTime now = LocalDateTime.now();
        // 只有待执行状态，或已过期的 RUNNING 任务，才能被当前 worker 领取。
        return genTaskMapper.claimForExecution(
                taskUuid,
                workerId,
                now.plusSeconds(leaseSeconds),
                now,
                GenerationStatus.RUNNING.code(),
                GenerationStatus.PENDING.code(),
                GenerationStatus.RETRYING.code()
        ) == 1;
    }

    public LeaseHeartbeat startHeartbeat(String taskUuid, String workerId) {
        // 返回可关闭句柄，让调用方用 try-with-resources 管理心跳生命周期。
        ScheduledFuture<?> future = heartbeatExecutor.scheduleAtFixedRate(
                () -> renew(taskUuid, workerId),
                heartbeatSeconds,
                heartbeatSeconds,
                TimeUnit.SECONDS
        );
        return new LeaseHeartbeat(future);
    }

    private void renew(String taskUuid, String workerId) {
        try {
            LocalDateTime now = LocalDateTime.now();
            int updated = genTaskMapper.renewLease(
                    taskUuid,
                    workerId,
                    GenerationStatus.RUNNING.code(),
                    now.plusSeconds(leaseSeconds),
                    now
            );
            // workerId 和 RUNNING 状态不匹配时续租失败，说明执行权已经转移。
            if (updated != 1) {
                log.info("Task lease heartbeat stopped because ownership changed, taskUuid={}, workerId={}",
                        taskUuid, workerId);
            }
        } catch (Exception e) {
            log.warn("Task lease heartbeat failed, taskUuid={}, workerId={}, error={}",
                    taskUuid, workerId, e.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        heartbeatExecutor.shutdownNow();
    }

    public static final class LeaseHeartbeat implements AutoCloseable {
        private final ScheduledFuture<?> future;

        private LeaseHeartbeat(ScheduledFuture<?> future) {
            this.future = future;
        }

        @Override
        public void close() {
            future.cancel(false);
        }
    }

    private static final class LeaseThreadFactory implements ThreadFactory {
        private final AtomicInteger sequence = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "generation-lease-heartbeat-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
