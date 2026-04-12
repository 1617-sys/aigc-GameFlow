package aigc.gameflow.controller;

import aigc.gameflow.common.ApiResponse;
import aigc.gameflow.dto.TaskSubmitRequest;
import aigc.gameflow.model.entity.GenTask;
import aigc.gameflow.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/task")
public class TaskController {

    @Autowired
    private TaskService taskService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @PostMapping("/submit")
    public ApiResponse<String> submit(@Valid @RequestBody TaskSubmitRequest request) {
        return ApiResponse.success("任务提交成功", taskService.submitTask(request.prompt()));
    }

    @GetMapping("/{uuid}")
    public ApiResponse<GenTask> getTaskStatus(@PathVariable String uuid) {
        Long currentUserId = taskService.getCurrentUserId();
        String cacheKey = "task:info:" + currentUserId + ":" + uuid;
        GenTask task = (GenTask) redisTemplate.opsForValue().get(cacheKey);

        if (task != null) {
            return ApiResponse.success(task);
        }

        task = taskService.getCurrentUserTask(uuid);
        redisTemplate.opsForValue().set(cacheKey, task, 30, TimeUnit.MINUTES);
        return ApiResponse.success(task);
    }

    @GetMapping("/mine")
    public ApiResponse<List<GenTask>> listMyTasks() {
        return ApiResponse.success(taskService.listCurrentUserTasks());
    }
}
