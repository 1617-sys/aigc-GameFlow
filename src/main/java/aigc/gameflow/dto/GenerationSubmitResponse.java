package aigc.gameflow.dto;

import lombok.Builder;

/** 任务提交成功后的精简响应，不返回尚未生成的图片信息。 */
@Builder
public record GenerationSubmitResponse(
        String taskUuid,
        String status,
        String provider,
        String traceId
) {
}
