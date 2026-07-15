package interview.guide.modules.interview.model;

import java.util.List;

/**
 * 面试会话DTO
 *
 * @param questionsSource 出题来源：AI / DEFAULT；历史会话可能为 null
 */
public record InterviewSessionDTO(
    String sessionId,
    String resumeText,
    int totalQuestions,
    int currentQuestionIndex,
    List<InterviewQuestionDTO> questions,
    SessionStatus status,
    String questionsSource
) {
    /** 兼容旧调用：无出题来源 */
    public InterviewSessionDTO(
        String sessionId,
        String resumeText,
        int totalQuestions,
        int currentQuestionIndex,
        List<InterviewQuestionDTO> questions,
        SessionStatus status
    ) {
        this(sessionId, resumeText, totalQuestions, currentQuestionIndex, questions, status, null);
    }

    public enum SessionStatus {
        CREATED,      // 会话已创建
        IN_PROGRESS,  // 面试进行中
        COMPLETED,    // 面试已完成
        EVALUATED     // 已生成评估报告
    }

    /** 出题来源常量 */
    public static final String SOURCE_AI = "AI";
    public static final String SOURCE_DEFAULT = "DEFAULT";
}
