package aigc.gameflow.image;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** 验证万相请求格式和异步结果解析。 */
class WanxImageProviderTest {

    @Test
    void submitsWan27RequestAndReadsImageFromChoices() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        RestClient restClient = restClientBuilder.build();
        String baseUrl = "https://example.invalid/api/v1";

        server.expect(requestTo(baseUrl + "/services/aigc/image-generation/generation"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(header("X-DashScope-Async", "enable"))
                .andExpect(content().string(containsString("\"model\":\"wan2.7-image-pro\"")))
                .andExpect(content().string(containsString("\"messages\"")))
                .andExpect(content().string(containsString("画面中不要出现以下内容：blurry")))
                .andExpect(content().string(containsString("\"thinking_mode\":true")))
                .andRespond(withSuccess("""
                        {"output":{"task_id":"task-1","task_status":"PENDING"}}
                        """, MediaType.APPLICATION_JSON));

        server.expect(requestTo(baseUrl + "/tasks/task-1"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andRespond(withSuccess("""
                        {
                          "output": {
                            "task_id": "task-1",
                            "task_status": "SUCCEEDED",
                            "choices": [{
                              "message": {
                                "content": [{"type":"image","image":"https://example.invalid/result.png"}]
                              }
                            }]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        WanxImageProvider provider = new WanxImageProvider(
                restClient,
                "test-key",
                baseUrl,
                "wan2.7-image-pro"
        );

        ImageGenerationResult result = provider.generate(ImageGenerationRequest.builder()
                .taskUuid("task-1")
                .prompt("game character concept art")
                .negativePrompt("blurry")
                .size("2048x2048")
                .build());

        assertEquals(ProviderType.WANX, result.getProvider());
        assertEquals("wan2.7-image-pro", result.getModel());
        assertEquals("task-1", result.getProviderJobId());
        assertEquals("https://example.invalid/result.png", result.getRemoteImageUrl());
        server.verify();
    }
}
