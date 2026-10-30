package ai.interview.modules.interview.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request for creating a resume-based mock interview. */
public record CreateInterviewRequest(
    @NotBlank(message = "简历文本不能为空")
    @Size(max = 50000, message = "简历文本过长，请控制在 50000 字以内")
    String resumeText,
    @Min(value = 3, message = "题目数量最少3题") @Max(value = 20, message = "题目数量最多20题") int questionCount,
    @NotNull(message = "简历ID不能为空") Long resumeId,
    Boolean forceCreate,
    InterviewTemplateConfig template,
    Long jobTargetId
) {}
