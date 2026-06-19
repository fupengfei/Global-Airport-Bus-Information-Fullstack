package com.airportbus.user.api;

import com.airportbus.user.api.dto.AuthDtos.*;
import com.airportbus.user.security.CurrentUser;
import com.airportbus.user.service.AuthService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@Tag(name = "me", description = "个人中心(需登录)")
@RestController
@RequestMapping("/api/v1/me")
public class MeController {
    private final AuthService auth;
    public MeController(AuthService auth) { this.auth = auth; }

    @GetMapping
    public MeView me() { return auth.me(CurrentUser.require().userId()); }
    @PatchMapping
    public MeView update(@RequestBody UpdateMeReq req) { return auth.updateMe(CurrentUser.require().userId(), req); }
    @PostMapping("/password")
    public Ok changePassword(@RequestBody ChangePasswordReq req) {
        return auth.changePassword(CurrentUser.require().userId(), req);
    }
}
