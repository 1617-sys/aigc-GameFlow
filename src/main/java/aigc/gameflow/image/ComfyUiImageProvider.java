package aigc.gameflow.image;

import aigc.gameflow.service.ComfyUiService;
import aigc.gameflow.service.GameAssetService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 本地 ComfyUI Provider：提交工作流任务并轮询生成结果。 */
@Slf4j
@Component
public class ComfyUiImageProvider implements ImageGenerationProvider {

    private final GameAssetService gameAssetService;
    private final ComfyUiService comfyUiService;
    private final boolean enabled;

    public ComfyUiImageProvider(
            GameAssetService gameAssetService,
            ComfyUiService comfyUiService,
            @Value("${comfyui.enabled:false}") boolean enabled
    ) {
        this.gameAssetService = gameAssetService;
        this.comfyUiService = comfyUiService;
        this.enabled = enabled;
    }

    @Override
    public ProviderType providerType() {
        return ProviderType.COMFYUI;
    }

    @Override
    public boolean supports(ImageGenerationRequest request) {
        return enabled;
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
        // ComfyUI 是异步接口，这里以固定间隔查询历史记录，最长等待 5 分钟。
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
