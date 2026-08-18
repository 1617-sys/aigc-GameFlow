package aigc.gameflow.service;

import aigc.gameflow.mapper.SysUserMapper;
import aigc.gameflow.model.entity.SysUser;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/** 用户注册和密码校验服务。 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;

    public void register(String username, String password) {
        Long count = sysUserMapper.selectCount(
                new QueryWrapper<SysUser>().eq("username", username)
        );
        if (count > 0) {
            throw new IllegalArgumentException("Username already exists");
        }

        LocalDateTime now = LocalDateTime.now();
        SysUser user = new SysUser();
        user.setUsername(username);
        // 数据库只保存 BCrypt 哈希，登录时通过 matches 验证原始密码。
        user.setPassword(passwordEncoder.encode(password));
        user.setBalance(10);
        user.setRole("USER");
        user.setCreateTime(now);
        user.setUpdateTime(now);
        sysUserMapper.insert(user);
    }

    public SysUser login(String username, String password) {
        SysUser user = sysUserMapper.selectOne(
                new QueryWrapper<SysUser>().eq("username", username)
        );
        if (user == null) {
            throw new IllegalArgumentException("User does not exist");
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new IllegalArgumentException("Incorrect password");
        }
        return user;
    }
}
