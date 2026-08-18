package aigc.gameflow.service;

import aigc.gameflow.image.GenerationEventType;
import aigc.gameflow.mapper.GenTaskMapper;
import aigc.gameflow.model.entity.GenTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;

/** 任务结束后向调用方提供的 callbackUrl 推送结果，并记录回调状态。 */
@Slf4j
@Service
public class CallbackService {

    private final RestClient restClient;
    private final GenTaskMapper genTaskMapper;
    private final GenerationEventService generationEventService;

    public CallbackService(
            @Qualifier("callbackRestClient") RestClient restClient,
            GenTaskMapper genTaskMapper,
            GenerationEventService generationEventService
    ) {
        this.restClient = restClient;
        this.genTaskMapper = genTaskMapper;
        this.generationEventService = generationEventService;
    }

    public void notifyIfNeeded(GenTask task) {
        if (task.getCallbackUrl() == null || task.getCallbackUrl().isBlank()) {
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("taskUuid", task.getTaskUuid());
        payload.put("externalRunId", task.getExternalRunId());
        payload.put("status", task.getStatus());
        payload.put("imageUrl", task.getImageUrl());
        payload.put("provider", task.getProvider());
        payload.put("model", task.getModel());
        payload.put("latencyMs", task.getLatencyMs());
        payload.put("errorMsg", task.getErrorMsg());
        payload.put("traceId", task.getTraceId());

        // 回调失败不会回滚已完成的生成任务，只记录失败供后续排查。
        try {
            restClient.post()
                    .uri(task.getCallbackUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            task.setCallbackStatus("SUCCESS");
            task.setCallbackError(null);
            genTaskMapper.updateById(task);
            generationEventService.record(task, GenerationEventType.CALLBACK_SENT,
                    "Callback sent to upstream workflow", payload);
            log.info("Callback sent, taskUuid={}, callbackUrl={}", task.getTaskUuid(), task.getCallbackUrl());
        } catch (Exception e) {
            task.setCallbackStatus("FAILED");
            task.setCallbackError(e.getMessage());
            genTaskMapper.updateById(task);
            generationEventService.record(task, GenerationEventType.CALLBACK_FAILED,
                    "Callback failed: " + e.getMessage(), payload);
            log.warn("Callback failed, taskUuid={}, callbackUrl={}, error={}",
                    task.getTaskUuid(), task.getCallbackUrl(), e.getMessage());
        }
    }
}
