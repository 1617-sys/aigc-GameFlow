package aigc.gameflow.image;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ImageGenerationRequest {
    private String taskUuid;
    private String prompt;
    private String negativePrompt;
    private ProviderType preferredProvider;
    private ProviderPolicy providerPolicy;
    private String model;
    private String size;
    private String quality;
}
