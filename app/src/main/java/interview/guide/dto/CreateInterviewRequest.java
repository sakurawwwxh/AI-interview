package interview.guide.dto;

/**
 * 创建面试会话请求
 */
public record CreateInterviewRequest(
    String resumeText,      // 简历文本内容
    int questionCount       // 面试题目数量 (建议5-15)
) {
    public CreateInterviewRequest {
        if (questionCount < 3 || questionCount > 20) {
            throw new IllegalArgumentException("题目数量应在3-20之间");
        }
    }
}
