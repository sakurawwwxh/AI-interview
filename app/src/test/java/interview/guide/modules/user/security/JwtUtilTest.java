package interview.guide.modules.user.security;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtUtilTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void shouldOnlyParseAccessTokensAsAccessTokens() {
        JwtUtil jwtUtil = new JwtUtil(SECRET, 60, 60);

        String accessToken = jwtUtil.generateAccessToken(42L, "alice", "USER");

        assertEquals(42L, jwtUtil.extractUserId(jwtUtil.parseAccessToken(accessToken)));
        assertThrows(JwtException.class, () -> jwtUtil.parseRefreshToken(accessToken));
    }

    @Test
    void shouldOnlyParseRefreshTokensAsRefreshTokens() {
        JwtUtil jwtUtil = new JwtUtil(SECRET, 60, 60);

        String refreshToken = jwtUtil.generateRefreshToken(42L, "alice");

        assertEquals(42L, jwtUtil.extractUserId(jwtUtil.parseRefreshToken(refreshToken)));
        assertThrows(JwtException.class, () -> jwtUtil.parseAccessToken(refreshToken));
    }

    @Test
    void shouldRejectTooShortSecret() {
        assertThrows(IllegalArgumentException.class, () -> new JwtUtil("too-short", 60, 60));
    }
}
