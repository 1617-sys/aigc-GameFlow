package aigc.gameflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TaskSubmitRequest(
        @NotBlank(message = "提示词不能为空")
        @Size(max = 1000, message = "提示词长度不能超过 1000")
        String prompt
) {
}
