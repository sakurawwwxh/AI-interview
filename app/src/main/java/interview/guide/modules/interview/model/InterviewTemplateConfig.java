package interview.guide.modules.interview.model;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;

import java.util.EnumSet;
import java.util.List;

/**
 * 创建会话时选择的出题模板。该对象会原样快照到会话，避免模板后续调整影响历史面试。
 */
public record InterviewTemplateConfig(
    String id,
    String name,
    List<QuestionTypeWeight> questionTypes,
    DifficultyDistribution difficultyDistribution,
    Integer followUpCount
) {
    public record QuestionTypeWeight(InterviewQuestionDTO.QuestionType type, int weight) {
    }

    public record DifficultyDistribution(int basic, int advanced, int expert) {
    }

    public static InterviewTemplateConfig defaultBackend(int followUpCount) {
        return new InterviewTemplateConfig(
            "backend-standard",
            "后端综合",
            List.of(
                new QuestionTypeWeight(InterviewQuestionDTO.QuestionType.PROJECT, 20),
                new QuestionTypeWeight(InterviewQuestionDTO.QuestionType.MYSQL, 20),
                new QuestionTypeWeight(InterviewQuestionDTO.QuestionType.REDIS, 20),
                new QuestionTypeWeight(InterviewQuestionDTO.QuestionType.JAVA_BASIC, 10),
                new QuestionTypeWeight(InterviewQuestionDTO.QuestionType.JAVA_COLLECTION, 10),
                new QuestionTypeWeight(InterviewQuestionDTO.QuestionType.JAVA_CONCURRENT, 10),
                new QuestionTypeWeight(InterviewQuestionDTO.QuestionType.SPRING, 10)
            ),
            new DifficultyDistribution(30, 50, 20),
            followUpCount
        );
    }

    public InterviewTemplateConfig normalize(int defaultFollowUpCount) {
        if (questionTypes == null || questionTypes.isEmpty()) {
            throw invalid("面试模板至少需要一个题型");
        }
        EnumSet<InterviewQuestionDTO.QuestionType> seen = EnumSet.noneOf(InterviewQuestionDTO.QuestionType.class);
        for (QuestionTypeWeight item : questionTypes) {
            if (item == null || item.type() == null || item.weight() <= 0 || !seen.add(item.type())) {
                throw invalid("面试模板题型或权重无效");
            }
        }
        DifficultyDistribution difficulty = difficultyDistribution == null
            ? new DifficultyDistribution(30, 50, 20)
            : difficultyDistribution;
        if (difficulty.basic() < 0 || difficulty.advanced() < 0 || difficulty.expert() < 0
            || difficulty.basic() + difficulty.advanced() + difficulty.expert() <= 0) {
            throw invalid("难度分布无效");
        }
        int normalizedFollowUpCount = followUpCount == null ? defaultFollowUpCount : followUpCount;
        if (normalizedFollowUpCount < 0 || normalizedFollowUpCount > 2) {
            throw invalid("追问数量必须在 0 到 2 之间");
        }
        return new InterviewTemplateConfig(
            id == null || id.isBlank() ? "custom" : id.trim(),
            name == null || name.isBlank() ? "自定义模板" : name.trim(),
            List.copyOf(questionTypes), difficulty, normalizedFollowUpCount
        );
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.BAD_REQUEST, message);
    }
}
