package interview.guide.common.ai;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.infrastructure.redis.RedisService;
import interview.guide.modules.user.model.UserEntity;
import interview.guide.modules.user.repository.UserRepository;
import interview.guide.modules.user.security.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.Duration;

/**
 * AI 调用 token 用量记录与配额管理（按用户隔离）
 *
 * <p>通过 Redis 原子计数器记录每日/每月 token 消耗，调用前校验配额，超限拒绝请求。
 * Redis key 设计（按用户）：
 * <ul>
 *   <li>{@code ai:usage:daily:{userId}:{yyyy-MM-dd}} - 当日总 token 数</li>
 *   <li>{@code ai:usage:monthly:{userId}:{yyyy-MM}} - 当月总 token 数</li>
 *   <li>{@code ai:usage:req:daily:{userId}:{yyyy-MM-dd}} - 当日请求次数</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiUsageRecorder {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final long DEFAULT_DAILY_LIMIT = 500000L;

    private final RedisService redisService;
    private final UserRepository userRepository;

    @Value("${app.ai.quota.enabled:true}")
    private boolean quotaEnabled;

    /**
     * 校验当日 token 配额，超限时抛出异常阻断调用
     */
    public void checkQuota() {
        if (!quotaEnabled) {
            return;
        }
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return; // 未登录用户不校验（如异步评估等系统调用）
        }
        long todayUsage = getDailyUsage(userId);
        long limit = getDailyLimit(userId);
        if (todayUsage >= limit) {
            log.warn("AI 调用配额超限: userId={}, todayUsage={}, limit={}", userId, todayUsage, limit);
            throw new BusinessException(ErrorCode.AI_RATE_LIMIT_EXCEEDED,
                "今日 AI 调用量已达上限（" + limit + " tokens），请明天再试");
        }
    }

    /**
     * 记录一次 AI 调用的 token 用量
     */
    public void recordUsage(int promptTokens, int completionTokens, String operation) {
        long total = (long) promptTokens + completionTokens;
        if (total <= 0) {
            return;
        }
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return; // 未登录用户不记录
        }
        try {
            String today = LocalDate.now().format(DATE_FMT);
            String month = LocalDate.now().format(MONTH_FMT);
            redisService.getAtomicLong("ai:usage:daily:" + userId + ":" + today).addAndGet(total);
            redisService.getAtomicLong("ai:usage:monthly:" + userId + ":" + month).addAndGet(total);
            redisService.increment("ai:usage:req:daily:" + userId + ":" + today);
            log.debug("AI 用量记录: userId={}, operation={}, prompt={}, completion={}, total={}",
                userId, operation, promptTokens, completionTokens, total);
        } catch (Exception e) {
            log.warn("AI 用量记录失败（不影响业务）: {}", e.getMessage());
        }
    }

    /**
     * 获取用户当日 token 用量
     */
    public long getDailyUsage(Long userId) {
        String today = LocalDate.now().format(DATE_FMT);
        return redisService.getAtomicLong("ai:usage:daily:" + userId + ":" + today).get();
    }

    /**
     * 获取用户当月 token 用量
     */
    public long getMonthlyUsage(Long userId) {
        String month = LocalDate.now().format(MONTH_FMT);
        return redisService.getAtomicLong("ai:usage:monthly:" + userId + ":" + month).get();
    }

    /**
     * 获取用户当日请求次数
     */
    public long getDailyRequestCount(Long userId) {
        String today = LocalDate.now().format(DATE_FMT);
        return redisService.getAtomicLong("ai:usage:req:daily:" + userId + ":" + today).get();
    }

    /**
     * 获取用户的每日配额上限
     */
    public long getDailyLimit(Long userId) {
        return userRepository.findById(userId)
            .map(UserEntity::getDailyTokenQuota)
            .orElse(DEFAULT_DAILY_LIMIT);
    }

    /**
     * 获取当前登录用户的用量统计
     */
    public AiUsageDTO getUsageStats() {
        Long userId = UserContext.getCurrentUserIdOrThrow();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime midnight = LocalDateTime.of(now.toLocalDate().plusDays(1), LocalTime.MIDNIGHT);
        long secondsUntilReset = Duration.between(now, midnight).getSeconds();

        return new AiUsageDTO(
            getDailyUsage(userId),
            getDailyLimit(userId),
            getMonthlyUsage(userId),
            getDailyRequestCount(userId),
            quotaEnabled,
            secondsUntilReset
        );
    }
}
