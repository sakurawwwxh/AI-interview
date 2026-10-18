package ai.interview.modules.user.controller;

import ai.interview.common.annotation.RateLimit;
import ai.interview.common.result.Result;
import ai.interview.modules.user.model.AuthDTO;
import ai.interview.modules.user.model.AuthDTO.AuthResponse;
import ai.interview.modules.user.model.AuthDTO.RefreshResponse;
import ai.interview.modules.user.model.AuthDTO.UserInfo;
import ai.interview.modules.user.security.UserContext;
import ai.interview.modules.user.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @RateLimit(dimensions = {RateLimit.Dimension.IP}, count = 5, interval = 1, timeUnit = RateLimit.TimeUnit.SECONDS)
    // 注册与登录按 IP 限流：无限制下可以批量注册白嫖额度、或无限撞库
    public Result<AuthResponse> register(@Valid @RequestBody AuthDTO.RegisterRequest request) {
        return Result.success(authService.register(request));
    }

    @PostMapping("/login")
    @RateLimit(dimensions = {RateLimit.Dimension.IP}, count = 10, interval = 1, timeUnit = RateLimit.TimeUnit.SECONDS)
    public Result<AuthResponse> login(@Valid @RequestBody AuthDTO.LoginRequest request) {
        return Result.success(authService.login(request));
    }

    @PostMapping("/refresh")
    public Result<RefreshResponse> refresh(@Valid @RequestBody AuthDTO.RefreshRequest request) {
        return Result.success(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public Result<Void> logout(@Valid @RequestBody AuthDTO.RefreshRequest request) {
        authService.logout(UserContext.getCurrentUserIdOrThrow(), request.refreshToken());
        return Result.success(null);
    }

    @GetMapping("/me")
    public Result<UserInfo> me() {
        return Result.success(authService.getUserInfo(UserContext.getCurrentUserIdOrThrow()));
    }

    @PutMapping("/me")
    public Result<UserInfo> updateProfile(@Valid @RequestBody AuthDTO.UpdateProfileRequest request) {
        return Result.success(authService.updateProfile(UserContext.getCurrentUserIdOrThrow(), request));
    }

    @PostMapping("/change-password")
    public Result<Void> changePassword(@Valid @RequestBody AuthDTO.ChangePasswordRequest request) {
        authService.changePassword(UserContext.getCurrentUserIdOrThrow(), request);
        return Result.success(null);
    }
}
