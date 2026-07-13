package interview.guide.modules.user.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 认证相关 DTO
 */
public class AuthDTO {

    /**
     * 注册请求
     */
    public record RegisterRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(min = 3, max = 20, message = "用户名长度必须在 3-20 个字符之间")
        String username,

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 32, message = "密码长度必须在 6-32 个字符之间")
        String password,

        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        String email
    ) {}

    /**
     * 登录请求
     */
    public record LoginRequest(
        @NotBlank(message = "用户名不能为空")
        String username,

        @NotBlank(message = "密码不能为空")
        String password
    ) {}

    /**
     * 刷新 Token 请求
     */
    public record RefreshRequest(
        @NotBlank(message = "refreshToken 不能为空")
        String refreshToken
    ) {}

    /**
     * 认证响应（登录/注册成功返回）
     */
    public record AuthResponse(
        String accessToken,
        String refreshToken,
        UserInfo user
    ) {}

    /**
     * 刷新 Token 响应
     */
    public record RefreshResponse(
        String accessToken,
        String refreshToken
    ) {}

    public record UpdateProfileRequest(
        @NotBlank(message = "Display name must not be blank")
        @Size(max = 50, message = "Display name must be at most 50 characters")
        String displayName,

        @NotBlank(message = "Email must not be blank")
        @Email(message = "Email format is invalid")
        String email
    ) {}

    public record ChangePasswordRequest(
        @NotBlank(message = "Current password must not be blank")
        String currentPassword,

        @NotBlank(message = "New password must not be blank")
        @Size(min = 6, max = 32, message = "New password must contain 6-32 characters")
        String newPassword
    ) {}

    /**
     * 用户信息
     */
    public record UserInfo(
        Long id,
        String username,
        String displayName,
        String email,
        UserRole role,
        Long dailyTokenQuota
    ) {}
}
