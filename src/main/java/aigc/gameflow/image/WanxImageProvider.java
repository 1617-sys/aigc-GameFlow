package aigc.gameflow.image;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 阿里云万相 Provider：异步提交生成任务，并轮询 DashScope 任务结果。 */
@Slf4j
@Component
public class WanxImageProvider implements ImageGenerationProvider {

    private static final long POLL_INTERVAL_MS = 2_000L;
    private static final long POLL_TIMEOUT_MS = 180_000L;

    private final RestClient restClient;
    private final String apiKey;
    private final String baseUrl;
    private final String defaultModel;

    public WanxImageProvider(
            @Qualifier("wanxRestClient") RestClient restClient,
            @Value("${generation.wanx.api-key:}") String apiKey,
            @Value("${generation.wanx.base-url:https://dashscope.aliyuncs.com/api/v1}") String baseUrl,
            @Value("${generation.wanx.image-model:wan2.7-image-pro}") String defaultModel
    ) {
        this.restClient = restClient;
        this.apiKey = apiKey;
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.defaultModel = defaultModel;
    }

    @Override
    public ProviderType providerType() {
        return ProviderType.WANX;
    }

    @Override
    public boolean supports(ImageGenerationRequest request) {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public ImageGenerationResult generate(ImageGenerationRequest request) {
        long start = System.currentTimeMillis();
        String model = valueOrDefault(request.getModel(), defaultModel);
        String providerJobId = submitTask(request, model);
        String imageUrl = waitForResult(providerJobId);

        return ImageGenerationResult.builder()
                .provider(ProviderType.WANX)
                .model(model)
                .providerJobId(providerJobId)
                .remoteImageUrl(imageUrl)
                .originalFilename("wanx-" + request.getTaskUuid() + ".png")
                .latencyMs(System.currentTimeMillis() - start)
                .build();
    }

    private String submitTask(ImageGenerationRequest request, String model) {
        // 按 DashScope 异步图片生成协议组装请求体。
        Map<String, Object> content = Map.of("text", buildPrompt(request));
        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", List.of(content));
        Map<String, Object> input = Map.of("messages", List.of(message));

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("size", normalizeSize(request.getSize()));
        parameters.put("n", 1);
        parameters.put("watermark", false);
        parameters.put("thinking_mode", true);

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("input", input);
        body.put("parameters", parameters);

        HttpHeaders headers = authorizedHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-DashScope-Async", "enable");

        String responseBody = restClient.post()
                .uri(baseUrl + "/services/aigc/image-generation/generation")
                .headers(requestHeaders -> requestHeaders.addAll(headers))
                .body(body)
                .retrieve()
                .body(String.class);

        JSONObject json = JSON.parseObject(responseBody);
        throwIfApiError(json);
        JSONObject output = json.getJSONObject("output");
        String taskId = output == null ? null : output.getString("task_id");
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalStateException("Wanx returned no task_id");
        }
        return taskId;
    }

    private String waitForResult(String taskId) {
        // 轮询只接受 SUCCEEDED；明确失败状态立即抛出，其余状态继续等待。
        long deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            String responseBody = restClient.get()
                    .uri(baseUrl + "/tasks/" + taskId)
                    .headers(requestHeaders -> requestHeaders.addAll(authorizedHeaders()))
                    .retrieve()
                    .body(String.class);

            JSONObject json = JSON.parseObject(responseBody);
            throwIfApiError(json);
            JSONObject output = json.getJSONObject("output");
            String status = output == null ? null : output.getString("task_status");

            if ("SUCCEEDED".equals(status)) {
                String url = extractImageUrl(output);
                if (url == null || url.isBlank()) {
                    throw new IllegalStateException("Wanx task succeeded without image URL");
                }
                return url;
            }

            if ("FAILED".equals(status) || "CANCELED".equals(status) || "UNKNOWN".equals(status)) {
                String code = output == null ? null : output.getString("code");
                String message = output == null ? null : output.getString("message");
                throw new IllegalStateException("Wanx task " + status + ": " + code + " - " + message);
            }

            sleepBeforeNextPoll();
        }

        throw new IllegalStateException("Wanx generation timeout");
    }

    private HttpHeaders authorizedHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        return headers;
    }

    private void throwIfApiError(JSONObject json) {
        if (json == null) {
            throw new IllegalStateException("Wanx returned an empty response");
        }
        String code = json.getString("code");
        if (code != null && !code.isBlank()) {
            throw new IllegalStateException("Wanx API error: " + code + " - " + json.getString("message"));
        }
    }

    private void sleepBeforeNextPoll() {
        try {
            Thread.sleep(POLL_INTERVAL_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Wanx polling interrupted", e);
        }
    }

    private String buildPrompt(ImageGenerationRequest request) {
        String negativePrompt = request.getNegativePrompt();
        if (negativePrompt == null || negativePrompt.isBlank()) {
            return request.getPrompt();
        }
        return request.getPrompt() + "\n\n画面中不要出现以下内容：" + negativePrompt.trim();
    }

    private String extractImageUrl(JSONObject output) {
        JSONArray choices = output == null ? null : output.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            return null;
        }
        JSONObject message = choices.getJSONObject(0).getJSONObject("message");
        JSONArray content = message == null ? null : message.getJSONArray("content");
        if (content == null) {
            return null;
        }
        for (int i = 0; i < content.size(); i++) {
            String image = content.getJSONObject(i).getString("image");
            if (image != null && !image.isBlank()) {
                return image;
            }
        }
        return null;
    }

    private String normalizeSize(String size) {
        return valueOrDefault(size, "2K").replace('x', '*');
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String stripTrailingSlash(String value) {
        return value != null && value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
