package aigc.gameflow.image;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class OpenAiImageProvider implements ImageGenerationProvider {

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String baseUrl;
    private final String defaultModel;

    public OpenAiImageProvider(
            RestTemplate restTemplate,
            @Value("${generation.openai.api-key:}") String apiKey,
            @Value("${generation.openai.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${generation.openai.image-model:gpt-image-1}") String defaultModel
    ) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.defaultModel = defaultModel;
    }

    @Override
    public ProviderType providerType() {
        return ProviderType.OPENAI;
    }

    @Override
    public boolean supports(ImageGenerationRequest request) {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public ImageGenerationResult generate(ImageGenerationRequest request) {
        long start = System.currentTimeMillis();
        String model = request.getModel() == null || request.getModel().isBlank() ? defaultModel : request.getModel();

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("prompt", request.getPrompt());
        body.put("size", valueOrDefault(request.getSize(), "1024x1024"));
        body.put("quality", valueOrDefault(request.getQuality(), "auto"));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl + "/images/generations",
                new HttpEntity<>(body, headers),
                String.class
        );

        JSONObject json = JSON.parseObject(response.getBody());
        JSONArray data = json.getJSONArray("data");
        if (data == null || data.isEmpty()) {
            throw new IllegalStateException("OpenAI image API returned empty data");
        }

        JSONObject first = data.getJSONObject(0);
        String b64 = first.getString("b64_json");
        String url = first.getString("url");

        return ImageGenerationResult.builder()
                .provider(ProviderType.OPENAI)
                .model(model)
                .imageBase64(b64)
                .remoteImageUrl(url)
                .originalFilename("openai-" + request.getTaskUuid() + ".png")
                .latencyMs(System.currentTimeMillis() - start)
                .rawResponse(response.getBody())
                .build();
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
