package aigc.gameflow.image;

import lombok.Builder;
import lombok.Data;

/** 不同 Provider 返回结果的统一模型。 */
@Data
@Builder
public class ImageGenerationResult {
    private ProviderType provider;
    private String model;
    private String providerJobId;
    private String remoteImageUrl;
    private String imageBase64;
    private String originalFilename;
    private long latencyMs;
    private String rawResponse;
}
