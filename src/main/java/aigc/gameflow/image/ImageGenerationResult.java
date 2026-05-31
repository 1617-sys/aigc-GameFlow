package aigc.gameflow.image;

import lombok.Builder;
import lombok.Data;

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
