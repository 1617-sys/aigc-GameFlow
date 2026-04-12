package aigc.gameflow.controller;

import aigc.gameflow.common.ApiResponse;
import aigc.gameflow.dto.AuthRequest;
import aigc.gameflow.dto.LoginResponse;
import aigc.gameflow.model.entity.SysUser;
import aigc.gameflow.service.UserService;
import aigc.gameflow.utils.JwtUtils;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
public class UserController {
    @Autowired
    private UserService userService;

    @PostMapping("/register")
    public ApiResponse<Void> register(@Valid @RequestBody AuthRequest request) {
        userService.register(request.username(), request.password());
        return ApiResponse.success("注册成功");
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody AuthRequest request) {
        SysUser user = userService.login(request.username(), request.password());
        String token = JwtUtils.createToken(user.getId(), user.getUsername());
        LoginResponse loginResponse = new LoginResponse(token, user.getId(), user.getBalance());
        return ApiResponse.success("登录成功", loginResponse);
    }
}
