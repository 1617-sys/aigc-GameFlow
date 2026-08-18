package aigc.gameflow.integration;

import aigc.gameflow.config.RabbitConfig;
import aigc.gameflow.dto.GenerationSubmitRequest;
import aigc.gameflow.image.GenerationStatus;
import aigc.gameflow.image.ProviderType;
import aigc.gameflow.mapper.GenTaskMapper;
import aigc.gameflow.mapper.GenerationEventMapper;
import aigc.gameflow.mapper.GenerationOutboxMapper;
import aigc.gameflow.mapper.SysUserMapper;
import aigc.gameflow.model.entity.GenTask;
import aigc.gameflow.model.entity.GenerationOutbox;
import aigc.gameflow.model.entity.SysUser;
import aigc.gameflow.service.GenerationOutboxService;
import aigc.gameflow.service.OutboxRelayService;
import aigc.gameflow.service.TaskLeaseRecoveryService;
import aigc.gameflow.service.TaskService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 使用真实 MySQL、Redis 和 RabbitMQ 容器验证幂等、Outbox 与租约恢复链路。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.sql.init.mode=always",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "generation.outbox.relay-enabled=false",
        "generation.lease.recovery-enabled=false",
        "generation.backpressure.enabled=false",
        "generation.rate-limit.user-per-second=1000",
        "generation.rate-limit.global-per-second=1000",
        "generation.mock.enabled=true",
        "generation.mock.delay-ms=0",
        "minio.endpoint=http://127.0.0.1:19000",
        "minio.access-key=test",
        "minio.secret-key=test",
        "minio.bucket-name=test"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GenerationReliabilityIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("game_flow")
            .withUsername("test")
            .withPassword("test");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.2")
            .withExposedPorts(6379);

    @Container
    static final RabbitMQContainer RABBITMQ = new RabbitMQContainer("rabbitmq:3.13-management");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", RABBITMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBITMQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBITMQ::getAdminPassword);
    }

    @Autowired
    private TaskService taskService;
    @Autowired
    private OutboxRelayService outboxRelayService;
    @Autowired
    private TaskLeaseRecoveryService taskLeaseRecoveryService;
    @Autowired
    private SysUserMapper sysUserMapper;
    @Autowired
    private GenTaskMapper genTaskMapper;
    @Autowired
    private GenerationOutboxMapper generationOutboxMapper;
    @Autowired
    private GenerationEventMapper generationEventMapper;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private RabbitAdmin rabbitAdmin;

    private Long userId;

    @BeforeEach
    void setUp() {
        generationEventMapper.delete(null);
        generationOutboxMapper.delete(null);
        genTaskMapper.delete(null);
        sysUserMapper.delete(null);
        try (RedisConnection connection = redisTemplate.getConnectionFactory().getConnection()) {
            connection.serverCommands().flushDb();
        }
        rabbitAdmin.purgeQueue(RabbitConfig.TASK_QUEUE, true);
        rabbitAdmin.purgeQueue(RabbitConfig.RETRY_QUEUE, true);
        rabbitAdmin.purgeQueue(RabbitConfig.DLQ, true);

        SysUser user = new SysUser();
        user.setUsername("integration-" + UUID.randomUUID());
        user.setPassword("encoded");
        user.setBalance(10);
        user.setRole("USER");
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        sysUserMapper.insert(user);
        userId = user.getId();
        authenticate(userId);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @Order(1)
    void concurrentDuplicateSubmissionsDebitOnceAndCreateOneOutboxEvent() throws Exception {
        String idempotencyKey = "same-key-" + UUID.randomUUID();
        GenerationSubmitRequest request = request("concurrent poster");
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<String>> calls = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                calls.add(() -> {
                    authenticate(userId);
                    return taskService.submitGenerationJob(request, idempotencyKey);
                });
            }
            List<Future<String>> futures = executor.invokeAll(calls);
            String firstTaskUuid = futures.getFirst().get();
            for (Future<String> future : futures) {
                assertEquals(firstTaskUuid, future.get());
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, genTaskMapper.selectCount(null));
        assertEquals(1, generationOutboxMapper.selectCount(null));
        assertEquals(9, sysUserMapper.selectById(userId).getBalance());
    }

    @Test
    @Order(2)
    void taskAndOutboxCommitBeforeRabbitMqPublishThenRelaySuccessfully() {
        String taskUuid = taskService.submitGenerationJob(
                request("outbox poster"),
                "outbox-key-" + UUID.randomUUID()
        );

        GenTask task = findTask(taskUuid);
        GenerationOutbox outbox = findOutbox(taskUuid);
        assertEquals(GenerationStatus.PENDING.code(), task.getStatus());
        assertEquals(GenerationOutboxService.STATUS_PENDING, outbox.getStatus());
        assertEquals(0, readyMessages(RabbitConfig.TASK_QUEUE));

        assertEquals(1, outboxRelayService.publishBatch());

        GenerationOutbox sent = generationOutboxMapper.selectById(outbox.getId());
        assertEquals(GenerationOutboxService.STATUS_SENT, sent.getStatus());
        assertNotNull(sent.getSentTime());
        assertEquals(1, readyMessages(RabbitConfig.TASK_QUEUE));
    }

    @Test
    @Order(3)
    void expiredRunningLeaseIsRecoveredAndRequeuedThroughOutbox() {
        GenTask running = insertRunningTask(0, LocalDateTime.now().minusSeconds(10));

        assertEquals(1, taskLeaseRecoveryService.recoverExpiredTasks());

        GenTask recovered = findTask(running.getTaskUuid());
        assertEquals(GenerationStatus.RETRYING.code(), recovered.getStatus());
        assertEquals(1, recovered.getRetryCount());
        assertNull(recovered.getWorkerId());
        assertNull(recovered.getLeaseExpireTime());
        assertEquals(1, generationOutboxMapper.selectCount(
                new QueryWrapper<GenerationOutbox>().eq("task_uuid", running.getTaskUuid())
        ));
    }

    @Test
    @Order(4)
    void expiredLeaseStopsAtRetryLimitInsteadOfLoopingForever() {
        GenTask running = insertRunningTask(3, LocalDateTime.now().minusSeconds(10));

        assertEquals(1, taskLeaseRecoveryService.recoverExpiredTasks());

        GenTask failed = findTask(running.getTaskUuid());
        assertEquals(GenerationStatus.FAILED.code(), failed.getStatus());
        assertEquals(0, generationOutboxMapper.selectCount(
                new QueryWrapper<GenerationOutbox>().eq("task_uuid", running.getTaskUuid())
        ));
    }

    @Test
    @Order(5)
    void rabbitMqOutageKeepsOutboxPendingWithoutLosingTask() throws Exception {
        String taskUuid = taskService.submitGenerationJob(
                request("broker outage poster"),
                "broker-outage-key-" + UUID.randomUUID()
        );
        GenerationOutbox outbox = findOutbox(taskUuid);

        RABBITMQ.execInContainer("rabbitmqctl", "stop_app");
        try {
            assertEquals(0, outboxRelayService.publishBatch());

            GenerationOutbox retryable = generationOutboxMapper.selectById(outbox.getId());
            assertEquals(GenerationOutboxService.STATUS_PENDING, retryable.getStatus());
            assertEquals(1, retryable.getRetryCount());
            assertNotNull(retryable.getNextAttemptTime());
            assertNull(retryable.getLockedBy());
            assertNull(retryable.getLockedUntil());
            assertEquals(GenerationStatus.PENDING.code(), findTask(taskUuid).getStatus());
        } finally {
            RABBITMQ.execInContainer("rabbitmqctl", "start_app");
        }
    }

    private GenerationSubmitRequest request(String prompt) {
        return GenerationSubmitRequest.builder()
                .prompt(prompt)
                .preferredProvider(ProviderType.MOCK)
                .size("1024x1024")
                .build();
    }

    private GenTask insertRunningTask(int retries, LocalDateTime leaseExpireTime) {
        LocalDateTime now = LocalDateTime.now();
        GenTask task = GenTask.builder()
                .taskUuid(UUID.randomUUID().toString())
                .idempotencyKey("lease-key-" + UUID.randomUUID())
                .requestHash(UUID.randomUUID().toString().replace("-", ""))
                .version(1)
                .retryCount(retries)
                .workerId("dead-worker-" + UUID.randomUUID())
                .leaseExpireTime(leaseExpireTime)
                .lastHeartbeatTime(leaseExpireTime.minusSeconds(20))
                .prompt("lease recovery poster")
                .status(GenerationStatus.RUNNING.code())
                .userId(userId)
                .sourceApp("integration-test")
                .traceId(UUID.randomUUID().toString())
                .createTime(now.minusMinutes(1))
                .updateTime(now.minusMinutes(1))
                .isDeleted(0)
                .build();
        genTaskMapper.insert(task);
        return task;
    }

    private GenTask findTask(String taskUuid) {
        return genTaskMapper.selectOne(new QueryWrapper<GenTask>().eq("task_uuid", taskUuid));
    }

    private GenerationOutbox findOutbox(String taskUuid) {
        return generationOutboxMapper.selectOne(
                new QueryWrapper<GenerationOutbox>().eq("task_uuid", taskUuid)
        );
    }

    private int readyMessages(String queue) {
        Integer count = rabbitAdmin.getQueueInfo(queue).getMessageCount();
        return count == null ? 0 : count;
    }

    private void authenticate(Long authenticatedUserId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(authenticatedUserId, null, List.of())
        );
    }
}
