package aigc.gameflow.image;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

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
            return findSupported(preferred, request);
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
        return providers.stream()
                .map(ImageGenerationProvider::providerType)
                .sorted(Comparator.comparing(Enum::name))
                .toList();
    }

    private ImageGenerationProvider findSupported(ProviderType type, ImageGenerationRequest request) {
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
