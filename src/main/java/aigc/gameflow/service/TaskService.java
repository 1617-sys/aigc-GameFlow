package aigc.gameflow.service;

import aigc.gameflow.config.RabbitConfig;
import aigc.gameflow.dto.GenerationSubmitRequest;
import aigc.gameflow.image.GenerationEventType;
import aigc.gameflow.image.GenerationStatus;
import aigc.gameflow.mapper.GenTaskMapper;
import aigc.gameflow.mapper.SysUserMapper;
import aigc.gameflow.model.entity.GenTask;
import aigc.gameflow.model.entity.SysUser;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class TaskService {
    private static final int DEFAULT_TASK_PAGE_SIZE = 20;

    private final GenTaskMapper genTaskMapper;
    private final RabbitTemplate rabbitTemplate;
    private final SysUserMapper sysUserMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final GenerationEventService generationEventService;

    public TaskService(
            GenTaskMapper genTaskMapper,
            RabbitTemplate rabbitTemplate,
            SysUserMapper sysUserMapper,
            RedisTemplate<String, Object> redisTemplate,
            GenerationEventService generationEventService
    ) {
        this.genTaskMapper = genTaskMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.sysUserMapper = sysUserMapper;
        this.redisTemplate = redisTemplate;
        this.generationEventService = generationEventService;
    }

    public Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new IllegalArgumentException("User is not authenticated");
        }
        return userId;
    }

    public GenTask getCurrentUserTask(String uuid) {
        GenTask task = genTaskMapper.selectOne(
                new QueryWrapper<GenTask>()
                        .eq("task_uuid", uuid)
                        .eq("user_id", getCurrentUserId())
                        .eq("is_deleted", 0)
        );

        if (task == null) {
            throw new IllegalArgumentException("Task does not exist or is not accessible");
        }

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

    public String submitTask(String prompt) {
        GenerationSubmitRequest request = GenerationSubmitRequest.builder()
                .prompt(prompt)
                .sourceApp("legacy-task-api")
                .build();
        return submitGenerationJob(request);
    }

    @Transactional(rollbackFor = Exception.class)
    public String submitGenerationJob(GenerationSubmitRequest request) {
        Long userId = getCurrentUserId();
        applySubmitLimit(userId);
        debitUserBalance(userId);

        String taskUuid = UUID.randomUUID().toString();
        String traceId = UUID.randomUUID().toString();
        GenTask genTask = GenTask.builder()
                .taskUuid(taskUuid)
                .prompt(request.getPrompt().trim())
                .negativePrompt(trimToNull(request.getNegativePrompt()))
                .provider(request.getPreferredProvider() == null ? null : request.getPreferredProvider().name())
                .model(trimToNull(request.getModel()))
                .size(trimToNull(request.getSize()))
                .quality(trimToNull(request.getQuality()))
                .sourceApp(valueOrDefault(request.getSourceApp(), "gamedev-agent-workbench"))
                .externalRunId(trimToNull(request.getExternalRunId()))
                .callbackUrl(trimToNull(request.getCallbackUrl()))
                .traceId(traceId)
                .status(GenerationStatus.PENDING.code())
                .userId(userId)
                .createTime(LocalDateTime.now())
                .build();

        genTaskMapper.insert(genTask);
        generationEventService.record(genTask, GenerationEventType.TASK_CREATED, "Generation task created");
        rabbitTemplate.convertAndSend(RabbitConfig.TASK_QUEUE, taskUuid);
        generationEventService.record(genTask, GenerationEventType.TASK_QUEUED, "Generation task sent to RabbitMQ");
        log.info("Generation task submitted, taskUuid={}, traceId={}", taskUuid, traceId);
        return taskUuid;
    }

    @Transactional(rollbackFor = Exception.class)
    public void retryGenerationJob(String taskUuid) {
        GenTask task = getCurrentUserTask(taskUuid);
        int status = task.getStatus() == null ? GenerationStatus.PENDING.code() : task.getStatus();
        if (status == GenerationStatus.RUNNING.code()) {
            throw new IllegalArgumentException("Running task cannot be retried");
        }

        task.setStatus(GenerationStatus.RETRYING.code());
        task.setErrorMsg(null);
        task.setImageUrl(null);
        task.setProviderJobId(null);
        task.setCallbackStatus(null);
        task.setCallbackError(null);
        task.setUpdateTime(LocalDateTime.now());
        genTaskMapper.updateById(task);
        cacheTask(task);
        generationEventService.record(task, GenerationEventType.TASK_RETRY_REQUESTED, "Retry requested by user");

        rabbitTemplate.convertAndSend(RabbitConfig.TASK_QUEUE, taskUuid);
        generationEventService.record(task, GenerationEventType.TASK_QUEUED, "Retry task sent to RabbitMQ");
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancelGenerationJob(String taskUuid) {
        GenTask task = getCurrentUserTask(taskUuid);
        int status = task.getStatus() == null ? GenerationStatus.PENDING.code() : task.getStatus();
        if (status == GenerationStatus.SUCCESS.code() || status == GenerationStatus.FAILED.code()) {
            throw new IllegalArgumentException("Completed task cannot be canceled");
        }

        task.setStatus(GenerationStatus.CANCELED.code());
        task.setErrorMsg("Task canceled by user");
        task.setUpdateTime(LocalDateTime.now());
        genTaskMapper.updateById(task);
        cacheTask(task);
        generationEventService.record(task, GenerationEventType.TASK_CANCELED, "Task canceled by user");
    }

    private void applySubmitLimit(Long userId) {
        String limitKey = "limit:submit:" + userId;
        Boolean isAllowed = redisTemplate.opsForValue().setIfAbsent(limitKey, "1", 5, TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(isAllowed)) {
            throw new IllegalArgumentException("Submit too frequently, please try again later");
        }
    }

    private void debitUserBalance(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("Current user does not exist");
        }

        Integer balance = user.getBalance();
        if (balance == null || balance <= 0) {
            throw new IllegalArgumentException("Insufficient balance");
        }
        user.setBalance(balance - 1);
        sysUserMapper.updateById(user);
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String valueOrDefault(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? fallback : trimmed;
    }

    private void cacheTask(GenTask task) {
        String cacheKey = "task:info:" + task.getUserId() + ":" + task.getTaskUuid();
        redisTemplate.opsForValue().set(cacheKey, task, 30, TimeUnit.MINUTES);
    }
}
