package interview.guide.modules.target.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "job_targets", indexes = @Index(name = "idx_job_target_user_updated", columnList = "user_id,updated_at"))
public class JobTargetEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(nullable = false, length = 120) private String title;
    @Column(length = 120) private String company;
    @Column(name = "job_description", columnDefinition = "TEXT", nullable = false) private String jobDescription;
    @Column(nullable = false) private boolean active;
    @Column(nullable = false) private LocalDateTime createdAt;
    @Column(nullable = false) private LocalDateTime updatedAt;
    @PrePersist void create() { createdAt = LocalDateTime.now(); updatedAt = createdAt; }
    @PreUpdate void update() { updatedAt = LocalDateTime.now(); }
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; } public void setUserId(Long userId) { this.userId = userId; }
    public String getTitle() { return title; } public void setTitle(String title) { this.title = title; }
    public String getCompany() { return company; } public void setCompany(String company) { this.company = company; }
    public String getJobDescription() { return jobDescription; } public void setJobDescription(String jobDescription) { this.jobDescription = jobDescription; }
    public boolean isActive() { return active; } public void setActive(boolean active) { this.active = active; }
    public LocalDateTime getCreatedAt() { return createdAt; } public LocalDateTime getUpdatedAt() { return updatedAt; }
}
