package aigc.gameflow.dto;

/** 登录成功后返回的身份凭证和用户概况。 */
public record LoginResponse(
        String token,
        Long userId,
        Integer balance
) {
}
