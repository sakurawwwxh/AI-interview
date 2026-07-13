package interview.guide.modules.practice.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class PracticeDTO {

    private PracticeDTO() {
    }

    public record TaskItem(
        Long id,
        PracticeTaskStatus status,
        String question,
        String category,
        int originalScore,
        Integer lastScore,
        Integer improvement,
        int attemptCount,
        LocalDateTime createdAt,
        LocalDateTime lastPracticedAt,
        LocalDateTime nextReviewAt,
        Integer reviewIntervalDays
    ) {
    }

    public record TaskPage(List<TaskItem> items, long total, int page, int size) {
    }

    public record AttemptItem(Long id, String answer, int score, String feedback, LocalDateTime createdAt) {
    }

    public record TaskDetail(
        Long id,
        PracticeTaskStatus status,
        String question,
        String category,
        String originalAnswer,
        int originalScore,
        String originalFeedback,
        String referenceAnswer,
        List<String> keyPoints,
        Integer lastScore,
        Integer improvement,
        int attemptCount,
        LocalDateTime nextReviewAt,
        Integer reviewIntervalDays,
        List<AttemptItem> attempts
    ) {
    }

    public record SubmitAttemptRequest(
        @NotBlank(message = "复练答案不能为空")
        @Size(max = 10000, message = "复练答案不能超过 10000 个字符")
        String answer
    ) {
    }

    public record SubmitAttemptResponse(AttemptItem attempt, PracticeTaskStatus taskStatus, Integer improvement) {
    }

    public record UpdateTaskStatusRequest(@NotNull PracticeTaskStatus status) {
    }

    public record Summary(long todoCount, long completedCount, long ignoredCount, long completedThisWeek,
                          Integer averageImprovement) {
    }
}
