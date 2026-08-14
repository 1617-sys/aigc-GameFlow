package aigc.gameflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthRequest(
        @NotBlank(message = "username must not be blank")
        @Size(max = 50, message = "username must be at most 50 characters")
        String username,
        @NotBlank(message = "password must not be blank")
        @Size(min = 3, max = 50, message = "password must contain 3 to 50 characters")
        String password
) {
}
