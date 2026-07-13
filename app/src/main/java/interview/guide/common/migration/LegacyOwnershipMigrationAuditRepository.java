package interview.guide.common.migration;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LegacyOwnershipMigrationAuditRepository extends JpaRepository<LegacyOwnershipMigrationAuditEntity, Long> {
}
