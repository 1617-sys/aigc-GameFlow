package aigc.gameflow.image;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/** 根据显式选择或路由策略，从可用 Provider 中选择实际执行者。 */
@Component
public class ImageGenerationRouter {

    private final List<ImageGenerationProvider> providers;
    private final ProviderType defaultProvider;

    public ImageGenerationRouter(
            List<ImageGenerationProvider> providers,
            @Value("${generation.default-provider:COMFYUI}") String defaultProvider
    ) {
        this.providers = providers;
        this.defaultProvider = parseProvider(defaultProvider, ProviderType.COMFYUI);
    }

    public ImageGenerationProvider route(ImageGenerationRequest request) {
        ProviderType preferred = request.getPreferredProvider();
        if (preferred != null) {
            // 用户显式指定时不静默降级，配置不可用应明确报错。
            return findRequestedProvider(preferred, request);
        }

        ProviderPolicy policy = request.getProviderPolicy() == null ? ProviderPolicy.AUTO : request.getProviderPolicy();
        if (policy == ProviderPolicy.LOCAL_FIRST || policy == ProviderPolicy.COST_FIRST) {
            return findSupported(ProviderType.COMFYUI, request);
        }
        if (policy == ProviderPolicy.QUALITY_FIRST) {
            return findSupported(ProviderType.WANX, request);
        }

        return findSupported(defaultProvider, request);
    }

    public List<ProviderType> availableProviders() {
        ImageGenerationRequest probe = ImageGenerationRequest.builder().build();
        return providers.stream()
                .filter(provider -> provider.supports(probe))
                .map(ImageGenerationProvider::providerType)
                .sorted(Comparator.comparing(Enum::name))
                .toList();
    }

    private ImageGenerationProvider findRequestedProvider(ProviderType type, ImageGenerationRequest request) {
        return providers.stream()
                .filter(provider -> provider.providerType() == type)
                .filter(provider -> provider.supports(request))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Requested image provider " + type + " is not available; check its configuration"
                ));
    }

    private ImageGenerationProvider findSupported(ProviderType type, ImageGenerationRequest request) {
        // 策略选择允许降级：首选不可用时使用任意支持该请求的 Provider。
        return providers.stream()
                .filter(provider -> provider.providerType() == type)
                .filter(provider -> provider.supports(request))
                .findFirst()
                .orElseGet(() -> providers.stream()
                        .filter(provider -> provider.supports(request))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("No available image generation provider")));
    }

    private ProviderType parseProvider(String value, ProviderType fallback) {
        try {
            return ProviderType.valueOf(value.trim().toUpperCase());
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
