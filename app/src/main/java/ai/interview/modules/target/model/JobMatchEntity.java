package ai.interview.modules.target.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 岗位 JD 与简历的匹配结果快照（每次「开始匹配」落一条）。
 */
@Entity
@Table(name = "job_matches", indexes = {
    @Index(name = "idx_job_match_user_resume_target", columnList = "user_id,resume_id,job_target_id,analyzed_at"),
    @Index(name = "idx_job_match_resume", columnList = "resume_id,analyzed_at")
})
public class JobMatchEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "resume_id", nullable = false)
    private Long resumeId;

    @Column(name = "job_target_id", nullable = false)
    private Long jobTargetId;

    /** 岗位标题快照，岗位删除后仍可展示 */
    @Column(name = "job_title_snapshot", length = 120)
    private String jobTitleSnapshot;

    @Column(nullable = false)
    private Integer score;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "matched_skills_json", columnDefinition = "TEXT")
    private String matchedSkillsJson;

    @Column(name = "missing_skills_json", columnDefinition = "TEXT")
    private String missingSkillsJson;

    @Column(name = "suggestions_json", columnDefinition = "TEXT")
    private String suggestionsJson;

    @Column(name = "optimization_suggestions_json", columnDefinition = "TEXT")
    private String optimizationSuggestionsJson;

    @Column(name = "optimized_resume_text", columnDefinition = "TEXT")
    private String optimizedResumeText;

    @Column(name = "analyzed_at", nullable = false)
    private LocalDateTime analyzedAt;

    @PrePersist
    void onCreate() {
        if (analyzedAt == null) {
            analyzedAt = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getResumeId() { return resumeId; }
    public void setResumeId(Long resumeId) { this.resumeId = resumeId; }
    public Long getJobTargetId() { return jobTargetId; }
    public void setJobTargetId(Long jobTargetId) { this.jobTargetId = jobTargetId; }
    public String getJobTitleSnapshot() { return jobTitleSnapshot; }
    public void setJobTitleSnapshot(String jobTitleSnapshot) { this.jobTitleSnapshot = jobTitleSnapshot; }
    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getMatchedSkillsJson() { return matchedSkillsJson; }
    public void setMatchedSkillsJson(String matchedSkillsJson) { this.matchedSkillsJson = matchedSkillsJson; }
    public String getMissingSkillsJson() { return missingSkillsJson; }
    public void setMissingSkillsJson(String missingSkillsJson) { this.missingSkillsJson = missingSkillsJson; }
    public String getSuggestionsJson() { return suggestionsJson; }
    public void setSuggestionsJson(String suggestionsJson) { this.suggestionsJson = suggestionsJson; }
    public String getOptimizationSuggestionsJson() { return optimizationSuggestionsJson; }
    public void setOptimizationSuggestionsJson(String optimizationSuggestionsJson) {
        this.optimizationSuggestionsJson = optimizationSuggestionsJson;
    }
    public String getOptimizedResumeText() { return optimizedResumeText; }
    public void setOptimizedResumeText(String optimizedResumeText) { this.optimizedResumeText = optimizedResumeText; }
    public LocalDateTime getAnalyzedAt() { return analyzedAt; }
    public void setAnalyzedAt(LocalDateTime analyzedAt) { this.analyzedAt = analyzedAt; }
}
