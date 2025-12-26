package interview.guide.dto;

/**
 * 提交答案请求
 */
public record SubmitAnswerRequest(
    String sessionId,
    int questionIndex,
    String answer
) {}
