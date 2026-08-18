package aigc.gameflow.image;

import lombok.Builder;
import lombok.Data;

/** 业务层传给图片 Provider 的统一请求模型。 */
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
