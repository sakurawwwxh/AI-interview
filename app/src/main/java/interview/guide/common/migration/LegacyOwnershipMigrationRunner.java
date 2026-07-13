package interview.guide.common.migration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("legacy-migration")
@RequiredArgsConstructor
@EnableConfigurationProperties(LegacyMigrationProperties.class)
public class LegacyOwnershipMigrationRunner implements ApplicationRunner {

    private final LegacyMigrationProperties properties;
    private final LegacyOwnershipMigrationService migrationService;

    @Override
    public void run(ApplicationArguments args) {
        if (properties.getTargetUserId() == null) {
            throw new IllegalArgumentException("app.legacy-migration.target-user-id is required");
        }
        LegacyOwnershipMigrationService.MigrationResult result = properties.isApply()
            ? migrationService.apply(properties.getTargetUserId())
            : migrationService.preview(properties.getTargetUserId());
        log.info("Legacy ownership migration {}: targetUserId={}, resumes={}, sessions={}, skippedOrphans={}",
            result.applied() ? "APPLIED" : "DRY_RUN", result.targetUserId(), result.migratedResumeCount(),
            result.migratedSessionCount(), result.skippedOrphanSessionCount());
    }
}
