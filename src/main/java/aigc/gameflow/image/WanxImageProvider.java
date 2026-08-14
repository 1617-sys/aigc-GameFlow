package aigc.gameflow.image;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class WanxImageProvider implements ImageGenerationProvider {

    private static final long POLL_INTERVAL_MS = 2_000L;
    private static final long POLL_TIMEOUT_MS = 180_000L;

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String baseUrl;
    private final String defaultModel;

    public WanxImageProvider(
            RestTemplate restTemplate,
            @Value("${generation.wanx.api-key:}") String apiKey,
            @Value("${generation.wanx.base-url:https://dashscope.aliyuncs.com/api/v1}") String baseUrl,
            @Value("${generation.wanx.image-model:wanx-v1}") String defaultModel
    ) {
        this.restTemplate = restTemplate;
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
        Map<String, Object> input = new HashMap<>();
        input.put("prompt", request.getPrompt());
        if (request.getNegativePrompt() != null && !request.getNegativePrompt().isBlank()) {
            input.put("negative_prompt", request.getNegativePrompt());
        }

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("style", "<auto>");
        parameters.put("size", normalizeSize(request.getSize()));
        parameters.put("n", 1);

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("input", input);
        body.put("parameters", parameters);

        HttpHeaders headers = authorizedHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-DashScope-Async", "enable");

        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl + "/services/aigc/text2image/image-synthesis",
                new HttpEntity<>(body, headers),
                String.class
        );

        JSONObject json = JSON.parseObject(response.getBody());
        throwIfApiError(json);
        JSONObject output = json.getJSONObject("output");
        String taskId = output == null ? null : output.getString("task_id");
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalStateException("Wanx returned no task_id");
        }
        return taskId;
    }

    private String waitForResult(String taskId) {
        long deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS;
        HttpEntity<Void> entity = new HttpEntity<>(authorizedHeaders());

        while (System.currentTimeMillis() < deadline) {
            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl + "/tasks/" + taskId,
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            JSONObject json = JSON.parseObject(response.getBody());
            throwIfApiError(json);
            JSONObject output = json.getJSONObject("output");
            String status = output == null ? null : output.getString("task_status");

            if ("SUCCEEDED".equals(status)) {
                JSONArray results = output.getJSONArray("results");
                String url = results == null || results.isEmpty()
                        ? null
                        : results.getJSONObject(0).getString("url");
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

    private String normalizeSize(String size) {
        return valueOrDefault(size, "1024*1024").replace('x', '*');
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String stripTrailingSlash(String value) {
        return value != null && value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
