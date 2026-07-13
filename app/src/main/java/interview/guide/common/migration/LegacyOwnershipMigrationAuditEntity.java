package interview.guide.common.migration;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "legacy_ownership_migration_audits")
public class LegacyOwnershipMigrationAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long targetUserId;
    private int migratedResumeCount;
    private int migratedSessionCount;
    private int skippedOrphanSessionCount;
    private String executedBy;
    private LocalDateTime executedAt;

    protected LegacyOwnershipMigrationAuditEntity() {
    }

    public LegacyOwnershipMigrationAuditEntity(Long targetUserId, int migratedResumeCount,
                                               int migratedSessionCount, int skippedOrphanSessionCount) {
        this.targetUserId = targetUserId;
        this.migratedResumeCount = migratedResumeCount;
        this.migratedSessionCount = migratedSessionCount;
        this.skippedOrphanSessionCount = skippedOrphanSessionCount;
        this.executedBy = "legacy-migration-profile";
        this.executedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getTargetUserId() { return targetUserId; }
    public int getMigratedResumeCount() { return migratedResumeCount; }
    public int getMigratedSessionCount() { return migratedSessionCount; }
    public int getSkippedOrphanSessionCount() { return skippedOrphanSessionCount; }
    public String getExecutedBy() { return executedBy; }
    public LocalDateTime getExecutedAt() { return executedAt; }
}
