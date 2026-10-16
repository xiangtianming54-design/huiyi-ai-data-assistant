package com.huiyi.assistant.api.v1;

import com.huiyi.assistant.auth.AuthService;
import com.huiyi.assistant.common.result.Result;
import com.huiyi.assistant.model.dto.ChangePasswordRequest;
import com.huiyi.assistant.model.dto.LoginRequest;
import com.huiyi.assistant.model.dto.RefreshRequest;
import com.huiyi.assistant.model.dto.RegisterRequest;
import com.huiyi.assistant.model.vo.AuthTokensVO;
import com.huiyi.assistant.model.vo.MeVO;
import com.huiyi.assistant.security.AuthUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/auth/register")
    public Result<AuthTokensVO> register(@Valid @RequestBody RegisterRequest request) {
        return Result.ok(authService.register(request));
    }

    @PostMapping("/auth/login")
    public Result<AuthTokensVO> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }

    @PostMapping("/auth/refresh")
    public Result<AuthTokensVO> refresh(@Valid @RequestBody RefreshRequest request) {
        return Result.ok(authService.refresh(request.getRefreshToken()));
    }

    @PostMapping("/auth/logout")
    public Result<Void> logout(@RequestBody(required = false) RefreshRequest request) {
        if (request != null) {
            authService.logout(request.getRefreshToken());
        }
        return Result.ok();
    }

    @GetMapping("/me")
    public Result<MeVO> me(@AuthenticationPrincipal AuthUser user) {
        return Result.ok(authService.me(user.userId()));
    }

    @PostMapping("/auth/password")
    public Result<Void> changePassword(@AuthenticationPrincipal AuthUser user,
                                       @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(user.userId(), request);
        return Result.ok();
    }
}
