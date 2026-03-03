package aigc.gameflow.controller;

import aigc.gameflow.model.entity.GenTask;
import aigc.gameflow.service.TaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/task")
public class TaskController {

    @Autowired
    private TaskService taskService;

    @Autowired
    private RedisTemplate redisTemplate;

    @PostMapping("/submit")
    public String submit(@RequestBody Map<String, String> params) {
        // 获取前端传来的 "prompt"
        String prompt = params.get("prompt");

        // 调用 Service 逻辑
        return taskService.submitTask(prompt);
    }

    @GetMapping("/{uuid}")
    public GenTask getTaskStatus(@PathVariable String uuid) {
        // 1. 先查 Redis
        String cacheKey = "task:info:" + uuid;
        GenTask task = (GenTask) redisTemplate.opsForValue().get(cacheKey);

        if (task != null) {
            return task; // 命中缓存，直接返回，不走数据库
        }

        // 2. 缓存没命中（可能过期了），查数据库
        task = taskService.getByUuid(uuid); // 需要你在 Service 里写这个简单的查询

        // 3. 回填 Redis (防止缓存穿透)
        if (task != null) {
            redisTemplate.opsForValue().set(cacheKey, task, 30, TimeUnit.MINUTES);
        }

        return task;
    }
}
