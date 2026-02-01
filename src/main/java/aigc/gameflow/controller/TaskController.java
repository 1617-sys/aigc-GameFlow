package aigc.gameflow.controller;

import aigc.gameflow.service.TaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/task")
public class TaskController {

    @Autowired
    private TaskService taskService;

    @PostMapping("/submit")
    public String submit(@RequestBody Map<String, String> params) {
        // 获取前端传来的 "prompt"
        String prompt = params.get("prompt");

        // 调用 Service 逻辑
        return taskService.submitTask(prompt);
    }
}
