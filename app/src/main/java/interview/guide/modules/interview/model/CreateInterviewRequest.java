package interview.guide.modules.interview.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Request for creating a resume-based mock interview. */
public record CreateInterviewRequest(
    @NotBlank(message = "简历文本不能为空") String resumeText,
    @Min(value = 3, message = "题目数量最少3题") @Max(value = 20, message = "题目数量最多20题") int questionCount,
    @NotNull(message = "简历ID不能为空") Long resumeId,
    Boolean forceCreate,
    InterviewTemplateConfig template,
    Long jobTargetId
) {}
