package aigc.gameflow.service;

import aigc.gameflow.dto.GenerationSubmitRequest;
import aigc.gameflow.exception.ConflictException;
import aigc.gameflow.image.GenerationEventType;
import aigc.gameflow.image.GenerationStatus;
import aigc.gameflow.mapper.GenTaskMapper;
import aigc.gameflow.mapper.SysUserMapper;
import aigc.gameflow.model.entity.GenTask;
import aigc.gameflow.utils.GenerationRequestHasher;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 任务应用服务：负责查询、提交、重试和取消生成任务。
 * 提交时把扣费、任务、事件和 Outbox 写入放在同一个数据库事务中。
 */
@Service
@Slf4j
public class TaskService {
    private static final int DEFAULT_TASK_PAGE_SIZE = 20;
    private static final int IDEMPOTENCY_KEY_MIN_LENGTH = 8;
    private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 128;
    private static final long IDEMPOTENCY_RESULT_TTL_HOURS = 24;

    private final GenTaskMapper genTaskMapper;
    private final SysUserMapper sysUserMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final GenerationEventService generationEventService;
    private final SubmissionRateLimiter submissionRateLimiter;
    private final QueueBackpressureGuard queueBackpressureGuard;
    private final GenerationOutboxService generationOutboxService;
    private final TaskCacheService taskCacheService;
    private final TransactionTemplate transactionTemplate;

    public TaskService(
            GenTaskMapper genTaskMapper,
            SysUserMapper sysUserMapper,
            RedisTemplate<String, Object> redisTemplate,
            GenerationEventService generationEventService,
            SubmissionRateLimiter submissionRateLimiter,
            QueueBackpressureGuard queueBackpressureGuard,
            GenerationOutboxService generationOutboxService,
            TaskCacheService taskCacheService,
            PlatformTransactionManager transactionManager
    ) {
        this.genTaskMapper = genTaskMapper;
        this.sysUserMapper = sysUserMapper;
        this.redisTemplate = redisTemplate;
        this.generationEventService = generationEventService;
        this.submissionRateLimiter = submissionRateLimiter;
        this.queueBackpressureGuard = queueBackpressureGuard;
        this.generationOutboxService = generationOutboxService;
        this.taskCacheService = taskCacheService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new IllegalArgumentException("User is not authenticated");
        }
        return userId;
    }

    public GenTask getCurrentUserTask(String uuid) {
        Long userId = getCurrentUserId();
        String cacheKey = taskCacheKey(userId, uuid);
        // 查询采用 Cache Aside：先读 Redis，未命中时回源 MySQL 并回填缓存。
        GenTask cachedTask = (GenTask) redisTemplate.opsForValue().get(cacheKey);
        if (cachedTask != null) {
            return cachedTask;
        }

        GenTask task = findTask(uuid, userId);
        if (task == null) {
            throw new IllegalArgumentException("Task does not exist or is not accessible");
        }

        cacheTask(task);
        return task;
    }

    public List<GenTask> listCurrentUserTasks() {
        return genTaskMapper.selectList(
                new QueryWrapper<GenTask>()
                        .eq("user_id", getCurrentUserId())
                        .eq("is_deleted", 0)
                        .orderByDesc("create_time")
                        .last("limit " + DEFAULT_TASK_PAGE_SIZE)
        );
    }

    public List<GenTask> getCurrentUserTasks(List<String> taskUuids) {
        Long userId = getCurrentUserId();
        List<String> distinctUuids = new ArrayList<>(new LinkedHashSet<>(taskUuids));
        List<String> cacheKeys = distinctUuids.stream()
                .map(uuid -> taskCacheKey(userId, uuid))
                .toList();
        // 批量读取可减少逐个访问 Redis 产生的网络往返。
        List<Object> cachedValues = redisTemplate.opsForValue().multiGet(cacheKeys);

        Map<String, GenTask> tasksByUuid = new HashMap<>();
        List<String> missingUuids = new ArrayList<>();
        for (int i = 0; i < distinctUuids.size(); i++) {
            Object cached = cachedValues == null ? null : cachedValues.get(i);
            if (cached instanceof GenTask task) {
                tasksByUuid.put(task.getTaskUuid(), task);
            } else {
                missingUuids.add(distinctUuids.get(i));
            }
        }

        if (!missingUuids.isEmpty()) {
            List<GenTask> databaseTasks = genTaskMapper.selectList(
                    new QueryWrapper<GenTask>()
                            .eq("user_id", userId)
                            .eq("is_deleted", 0)
                            .in("task_uuid", missingUuids)
            );
            for (GenTask task : databaseTasks) {
                tasksByUuid.put(task.getTaskUuid(), task);
                cacheTask(task);
            }
        }

        return distinctUuids.stream()
                .map(tasksByUuid::get)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    public String submitGenerationJob(GenerationSubmitRequest request, String idempotencyKey) {
        Long userId = getCurrentUserId();
        String normalizedKey = validateIdempotencyKey(idempotencyKey);
        String requestHash = GenerationRequestHasher.hash(request);

        String cachedResult = readCachedIdempotencyResult(userId, normalizedKey, requestHash);
        if (cachedResult != null) {
            return cachedResult;
        }

        // 先做用户级限流和系统级背压，避免系统过载时继续创建任务。
        submissionRateLimiter.check(userId);
        queueBackpressureGuard.checkAcceptingNewTasks();
        String lockKey = "idempotency:lock:" + userId + ":" + DigestUtil.sha256Hex(normalizedKey);
        // 短锁只用于合并并发提交；最终幂等性仍由 MySQL 唯一索引保证。
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", 10, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(acquired)) {
            return waitForIdempotentResult(userId, normalizedKey, requestHash);
        }

        try {
            GenTask existing = findByIdempotencyKey(userId, normalizedKey);
            if (existing != null) {
                cacheIdempotencyResult(existing, normalizedKey);
                return verifyAndReturn(existing, requestHash);
            }

            GenTask created;
            try {
                // TransactionTemplate 明确划定扣费和任务落库的原子事务边界。
                created = transactionTemplate.execute(status -> createTaskInTransaction(
                        userId, normalizedKey, requestHash, request
                ));
            } catch (DuplicateKeyException duplicate) {
                GenTask concurrentTask = findByIdempotencyKey(userId, normalizedKey);
                if (concurrentTask != null) {
                    cacheIdempotencyResult(concurrentTask, normalizedKey);
                    return verifyAndReturn(concurrentTask, requestHash);
                }
                throw duplicate;
            }

            if (created == null) {
                throw new IllegalStateException("Generation task transaction returned no result");
            }

            // 缓存属于事务后的加速手段，不参与数据库一致性保证。
            cacheTask(created);
            cacheIdempotencyResult(created, normalizedKey);
            log.info("Generation task submitted, taskUuid={}, traceId={}",
                    created.getTaskUuid(), created.getTraceId());
            return created.getTaskUuid();
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    public void retryGenerationJob(String taskUuid) {
        queueBackpressureGuard.checkAcceptingNewTasks();
        GenTask task = getCurrentUserTask(taskUuid);
        GenTask latest = transactionTemplate.execute(status -> {
            int updated = genTaskMapper.transitionStatus(
                    taskUuid,
                    GenerationStatus.FAILED.code(),
                    GenerationStatus.RETRYING.code(),
                    null,
                    task.getRetryCount() == null ? 0 : task.getRetryCount(),
                    LocalDateTime.now()
            );
            if (updated != 1) {
                throw new ConflictException("Only failed tasks can be retried");
            }

            GenTask retrying = findTask(taskUuid, task.getUserId());
            generationEventService.record(retrying, GenerationEventType.TASK_RETRY_REQUESTED,
                    "Retry requested by user");
            generationOutboxService.enqueueExecution(retrying);
            return retrying;
        });
        if (latest == null) {
            throw new IllegalStateException("Retry transaction returned no task");
        }
        cacheTask(latest);
    }

    public void cancelGenerationJob(String taskUuid) {
        GenTask task = getCurrentUserTask(taskUuid);
        int currentStatus = task.getStatus() == null ? GenerationStatus.PENDING.code() : task.getStatus();
        if (currentStatus == GenerationStatus.SUCCESS.code()
                || currentStatus == GenerationStatus.FAILED.code()
                || currentStatus == GenerationStatus.CANCELED.code()) {
            throw new ConflictException("Completed task cannot be canceled");
        }

        int updated = genTaskMapper.transitionStatus(
                taskUuid,
                currentStatus,
                GenerationStatus.CANCELED.code(),
                "Task canceled by user",
                task.getRetryCount() == null ? 0 : task.getRetryCount(),
                LocalDateTime.now()
        );
        if (updated != 1) {
            throw new ConflictException("Task status changed, cancel request rejected");
        }

        GenTask latest = findTask(taskUuid, task.getUserId());
        cacheTask(latest);
        generationEventService.record(latest, GenerationEventType.TASK_CANCELED,
                "Task canceled by user");
    }

    private GenTask createTaskInTransaction(
            Long userId,
            String idempotencyKey,
            String requestHash,
            GenerationSubmitRequest request
    ) {
        GenTask existing = findByIdempotencyKey(userId, idempotencyKey);
        if (existing != null) {
            verifyAndReturn(existing, requestHash);
            return existing;
        }

        // 条件更新余额，更新失败代表用户不存在或余额不足。
        if (sysUserMapper.debitBalance(userId) != 1) {
            throw new IllegalArgumentException("Insufficient balance");
        }

        LocalDateTime now = LocalDateTime.now();
        GenTask task = GenTask.builder()
                .taskUuid(UUID.randomUUID().toString())
                .idempotencyKey(idempotencyKey)
                .requestHash(requestHash)
                .version(0)
                .retryCount(0)
                .prompt(request.getPrompt().trim())
                .negativePrompt(trimToNull(request.getNegativePrompt()))
                .provider(request.getPreferredProvider() == null ? null : request.getPreferredProvider().name())
                .model(trimToNull(request.getModel()))
                .size(trimToNull(request.getSize()))
                .quality(trimToNull(request.getQuality()))
                .sourceApp(valueOrDefault(request.getSourceApp(), "aigc-gameflow"))
                .externalRunId(trimToNull(request.getExternalRunId()))
                .callbackUrl(trimToNull(request.getCallbackUrl()))
                .traceId(UUID.randomUUID().toString())
                .status(GenerationStatus.PENDING.code())
                .userId(userId)
                .createTime(now)
                .updateTime(now)
                .build();

        // 以下三项与扣费处于同一事务：任何一步失败都会整体回滚。
        genTaskMapper.insert(task);
        generationEventService.record(task, GenerationEventType.TASK_CREATED,
                "Generation task created");
        // 事务内只写 Outbox，不直接发送 MQ，避免数据库成功但消息丢失。
        generationOutboxService.enqueueExecution(task);
        return task;
    }

    private String waitForIdempotentResult(Long userId, String key, String requestHash) {
        // 未获得短锁时短暂轮询已有请求的结果，避免重复创建任务。
        for (int i = 0; i < 40; i++) {
            String cachedResult = readCachedIdempotencyResult(userId, key, requestHash);
            if (cachedResult != null) {
                return cachedResult;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Idempotent request wait interrupted", e);
            }
        }
        GenTask task = findByIdempotencyKey(userId, key);
        if (task != null) {
            cacheIdempotencyResult(task, key);
            return verifyAndReturn(task, requestHash);
        }
        throw new ConflictException("The same idempotent request is still being processed");
    }

    private String readCachedIdempotencyResult(Long userId, String key, String requestHash) {
        Object value = redisTemplate.opsForValue().get(idempotencyResultKey(userId, key));
        if (!(value instanceof String cachedValue)) {
            return null;
        }
        int separator = cachedValue.indexOf('|');
        if (separator <= 0 || separator == cachedValue.length() - 1) {
            redisTemplate.delete(idempotencyResultKey(userId, key));
            return null;
        }
        String cachedHash = cachedValue.substring(0, separator);
        if (!requestHash.equals(cachedHash)) {
            throw new ConflictException("Idempotency-Key was already used with a different request");
        }
        return cachedValue.substring(separator + 1);
    }

    private void cacheIdempotencyResult(GenTask task, String key) {
        redisTemplate.opsForValue().set(
                idempotencyResultKey(task.getUserId(), key),
                task.getRequestHash() + "|" + task.getTaskUuid(),
                IDEMPOTENCY_RESULT_TTL_HOURS,
                TimeUnit.HOURS
        );
    }

    private String idempotencyResultKey(Long userId, String key) {
        return "idempotency:result:" + userId + ":" + DigestUtil.sha256Hex(key);
    }

    private String verifyAndReturn(GenTask task, String requestHash) {
        if (!requestHash.equals(task.getRequestHash())) {
            throw new ConflictException("Idempotency-Key was already used with a different request");
        }
        return task.getTaskUuid();
    }

    private GenTask findByIdempotencyKey(Long userId, String key) {
        return genTaskMapper.selectOne(new QueryWrapper<GenTask>()
                .eq("user_id", userId)
                .eq("idempotency_key", key)
                .eq("is_deleted", 0));
    }

    private GenTask findTask(String uuid, Long userId) {
        return genTaskMapper.selectOne(new QueryWrapper<GenTask>()
                .eq("task_uuid", uuid)
                .eq("user_id", userId)
                .eq("is_deleted", 0));
    }

    private String validateIdempotencyKey(String key) {
        if (key == null) {
            throw new IllegalArgumentException("Idempotency-Key is required");
        }
        String normalized = key.trim();
        if (normalized.length() < IDEMPOTENCY_KEY_MIN_LENGTH
                || normalized.length() > IDEMPOTENCY_KEY_MAX_LENGTH) {
            throw new IllegalArgumentException("Idempotency-Key length must be between 8 and 128");
        }
        return normalized;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String valueOrDefault(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? fallback : trimmed;
    }

    private void cacheTask(GenTask task) {
        taskCacheService.put(task);
    }

    private String taskCacheKey(Long userId, String taskUuid) {
        return taskCacheService.key(userId, taskUuid);
    }
}
