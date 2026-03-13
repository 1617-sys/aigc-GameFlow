package aigc.gameflow.controller;

import aigc.gameflow.model.entity.SysUser;
import aigc.gameflow.service.UserService;
import aigc.gameflow.utils.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/user")
public class UserController {
    @Autowired
    private UserService userService;

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody Map<String, String> params){
        String username = params.get("username");
        String password = params.get("password");

        userService.register(username, password);

        // 返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("msg", "注册成功");
        return result;
    }

    @PostMapping("/login")
    public Map<String,Object> login(@RequestBody Map<String,String> params){

        String username = params.get("username");
        String password = params.get("password");

        SysUser user= userService.login(username, password);

        String token = JwtUtils.createToken(user.getId(),user.getUsername());

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("msg", "登录成功");
        result.put("token", token); // <--- 前端拿到这个就能生图了
        result.put("userId", user.getId());
        result.put("balance", user.getBalance()); // 顺便把余额也返回去

        return result;
    }
}
