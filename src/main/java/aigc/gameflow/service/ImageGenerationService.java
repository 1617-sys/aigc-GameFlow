package aigc.gameflow.service;

import aigc.gameflow.image.GenerationEventType;
import aigc.gameflow.image.ImageGenerationRequest;
import aigc.gameflow.image.ImageGenerationResult;
import aigc.gameflow.image.ImageGenerationRouter;
import aigc.gameflow.image.ImageGenerationProvider;
import aigc.gameflow.image.ProviderPolicy;
import aigc.gameflow.image.ProviderType;
import aigc.gameflow.model.entity.GenTask;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.Base64;

/** 串联 Provider 路由、图片生成和 MinIO 持久化。 */
@Service
public class ImageGenerationService {

    private final ImageGenerationRouter router;
    private final MinioService minioService;
    private final GenerationEventService generationEventService;

    public ImageGenerationService(
            ImageGenerationRouter router,
            MinioService minioService,
            GenerationEventService generationEventService
    ) {
        this.router = router;
        this.minioService = minioService;
        this.generationEventService = generationEventService;
    }

    public ImageGenerationResult generateAndStore(GenTask task) {
        // 把数据库实体转换成与具体供应商无关的请求对象。
        ImageGenerationRequest request = ImageGenerationRequest.builder()
                .taskUuid(task.getTaskUuid())
                .prompt(task.getPrompt())
                .negativePrompt(task.getNegativePrompt())
                .preferredProvider(parseProvider(task.getProvider()))
                .providerPolicy(ProviderPolicy.AUTO)
                .model(task.getModel())
                .size(task.getSize())
                .quality(task.getQuality())
                .build();

        ImageGenerationProvider provider = router.route(request);
        generationEventService.record(task, GenerationEventType.PROVIDER_SELECTED,
                "Selected provider " + provider.providerType());
        generationEventService.record(task, GenerationEventType.PROVIDER_REQUEST_SENT,
                "Sending generation request to provider", request);
        ImageGenerationResult result = provider.generate(request);
        String finalUrl = storeResult(result);
        result.setRemoteImageUrl(finalUrl);
        generationEventService.record(task, GenerationEventType.IMAGE_STORED,
                "Generated image stored to object storage", result);
        return result;
    }

    private String storeResult(ImageGenerationResult result) {
        String filename = result.getOriginalFilename() == null ? "generated.png" : result.getOriginalFilename();
        // 同时兼容返回 Base64 的本地 Provider 和返回临时 URL 的云 Provider。
        if (result.getImageBase64() != null && !result.getImageBase64().isBlank()) {
            byte[] bytes = Base64.getDecoder().decode(result.getImageBase64());
            return minioService.uploadImage(new ByteArrayInputStream(bytes), filename);
        }

        if (result.getRemoteImageUrl() != null && !result.getRemoteImageUrl().isBlank()) {
            try (InputStream inputStream = new URL(result.getRemoteImageUrl()).openStream()) {
                return minioService.uploadImage(inputStream, filename);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to store generated image", e);
            }
        }

        throw new IllegalStateException("Image provider returned no image content");
    }

    private ProviderType parseProvider(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ProviderType.valueOf(value.toUpperCase());
        } catch (Exception ignored) {
            return null;
        }
    }
}
