package interview.guide.modules.interview.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 跨面试会话的个人能力统计。
 * 当前项目没有用户实体，因此统计范围为本地全部已评分会话。
 */
public record InterviewStatisticsDTO(
    int completedInterviewCount,
    int averageScore,
    Integer latestScore,
    Integer scoreChange,
    List<CategoryScore> abilityScores,
    List<TrendPoint> scoreTrend,
    List<Weakness> weaknesses
) {
    public record CategoryScore(String category, int averageScore, int questionCount) {
    }

    public record TrendPoint(String sessionId, LocalDateTime completedAt, int score) {
    }

    public record Weakness(String category, int averageScore, int questionCount) {
    }
}
