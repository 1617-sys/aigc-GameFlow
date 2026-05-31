package aigc.gameflow.image;

public interface ImageGenerationProvider {

    ProviderType providerType();

    boolean supports(ImageGenerationRequest request);

    ImageGenerationResult generate(ImageGenerationRequest request);
}
