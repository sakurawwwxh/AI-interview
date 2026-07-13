package interview.guide.modules.user.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;

/** JWT generation and parsing with strict access/refresh token separation. */
@Component
public class JwtUtil {

    public static final String TOKEN_TYPE_CLAIM = "typ";
    public static final String ACCESS_TOKEN_TYPE = "access";
    public static final String REFRESH_TOKEN_TYPE = "refresh";

    private final SecretKey key;
    private final long accessExpirationMs;
    private final long refreshExpirationMs;

    public JwtUtil(
        @Value("${app.jwt.secret}") String secret,
        @Value("${app.jwt.access-expiration:1800}") long accessExpirationSec,
        @Value("${app.jwt.refresh-expiration:604800}") long refreshExpirationSec
    ) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT_SECRET must contain at least 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessExpirationMs = Duration.ofSeconds(accessExpirationSec).toMillis();
        this.refreshExpirationMs = Duration.ofSeconds(refreshExpirationSec).toMillis();
    }

    public String generateAccessToken(Long userId, String username, String role) {
        Date now = new Date();
        return Jwts.builder()
            .subject(String.valueOf(userId))
            .claim("userId", userId)
            .claim("username", username)
            .claim("role", role)
            .claim(TOKEN_TYPE_CLAIM, ACCESS_TOKEN_TYPE)
            .issuedAt(now)
            .expiration(new Date(now.getTime() + accessExpirationMs))
            .signWith(key)
            .compact();
    }

    public String generateRefreshToken(Long userId, String username) {
        Date now = new Date();
        return Jwts.builder()
            .subject(String.valueOf(userId))
            .claim("userId", userId)
            .claim("username", username)
            .claim(TOKEN_TYPE_CLAIM, REFRESH_TOKEN_TYPE)
            .claim("tokenId", UUID.randomUUID().toString())
            .issuedAt(now)
            .expiration(new Date(now.getTime() + refreshExpirationMs))
            .signWith(key)
            .compact();
    }

    public Claims parseAccessToken(String token) {
        return parseToken(token, ACCESS_TOKEN_TYPE);
    }

    public Claims parseRefreshToken(String token) {
        return parseToken(token, REFRESH_TOKEN_TYPE);
    }

    private Claims parseToken(String token, String expectedType) {
        Claims claims = Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .getPayload();
        if (!expectedType.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
            throw new JwtException("Unexpected JWT token type");
        }
        return claims;
    }

    public Long extractUserId(Claims claims) {
        Object userId = claims.get("userId");
        return userId instanceof Number n ? n.longValue() : null;
    }

    public String extractUsername(Claims claims) {
        return claims.get("username", String.class);
    }

    public String extractRole(Claims claims) {
        return claims.get("role", String.class);
    }

    public String extractTokenId(Claims claims) {
        return claims.get("tokenId", String.class);
    }
}
