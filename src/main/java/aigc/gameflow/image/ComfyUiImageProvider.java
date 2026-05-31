package aigc.gameflow.image;

import aigc.gameflow.service.ComfyUiService;
import aigc.gameflow.service.GameAssetService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ComfyUiImageProvider implements ImageGenerationProvider {

    private final GameAssetService gameAssetService;
    private final ComfyUiService comfyUiService;

    public ComfyUiImageProvider(GameAssetService gameAssetService, ComfyUiService comfyUiService) {
        this.gameAssetService = gameAssetService;
        this.comfyUiService = comfyUiService;
    }

    @Override
    public ProviderType providerType() {
        return ProviderType.COMFYUI;
    }

    @Override
    public boolean supports(ImageGenerationRequest request) {
        return true;
    }

    @Override
    public ImageGenerationResult generate(ImageGenerationRequest request) {
        long start = System.currentTimeMillis();
        String promptId = gameAssetService.generateByText(request.getPrompt());
        log.info("ComfyUI accepted task {}, promptId={}", request.getTaskUuid(), promptId);

        String filename = waitForImage(promptId);
        return ImageGenerationResult.builder()
                .provider(ProviderType.COMFYUI)
                .model(request.getModel() == null ? "comfyui-workflow" : request.getModel())
                .providerJobId(promptId)
                .remoteImageUrl(comfyUiService.buildImageViewUrl(filename))
                .originalFilename(filename)
                .latencyMs(System.currentTimeMillis() - start)
                .build();
    }

    private String waitForImage(String promptId) {
        long start = System.currentTimeMillis();
        while ((System.currentTimeMillis() - start) < 300_000) {
            String filename = comfyUiService.getImageFilename(promptId);
            if (filename != null) {
                return filename;
            }
            try {
                Thread.sleep(2_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("ComfyUI polling interrupted", e);
            }
        }
        throw new IllegalStateException("ComfyUI generation timeout");
    }
}
