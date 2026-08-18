package aigc.gameflow.controller;

import aigc.gameflow.common.ApiResponse;
import aigc.gameflow.dto.GenerationSubmitRequest;
import aigc.gameflow.dto.GenerationSubmitResponse;
import aigc.gameflow.dto.TaskStatusBatchRequest;
import aigc.gameflow.image.GenerationStatus;
import aigc.gameflow.image.ImageGenerationRouter;
import aigc.gameflow.model.entity.GenTask;
import aigc.gameflow.model.entity.GenerationEvent;
import aigc.gameflow.service.GenerationEventService;
import aigc.gameflow.service.MinioService;
import aigc.gameflow.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;

/** 图片生成任务 REST 接口，负责参数接收和响应组装，业务规则交给 Service。 */
@RestController
@RequestMapping("/api/generation")
public class GenerationController {

    private final TaskService taskService;
    private final ImageGenerationRouter imageGenerationRouter;
    private final GenerationEventService generationEventService;
    private final MinioService minioService;

    public GenerationController(
            TaskService taskService,
            ImageGenerationRouter imageGenerationRouter,
            GenerationEventService generationEventService,
            MinioService minioService
    ) {
        this.taskService = taskService;
        this.imageGenerationRouter = imageGenerationRouter;
        this.generationEventService = generationEventService;
        this.minioService = minioService;
    }

    @PostMapping("/jobs")
    public ApiResponse<GenerationSubmitResponse> submitJob(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody GenerationSubmitRequest request
    ) {
        String taskUuid = taskService.submitGenerationJob(request, idempotencyKey);
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

    @GetMapping(value = "/jobs/{taskUuid}/image", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<StreamingResponseBody> getJobImage(@PathVariable String taskUuid) {
        GenTask task = taskService.getCurrentUserTask(taskUuid);
        // 使用流式响应转发对象存储内容，避免把整张图片一次性读入 JVM 内存。
        StreamingResponseBody body = outputStream -> minioService.streamImage(task.getImageUrl(), outputStream);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .contentType(MediaType.IMAGE_PNG)
                .body(body);
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

    @PostMapping("/jobs/statuses")
    public ApiResponse<List<GenTask>> getJobStatuses(@Valid @RequestBody TaskStatusBatchRequest request) {
        return ApiResponse.success(taskService.getCurrentUserTasks(request.taskUuids()));
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
