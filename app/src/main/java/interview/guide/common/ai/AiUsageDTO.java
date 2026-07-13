package interview.guide.common.ai;

/**
 * AI 用量统计 DTO
 */
public record AiUsageDTO(
    long dailyTokens,          // 当日已用 token 数
    long dailyLimit,           // 每日 token 上限
    long monthlyTokens,        // 当月已用 token 数
    long dailyRequestCount,    // 当日请求次数
    boolean quotaEnabled,      // 配额是否启用
    long secondsUntilReset     // 距离午夜重置的秒数
) {}
