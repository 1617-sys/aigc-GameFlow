package aigc.gameflow.service;

import aigc.gameflow.mapper.SysUserMapper;
import aigc.gameflow.model.entity.SysUser;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class UserService {
    
    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /*
    * 注册
    * */
    public void register(String username, String password) {
        //1.用户名是否已经被注册
        QueryWrapper<SysUser> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", username);
        Long count = sysUserMapper.selectCount(queryWrapper);
        
        if (count > 0) {
            throw new IllegalArgumentException("用户名已存在");
        }
        
        //2.加密密码
        String encodedPwd = passwordEncoder.encode(password);
        
        //3.添加新用户数据
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(encodedPwd);
        user.setBalance(10); // 注册送10积分
        user.setRole("USER");
        
        sysUserMapper.insert(user);
    }
    
    public SysUser login(String username, String password) {
        QueryWrapper<SysUser> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", username);
        SysUser user = sysUserMapper.selectOne(queryWrapper);

        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new IllegalArgumentException("密码错误");
        }

        return user;
    }
}
