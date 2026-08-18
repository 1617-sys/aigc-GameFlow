package aigc.gameflow.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 根据配置创建对象存储客户端。 */
@Configuration
public class MinioConfig {

    @Value("${minio.client-endpoint:${minio.endpoint}}")
    private String clientEndpoint;

    @Value("${minio.access-key}")
    private String accessKey;

    @Value("${minio.secret-key}")
    private String secretKey;

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(clientEndpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}
