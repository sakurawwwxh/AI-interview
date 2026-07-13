package interview.guide.modules.user.security;

import interview.guide.common.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/** Authenticates only JWT access tokens. */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String JWT_ERROR_ATTRIBUTE = "jwtErrorCode";
    private static final String BEARER_PREFIX = "Bearer ";
    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        String token = extractToken(request);
        if (StringUtils.hasText(token)) {
            try {
                Claims claims = jwtUtil.parseAccessToken(token);
                Long userId = jwtUtil.extractUserId(claims);
                String username = jwtUtil.extractUsername(claims);
                String role = jwtUtil.extractRole(claims);
                if (userId != null && StringUtils.hasText(role)) {
                    var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
                    var auth = new UsernamePasswordAuthenticationToken(userId, username, authorities);
                    SecurityContextHolder.getContext().setAuthentication(auth);
                } else {
                    request.setAttribute(JWT_ERROR_ATTRIBUTE, ErrorCode.TOKEN_INVALID);
                }
            } catch (ExpiredJwtException e) {
                request.setAttribute(JWT_ERROR_ATTRIBUTE, ErrorCode.TOKEN_EXPIRED);
                log.debug("JWT expired: {}", e.getMessage());
            } catch (JwtException | IllegalArgumentException e) {
                request.setAttribute(JWT_ERROR_ATTRIBUTE, ErrorCode.TOKEN_INVALID);
                log.debug("JWT rejected: {}", e.getMessage());
            }
        }
        chain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        return StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)
            ? header.substring(BEARER_PREFIX.length()) : null;
    }
}
