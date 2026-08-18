package aigc.gameflow.image;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证显式 Provider 选择和可用列表过滤规则。 */
class ImageGenerationRouterTest {

    @Test
    void doesNotSilentlyFallbackWhenRequestedProviderIsDisabled() {
        ImageGenerationRouter router = new ImageGenerationRouter(
                List.of(new StubProvider(ProviderType.MOCK, false), new StubProvider(ProviderType.COMFYUI, true)),
                "COMFYUI"
        );

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> router.route(
                ImageGenerationRequest.builder().preferredProvider(ProviderType.MOCK).build()
        ));

        assertTrue(error.getMessage().contains("MOCK"));
    }

    @Test
    void exposesOnlyConfiguredProviders() {
        ImageGenerationRouter router = new ImageGenerationRouter(
                List.of(
                        new StubProvider(ProviderType.MOCK, true),
                        new StubProvider(ProviderType.WANX, false),
                        new StubProvider(ProviderType.COMFYUI, false)
                ),
                "MOCK"
        );

        assertEquals(List.of(ProviderType.MOCK), router.availableProviders());
    }

    private record StubProvider(ProviderType providerType, boolean enabled) implements ImageGenerationProvider {
        @Override
        public boolean supports(ImageGenerationRequest request) {
            return enabled;
        }

        @Override
        public ImageGenerationResult generate(ImageGenerationRequest request) {
            throw new UnsupportedOperationException();
        }
    }
}
