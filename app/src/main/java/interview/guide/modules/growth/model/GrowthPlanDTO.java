package interview.guide.modules.growth.model;

import java.util.List;

/** Deterministic next actions assembled from the user's evaluated interviews and practice tasks. */
public record GrowthPlanDTO(
    String headline,
    String targetRole,
    int evaluatedInterviewCount,
    long pendingPracticeCount,
    List<ActionItem> actions
) {
    public record ActionItem(String title, String description, String category, Integer score,
                             String link, Long practiceTaskId, String priority) {}
}
