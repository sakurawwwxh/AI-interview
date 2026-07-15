package interview.guide.modules.user.service;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.infrastructure.redis.RedisService;
import interview.guide.modules.user.model.AuthDTO;
import interview.guide.modules.user.model.AuthDTO.AuthResponse;
import interview.guide.modules.user.model.AuthDTO.RefreshResponse;
import interview.guide.modules.user.model.AuthDTO.UserInfo;
import interview.guide.modules.user.model.UserEntity;
import interview.guide.modules.user.model.UserRole;
import interview.guide.modules.user.repository.UserRepository;
import interview.guide.modules.user.security.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

/**
 * 认证服务
 *
 * <p>处理用户注册、登录、Token 刷新、登出。
 * Refresh Token 存 Redis 可吊销，key: {@code refresh_token:{userId}:{tokenId}}，TTL 7 天。
 */
@Slf4j
@Service
public class AuthService {

    private static final String REFRESH_TOKEN_KEY_PREFIX = "refresh_token:";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RedisService redisService;
    private final Duration refreshTokenTtl;

    public AuthService(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        JwtUtil jwtUtil,
        RedisService redisService,
        @Value("${app.jwt.refresh-expiration:604800}") long refreshExpirationSec
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.redisService = redisService;
        this.refreshTokenTtl = Duration.ofSeconds(refreshExpirationSec);
    }

    /**
     * 注册
     */
    @Transactional
    public AuthResponse register(AuthDTO.RegisterRequest request) {
        // 检查用户名是否已存在
        if (userRepository.existsByUsername(request.username())) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }
        // 检查邮箱是否已存在
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_EXISTS);
        }

        // 创建用户
        UserEntity user = new UserEntity();
        user.setUsername(request.username());
        user.setDisplayName(request.username());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setEmail(request.email());
        user.setRole(UserRole.USER);
        user.setDailyTokenQuota(500000L);
        user = userRepository.save(user);

        log.info("用户注册成功: id={}, username={}", user.getId(), user.getUsername());

        // 生成双 Token
        return generateAuthResponse(user);
    }

    /**
     * 登录
     */
    public AuthResponse login(AuthDTO.LoginRequest request) {
        UserEntity user = userRepository.findByUsername(request.username())
            .orElseThrow(() -> new BusinessException(ErrorCode.PASSWORD_WRONG));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_WRONG);
        }

        log.info("用户登录成功: id={}, username={}", user.getId(), user.getUsername());

        return generateAuthResponse(user);
    }

    /**
     * 刷新 Access Token
     */
    public RefreshResponse refresh(String refreshToken) {
        // 1. 解析 refreshToken
        Claims claims;
        try {
            claims = jwtUtil.parseRefreshToken(refreshToken);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        Long userId = jwtUtil.extractUserId(claims);
        String username = jwtUtil.extractUsername(claims);
        String tokenId = jwtUtil.extractTokenId(claims);

        if (userId == null || tokenId == null) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        // 2. 验证 Redis 中是否存在（未登出/未吊销）
        String redisKey = REFRESH_TOKEN_KEY_PREFIX + userId + ":" + tokenId;
        String stored = redisService.get(redisKey);
        if (stored == null || !stored.equals(refreshToken)) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        // 3. 查库获取最新角色和配额
        UserEntity user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 4. 生成新 accessToken
        String newAccessToken = jwtUtil.generateAccessToken(userId, username, user.getRole().name());

        // 5. 滚动刷新 refreshToken（生成新的 refresh，删除旧的）
        String newRefreshToken = jwtUtil.generateRefreshToken(userId, username);
        Claims newClaims = jwtUtil.parseRefreshToken(newRefreshToken);
        String newTokenId = jwtUtil.extractTokenId(newClaims);
        String newRedisKey = REFRESH_TOKEN_KEY_PREFIX + userId + ":" + newTokenId;
        redisService.set(newRedisKey, newRefreshToken, refreshTokenTtl);
        redisService.delete(redisKey);

        log.info("Token 刷新成功: userId={}", userId);

        return new RefreshResponse(newAccessToken, newRefreshToken);
    }

    /**
     * 登出（删除 Redis 中的 refresh token）
     */
    public void logout(Long userId, String refreshToken) {
        try {
            Claims claims = jwtUtil.parseRefreshToken(refreshToken);
            String tokenId = jwtUtil.extractTokenId(claims);
            if (tokenId != null) {
                String redisKey = REFRESH_TOKEN_KEY_PREFIX + userId + ":" + tokenId;
                redisService.delete(redisKey);
                log.info("用户登出: userId={}", userId);
            }
        } catch (Exception e) {
            // refreshToken 无效也视为登出成功
            log.debug("登出时 refreshToken 解析失败（忽略）: {}", e.getMessage());
        }
    }

    /**
     * 获取当前用户信息
     */
    public UserInfo getUserInfo(Long userId) {
        UserEntity user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return toUserInfo(user);
    }

    @Transactional
    public UserInfo updateProfile(Long userId, AuthDTO.UpdateProfileRequest request) {
        UserEntity user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (userRepository.existsByEmailAndIdNot(request.email(), userId)) {
            throw new BusinessException(ErrorCode.EMAIL_EXISTS);
        }
        user.setDisplayName(request.displayName().trim());
        user.setEmail(request.email().trim());
        return toUserInfo(userRepository.save(user));
    }

    @Transactional
    public void changePassword(Long userId, AuthDTO.ChangePasswordRequest request) {
        UserEntity user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_WRONG);
        }
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        redisService.deleteByPattern(REFRESH_TOKEN_KEY_PREFIX + userId + ":*");
        log.info("Password changed and refresh tokens revoked: userId={}", userId);
    }

    /**
     * 生成双 Token 并存储 refresh 到 Redis
     */
    private AuthResponse generateAuthResponse(UserEntity user) {
        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getUsername(), user.getRole().name());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId(), user.getUsername());

        // 存储 refresh token 到 Redis
        Claims refreshClaims = jwtUtil.parseRefreshToken(refreshToken);
        String tokenId = jwtUtil.extractTokenId(refreshClaims);
        String redisKey = REFRESH_TOKEN_KEY_PREFIX + user.getId() + ":" + tokenId;
        redisService.set(redisKey, refreshToken, refreshTokenTtl);

        return new AuthResponse(accessToken, refreshToken, toUserInfo(user));
    }

    private UserInfo toUserInfo(UserEntity user) {
        return new UserInfo(
            user.getId(),
            user.getUsername(),
            user.getDisplayName() == null || user.getDisplayName().isBlank()
                ? user.getUsername() : user.getDisplayName(),
            user.getEmail(),
            user.getRole(),
            user.getDailyTokenQuota()
        );
    }
}
