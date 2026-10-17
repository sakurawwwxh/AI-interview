package ai.interview.common.ai;

import ai.interview.common.exception.BusinessException;
import ai.interview.common.exception.ErrorCode;
import ai.interview.infrastructure.redis.RedisService;
import ai.interview.modules.user.model.UserEntity;
import ai.interview.modules.user.repository.UserRepository;
import ai.interview.modules.user.security.UserContext;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
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

    @Value("${app.ai.quota.daily-token-limit:500000}")
    private long configuredDailyLimit;

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
            String dailyKey = "ai:usage:daily:" + userId + ":" + today;
            String monthlyKey = "ai:usage:monthly:" + userId + ":" + month;
            String requestKey = "ai:usage:req:daily:" + userId + ":" + today;
            redisService.getAtomicLong(dailyKey).addAndGet(total);
            redisService.getAtomicLong(monthlyKey).addAndGet(total);
            redisService.increment(requestKey);
            // 日期/月度 key 不需要永久保留，避免 Redis 内存随用户数和时间无限增长。
            redisService.expire(dailyKey, Duration.ofDays(2));
            redisService.expire(requestKey, Duration.ofDays(2));
            redisService.expire(monthlyKey, Duration.ofDays(45));
            log.debug("AI 用量记录: userId={}, operation={}, prompt={}, completion={}, total={}",
                userId, operation, promptTokens, completionTokens, total);
        } catch (Exception e) {
            log.warn("AI 用量记录失败（不影响业务）: {}", e.getMessage());
        }
    }

    /**
     * 从 Spring AI 响应中提取并记录模型用量。
     * 不同模型或流式响应可能不返回 usage，此时按 0 处理，不能影响主流程。
     */
    public void recordUsage(ChatResponse response, String operation) {
        if (response == null || response.getMetadata() == null) {
            return;
        }
        Usage usage = response.getMetadata().getUsage();
        if (usage == null) {
            return;
        }
        int promptTokens = usage.getPromptTokens() != null ? usage.getPromptTokens() : 0;
        int completionTokens = usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0;
        recordUsage(promptTokens, completionTokens, operation);
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
            .orElse(configuredDailyLimit > 0 ? configuredDailyLimit : DEFAULT_DAILY_LIMIT);
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
