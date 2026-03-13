package aigc.gameflow.utils;

import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class JwtUtils {

    // 密钥 (实际开发中应该放在 yml 配置文件里，这里为了演示写死)
    private static final byte[] KEY = "MiaoMiaoMiaoSuperSecretKey2026".getBytes(StandardCharsets.UTF_8);

    /**
     * 生成 Token
     */
    public static String createToken(Long userId, String username) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("uid", userId);
        payload.put("sub", username);
        // 过期时间：1天
        payload.put("expire_time", System.currentTimeMillis() + 1000 * 60 * 60 * 24);

        return JWTUtil.createToken(payload, KEY);
    }

    /**
     * 校验 Token 是否合法
     */
    public static boolean validate(String token) {
        try {
            return JWTUtil.verify(token, KEY);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 从 Token 解析 UserId
     */
    public static Long getUserId(String token) {
        try {
            JWT jwt = JWTUtil.parseToken(token);
            return Long.valueOf(jwt.getPayload("uid").toString());
        } catch (Exception e) {
            return null;
        }
    }
}