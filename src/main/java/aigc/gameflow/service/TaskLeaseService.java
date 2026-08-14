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
        return genTaskMapper.claimForExecution(
                taskUuid,
                workerId,
                LocalDateTime.now().plusSeconds(leaseSeconds),
                GenerationStatus.RUNNING.code(),
                GenerationStatus.PENDING.code(),
                GenerationStatus.RETRYING.code()
        ) == 1;
    }

    public LeaseHeartbeat startHeartbeat(String taskUuid, String workerId) {
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
            int updated = genTaskMapper.renewLease(
                    taskUuid,
                    workerId,
                    GenerationStatus.RUNNING.code(),
                    LocalDateTime.now().plusSeconds(leaseSeconds)
            );
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
