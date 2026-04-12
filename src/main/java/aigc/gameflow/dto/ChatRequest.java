package aigc.gameflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(
        @NotBlank(message = "消息不能为空")
        @Size(max = 2000, message = "消息长度不能超过 2000")
        String message
) {
}
