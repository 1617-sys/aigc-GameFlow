package aigc.gameflow.dto;

public record LoginResponse(
        String token,
        Long userId,
        Integer balance
) {
}
