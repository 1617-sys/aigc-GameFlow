package aigc.gameflow.utils;

import aigc.gameflow.dto.GenerationSubmitRequest;
import aigc.gameflow.image.ProviderType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class GenerationRequestHasherTest {

    @Test
    void sameRequestHasStableHash() {
        GenerationSubmitRequest request = GenerationSubmitRequest.builder()
                .prompt("game poster")
                .preferredProvider(ProviderType.MOCK)
                .size("1024x1024")
                .build();

        assertEquals(GenerationRequestHasher.hash(request), GenerationRequestHasher.hash(request));
    }

    @Test
    void changedRequestHasDifferentHash() {
        GenerationSubmitRequest first = GenerationSubmitRequest.builder().prompt("poster A").build();
        GenerationSubmitRequest second = GenerationSubmitRequest.builder().prompt("poster B").build();

        assertNotEquals(GenerationRequestHasher.hash(first), GenerationRequestHasher.hash(second));
    }
}
