package ai.interview.modules.target.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 岗位 JD 匹配结果（可持久化回显）。
 */
public record JobMatchDTO(
    Long id,
    Long resumeId,
    Long jobTargetId,
    String jobTitle,
    int score,
    String summary,
    List<String> matchedSkills,
    List<String> missingSkills,
    List<String> suggestions,
    List<ResumeOptimizationSuggestion> optimizationSuggestions,
    String optimizedResumeText,
    LocalDateTime analyzedAt
) {}
