package com.airportbus.user.api;

import com.airportbus.user.api.dto.AuthDtos.*;
import com.airportbus.user.service.AuthService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@Tag(name = "auth", description = "注册 / 登录 / 找回密码")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService auth;
    public AuthController(AuthService auth) { this.auth = auth; }

    @PostMapping("/register/code")
    public Sent sendCode(@RequestBody SendCodeReq req) { return auth.sendRegisterCode(req); }
    @PostMapping("/register")
    public TokenPair register(@RequestBody RegisterReq req) { return auth.register(req); }
    @PostMapping("/login")
    public TokenPair login(@RequestBody LoginReq req) { return auth.login(req); }
    @PostMapping("/refresh")
    public TokenPair refresh(@RequestBody RefreshReq req) { return auth.refresh(req); }
    @PostMapping("/logout")
    public Ok logout(@RequestBody RefreshReq req) { return auth.logout(req); }
    @PostMapping("/password/forgot")
    public Sent forgot(@RequestBody ForgotReq req) { return auth.forgot(req); }
    @PostMapping("/password/reset")
    public Ok reset(@RequestBody ResetReq req) { return auth.reset(req); }
}
