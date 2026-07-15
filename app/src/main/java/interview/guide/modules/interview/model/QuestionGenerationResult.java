package interview.guide.modules.interview.model;

import java.util.List;

/**
 * 面试出题结果：题目列表 + 来源标记
 */
public record QuestionGenerationResult(
    List<InterviewQuestionDTO> questions,
    String source
) {
    public static QuestionGenerationResult ai(List<InterviewQuestionDTO> questions) {
        return new QuestionGenerationResult(questions, InterviewSessionDTO.SOURCE_AI);
    }

    public static QuestionGenerationResult defaults(List<InterviewQuestionDTO> questions) {
        return new QuestionGenerationResult(questions, InterviewSessionDTO.SOURCE_DEFAULT);
    }
}
