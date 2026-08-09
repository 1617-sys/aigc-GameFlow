package aigc.gameflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record TaskStatusBatchRequest(
        @NotEmpty(message = "taskUuids must not be empty")
        @Size(max = 20, message = "at most 20 task UUIDs are allowed")
        List<@NotBlank(message = "task UUID must not be blank") String> taskUuids
) {
}
