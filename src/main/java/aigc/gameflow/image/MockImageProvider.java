package aigc.gameflow.image;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class MockImageProvider implements ImageGenerationProvider {

    private static final String ONE_PIXEL_PNG =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=";

    private final boolean enabled;
    private final long delayMs;
    private final double failureRate;

    public MockImageProvider(
            @Value("${generation.mock.enabled:false}") boolean enabled,
            @Value("${generation.mock.delay-ms:1000}") long delayMs,
            @Value("${generation.mock.failure-rate:0}") double failureRate
    ) {
        this.enabled = enabled;
        this.delayMs = Math.max(0, delayMs);
        this.failureRate = Math.max(0, Math.min(1, failureRate));
    }

    @Override
    public ProviderType providerType() {
        return ProviderType.MOCK;
    }

    @Override
    public boolean supports(ImageGenerationRequest request) {
        return enabled;
    }

    @Override
    public ImageGenerationResult generate(ImageGenerationRequest request) {
        long start = System.currentTimeMillis();
        sleep();
        if (ThreadLocalRandom.current().nextDouble() < failureRate) {
            throw new IllegalStateException("Mock provider simulated transient failure");
        }
        return ImageGenerationResult.builder()
                .provider(ProviderType.MOCK)
                .model("mock-image-v1")
                .providerJobId(UUID.randomUUID().toString())
                .imageBase64(ONE_PIXEL_PNG)
                .originalFilename("mock-" + request.getTaskUuid() + ".png")
                .latencyMs(System.currentTimeMillis() - start)
                .build();
    }

    private void sleep() {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Mock provider interrupted", e);
        }
    }
}
