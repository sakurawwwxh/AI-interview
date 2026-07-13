package interview.guide.modules.target.model;

/** A reviewable resume improvement grounded in the selected job description. */
public record ResumeOptimizationSuggestion(
    String section,
    String issue,
    String recommendation,
    String proposedText
) {}
