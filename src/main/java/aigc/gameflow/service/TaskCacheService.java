package aigc.gameflow.service;

import aigc.gameflow.model.entity.GenTask;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Service
public class TaskCacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    public TaskCacheService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void put(GenTask task) {
        if (task == null) {
            return;
        }
        long ttlMinutes = ThreadLocalRandom.current().nextLong(10, 16);
        redisTemplate.opsForValue().set(key(task.getUserId(), task.getTaskUuid()), task, ttlMinutes, TimeUnit.MINUTES);
    }

    public String key(Long userId, String taskUuid) {
        return "task:info:" + userId + ":" + taskUuid;
    }
}
