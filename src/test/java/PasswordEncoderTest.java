package aigc.gameflow;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 用于生成 BCrypt 加密密码的工具类
 * 使用方法：运行 main 方法，传入明文密码
 */
public class PasswordEncoderTest {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        
        // 生成测试用户密码
        String plainPassword = "123456";
        String encodedPassword = encoder.encode(plainPassword);
        
        System.out.println("明文密码：" + plainPassword);
        System.out.println("加密密码：" + encodedPassword);
        System.out.println("\n请将上面的加密密码复制到 SQL 语句中：");
        System.out.println("INSERT INTO sys_user (username, password, balance, role) VALUES ('test', '" + encodedPassword + "', 100, 'USER');");
        
        // 验证加密是否有效
        boolean matches = encoder.matches(plainPassword, encodedPassword);
        System.out.println("\n验证加密是否有效：" + matches);
    }
}
