package aigc.gameflow.controller;

import aigc.gameflow.common.ApiResponse;
import aigc.gameflow.dto.GenerationSubmitRequest;
import aigc.gameflow.dto.GenerationSubmitResponse;
import aigc.gameflow.image.GenerationStatus;
import aigc.gameflow.image.ImageGenerationRouter;
import aigc.gameflow.model.entity.GenTask;
import aigc.gameflow.model.entity.GenerationEvent;
import aigc.gameflow.service.GenerationEventService;
import aigc.gameflow.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/generation")
public class GenerationController {

    private final TaskService taskService;
    private final ImageGenerationRouter imageGenerationRouter;
    private final GenerationEventService generationEventService;

    public GenerationController(
            TaskService taskService,
            ImageGenerationRouter imageGenerationRouter,
            GenerationEventService generationEventService
    ) {
        this.taskService = taskService;
        this.imageGenerationRouter = imageGenerationRouter;
        this.generationEventService = generationEventService;
    }

    @PostMapping("/jobs")
    public ApiResponse<GenerationSubmitResponse> submitJob(@Valid @RequestBody GenerationSubmitRequest request) {
        String taskUuid = taskService.submitGenerationJob(request);
        GenTask task = taskService.getCurrentUserTask(taskUuid);
        return ApiResponse.success("generation job submitted", GenerationSubmitResponse.builder()
                .taskUuid(taskUuid)
                .status(GenerationStatus.PENDING.name())
                .provider(task.getProvider())
                .traceId(task.getTraceId())
                .build());
    }

    @GetMapping("/jobs/{taskUuid}")
    public ApiResponse<GenTask> getJob(@PathVariable String taskUuid) {
        return ApiResponse.success(taskService.getCurrentUserTask(taskUuid));
    }

    @GetMapping("/jobs/{taskUuid}/events")
    public ApiResponse<List<GenerationEvent>> listJobEvents(@PathVariable String taskUuid) {
        taskService.getCurrentUserTask(taskUuid);
        return ApiResponse.success(generationEventService.listByTask(taskUuid));
    }

    @GetMapping("/jobs")
    public ApiResponse<List<GenTask>> listJobs() {
        return ApiResponse.success(taskService.listCurrentUserTasks());
    }

    @PostMapping("/jobs/{taskUuid}/retry")
    public ApiResponse<Void> retryJob(@PathVariable String taskUuid) {
        taskService.retryGenerationJob(taskUuid);
        return ApiResponse.success("generation job retry submitted");
    }

    @PostMapping("/jobs/{taskUuid}/cancel")
    public ApiResponse<Void> cancelJob(@PathVariable String taskUuid) {
        taskService.cancelGenerationJob(taskUuid);
        return ApiResponse.success("generation job canceled");
    }

    @GetMapping("/providers")
    public ApiResponse<List<String>> listProviders() {
        return ApiResponse.success(
                imageGenerationRouter.availableProviders().stream()
                        .map(Enum::name)
                        .toList()
        );
    }
}
