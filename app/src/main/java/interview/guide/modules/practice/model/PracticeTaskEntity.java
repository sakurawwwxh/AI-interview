package interview.guide.modules.practice.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "practice_tasks", indexes = {
    @Index(name = "idx_practice_task_user_status", columnList = "userId,status"),
    @Index(name = "idx_practice_task_user_category", columnList = "userId,category")
})
public class PracticeTaskEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, unique = true)
    private Long sourceAnswerId;

    @Column(length = 36)
    private String sourceSessionId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String question;

    @Column(length = 100)
    private String category;

    @Column(columnDefinition = "TEXT")
    private String originalAnswer;

    private Integer originalScore;

    @Column(columnDefinition = "TEXT")
    private String originalFeedback;

    @Column(columnDefinition = "TEXT")
    private String referenceAnswer;

    @Column(columnDefinition = "TEXT")
    private String keyPointsJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PracticeTaskStatus status = PracticeTaskStatus.TODO;

    private Integer lastScore;
    private Integer attemptCount = 0;
    private LocalDateTime createdAt;
    private LocalDateTime lastPracticedAt;
    private LocalDateTime completedAt;
    private LocalDateTime ignoredAt;
    private LocalDateTime nextReviewAt;
    private Integer reviewIntervalDays;

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PracticeAttemptEntity> attempts = new ArrayList<>();

    @jakarta.persistence.PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getSourceAnswerId() { return sourceAnswerId; }
    public void setSourceAnswerId(Long sourceAnswerId) { this.sourceAnswerId = sourceAnswerId; }
    public String getSourceSessionId() { return sourceSessionId; }
    public void setSourceSessionId(String sourceSessionId) { this.sourceSessionId = sourceSessionId; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getOriginalAnswer() { return originalAnswer; }
    public void setOriginalAnswer(String originalAnswer) { this.originalAnswer = originalAnswer; }
    public Integer getOriginalScore() { return originalScore; }
    public void setOriginalScore(Integer originalScore) { this.originalScore = originalScore; }
    public String getOriginalFeedback() { return originalFeedback; }
    public void setOriginalFeedback(String originalFeedback) { this.originalFeedback = originalFeedback; }
    public String getReferenceAnswer() { return referenceAnswer; }
    public void setReferenceAnswer(String referenceAnswer) { this.referenceAnswer = referenceAnswer; }
    public String getKeyPointsJson() { return keyPointsJson; }
    public void setKeyPointsJson(String keyPointsJson) { this.keyPointsJson = keyPointsJson; }
    public PracticeTaskStatus getStatus() { return status; }
    public void setStatus(PracticeTaskStatus status) { this.status = status; }
    public Integer getLastScore() { return lastScore; }
    public void setLastScore(Integer lastScore) { this.lastScore = lastScore; }
    public Integer getAttemptCount() { return attemptCount; }
    public void setAttemptCount(Integer attemptCount) { this.attemptCount = attemptCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getLastPracticedAt() { return lastPracticedAt; }
    public void setLastPracticedAt(LocalDateTime lastPracticedAt) { this.lastPracticedAt = lastPracticedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public LocalDateTime getIgnoredAt() { return ignoredAt; }
    public void setIgnoredAt(LocalDateTime ignoredAt) { this.ignoredAt = ignoredAt; }
    public LocalDateTime getNextReviewAt() { return nextReviewAt; }
    public void setNextReviewAt(LocalDateTime nextReviewAt) { this.nextReviewAt = nextReviewAt; }
    public Integer getReviewIntervalDays() { return reviewIntervalDays; }
    public void setReviewIntervalDays(Integer reviewIntervalDays) { this.reviewIntervalDays = reviewIntervalDays; }
    public List<PracticeAttemptEntity> getAttempts() { return attempts; }
    public void addAttempt(PracticeAttemptEntity attempt) { attempts.add(attempt); attempt.setTask(this); }
}
