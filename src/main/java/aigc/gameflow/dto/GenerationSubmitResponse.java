package aigc.gameflow.dto;

import lombok.Builder;

@Builder
public record GenerationSubmitResponse(
        String taskUuid,
        String status,
        String provider,
        String traceId
) {
}
