package aigc.gameflow.service;

import aigc.gameflow.exception.RateLimitExceededException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class SubmissionRateLimiter {

    private static final DefaultRedisScript<Long> LIMIT_SCRIPT = new DefaultRedisScript<>("""
            local userCount = redis.call('INCR', KEYS[1])
            if userCount == 1 then redis.call('EXPIRE', KEYS[1], ARGV[3]) end
            if userCount > tonumber(ARGV[1]) then return 0 end
            local globalCount = redis.call('INCR', KEYS[2])
            if globalCount == 1 then redis.call('EXPIRE', KEYS[2], ARGV[3]) end
            if globalCount > tonumber(ARGV[2]) then return 0 end
            return 1
            """, Long.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final int userLimit;
    private final int globalLimit;

    public SubmissionRateLimiter(
            RedisTemplate<String, Object> redisTemplate,
            @Value("${generation.rate-limit.user-per-second:5}") int userLimit,
            @Value("${generation.rate-limit.global-per-second:300}") int globalLimit
    ) {
        this.redisTemplate = redisTemplate;
        this.userLimit = userLimit;
        this.globalLimit = globalLimit;
    }

    public void check(Long userId) {
        long window = Instant.now().getEpochSecond();
        Long allowed = redisTemplate.execute(
                LIMIT_SCRIPT,
                List.of("rate:submit:user:" + userId + ":" + window, "rate:submit:global:" + window),
                userLimit,
                globalLimit,
                2
        );
        if (!Long.valueOf(1L).equals(allowed)) {
            throw new RateLimitExceededException("Submit rate exceeded, retry after 1 second");
        }
    }
}
