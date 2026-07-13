package interview.guide.modules.user.security;

import interview.guide.common.exception.ErrorCode;
import interview.guide.common.result.Result;
import interview.guide.modules.user.model.UserRole;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.ObjectMapper;

/**
 * 当前用户上下文
 *
 * <p>从 SecurityContextHolder 获取当前登录用户信息，
 * 供 Service 层和 RateLimitAspect 调用。
 */
public class UserContext {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 获取当前用户 ID
     *
     * @return userId，未认证时返回 null
     */
    public static Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long userId) {
            return userId;
        }
        return null;
    }

    /**
     * 获取当前用户 ID，未认证时抛出异常
     */
    public static Long getCurrentUserIdOrThrow() {
        Long userId = getCurrentUserId();
        if (userId == null) {
            throw new interview.guide.common.exception.BusinessException(ErrorCode.TOKEN_INVALID, "未登录");
        }
        return userId;
    }

    /**
     * 获取当前用户名
     */
    public static String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getCredentials() instanceof String username) {
            return username;
        }
        return null;
    }

    /**
     * 判断当前用户是否为管理员
     */
    public static boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        return auth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch(a -> a.equals("ROLE_" + UserRole.ADMIN.name()));
    }
}
