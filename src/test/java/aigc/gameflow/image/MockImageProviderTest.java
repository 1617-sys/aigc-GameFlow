package aigc.gameflow.image;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MockImageProviderTest {

    @Test
    void returnsDeterministicImagePayloadWhenEnabled() {
        MockImageProvider provider = new MockImageProvider(true, 0, 0);

        ImageGenerationResult result = provider.generate(
                ImageGenerationRequest.builder().taskUuid("task-1").prompt("poster").build()
        );

        assertEquals(ProviderType.MOCK, result.getProvider());
        assertNotNull(result.getImageBase64());
        assertNotNull(result.getProviderJobId());
    }

    @Test
    void canSimulateTransientFailure() {
        MockImageProvider provider = new MockImageProvider(true, 0, 1);

        assertThrows(IllegalStateException.class, () -> provider.generate(
                ImageGenerationRequest.builder().taskUuid("task-2").prompt("poster").build()
        ));
    }
}
