package interview.guide.modules.user.controller;

import interview.guide.common.result.Result;
import interview.guide.modules.user.model.AuthDTO;
import interview.guide.modules.user.model.AuthDTO.AuthResponse;
import interview.guide.modules.user.model.AuthDTO.RefreshResponse;
import interview.guide.modules.user.model.AuthDTO.UserInfo;
import interview.guide.modules.user.security.UserContext;
import interview.guide.modules.user.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public Result<AuthResponse> register(@Valid @RequestBody AuthDTO.RegisterRequest request) {
        return Result.success(authService.register(request));
    }

    @PostMapping("/login")
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
