package interview.guide.modules.interview.model;

import java.time.LocalDateTime;

/**
 * 面试记录列表项（含关联简历摘要，避免前端 N+1 拉详情）
 */
public record InterviewListItemDTO(
    Long id,
    String sessionId,
    Long resumeId,
    String resumeFilename,
    Integer totalQuestions,
    String status,
    String evaluateStatus,
    String evaluateError,
    Integer overallScore,
    String overallFeedback,
    LocalDateTime createdAt,
    LocalDateTime completedAt
) {
}
