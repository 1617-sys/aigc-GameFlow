package aigc.gameflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(max = 50, message = "用户名长度不能超过 50")
        String username,
        @NotBlank(message = "密码不能为空")
        @Size(min = 3, max = 50, message = "密码长度需要在 3 到 50 个字符之间")
        String password
) {
}
