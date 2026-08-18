package aigc.gameflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 外部 HTTP 客户端配置。
 * 三类调用使用独立 Bean，当前保持相同超时，后续可分别配置认证、拦截器和超时策略。
 */
@Configuration
public class RestClientConfig {

    @Bean("wanxRestClient")
    public RestClient wanxRestClient() {
        return createRestClient();
    }

    @Bean("comfyUiRestClient")
    public RestClient comfyUiRestClient() {
        return createRestClient();
    }

    @Bean("callbackRestClient")
    public RestClient callbackRestClient() {
        return createRestClient();
    }

    private RestClient createRestClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5_000);
        requestFactory.setReadTimeout(180_000);
        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }
}
