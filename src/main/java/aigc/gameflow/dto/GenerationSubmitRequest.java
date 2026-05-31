package aigc.gameflow.dto;

import aigc.gameflow.image.ProviderPolicy;
import aigc.gameflow.image.ProviderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerationSubmitRequest {

    @NotBlank(message = "prompt must not be blank")
    @Size(max = 2000, message = "prompt must be less than 2000 characters")
    private String prompt;

    @Size(max = 1000, message = "negative prompt must be less than 1000 characters")
    private String negativePrompt;

    private ProviderType preferredProvider;

    private ProviderPolicy providerPolicy;

    private String model;

    private String size;

    private String quality;

    private String sourceApp;

    private String externalRunId;

    private String callbackUrl;
}
