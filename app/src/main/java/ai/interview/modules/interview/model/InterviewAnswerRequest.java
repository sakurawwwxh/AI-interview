package ai.interview.modules.interview.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request body used when an interview answer is submitted or saved. */
public record InterviewAnswerRequest(
    @NotNull(message = "问题索引不能为空")
    @Min(value = 0, message = "问题索引无效")
    Integer questionIndex,

    @NotBlank(message = "答案不能为空")
    @Size(max = 10000, message = "答案过长，请控制在 10000 字以内")
    String answer,

    @Min(value = 0, message = "答题时长不能为负数")
    Integer answerDurationSeconds
) {}
