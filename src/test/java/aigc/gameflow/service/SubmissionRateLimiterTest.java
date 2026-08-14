package aigc.gameflow.service;

import aigc.gameflow.exception.RateLimitExceededException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SubmissionRateLimiterTest {

    @SuppressWarnings("unchecked")
    @Test
    void allowsRequestWhenLuaReturnsOne() {
        RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(1L);
        SubmissionRateLimiter limiter = new SubmissionRateLimiter(redis, 5, 300);

        assertDoesNotThrow(() -> limiter.check(1L));
    }

    @SuppressWarnings("unchecked")
    @Test
    void rejectsRequestWhenLuaReturnsZero() {
        RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(0L);
        SubmissionRateLimiter limiter = new SubmissionRateLimiter(redis, 5, 300);

        assertThrows(RateLimitExceededException.class, () -> limiter.check(1L));
    }
}
