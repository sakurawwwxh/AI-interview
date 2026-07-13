package interview.guide.common.migration;

import interview.guide.modules.interview.model.InterviewSessionEntity;
import interview.guide.modules.interview.repository.InterviewSessionRepository;
import interview.guide.modules.resume.model.ResumeEntity;
import interview.guide.modules.resume.repository.ResumeRepository;
import interview.guide.modules.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LegacyOwnershipMigrationServiceTest {

    @Test
    void previewDoesNotWriteAndReportsOnlyMigratableSessions() {
        UserRepository userRepository = mock(UserRepository.class);
        ResumeRepository resumeRepository = mock(ResumeRepository.class);
        InterviewSessionRepository sessionRepository = mock(InterviewSessionRepository.class);
        LegacyOwnershipMigrationAuditRepository auditRepository = mock(LegacyOwnershipMigrationAuditRepository.class);
        ResumeEntity legacyResume = new ResumeEntity();
        legacyResume.setId(11L);
        InterviewSessionEntity migratable = session(legacyResume);
        ResumeEntity alreadyOwnedResume = new ResumeEntity();
        alreadyOwnedResume.setId(12L);
        alreadyOwnedResume.setUserId(7L);
        InterviewSessionEntity alreadyOwnedResumeSession = session(alreadyOwnedResume);
        InterviewSessionEntity orphan = session(null);

        when(userRepository.existsById(7L)).thenReturn(true);
        when(resumeRepository.findAllByUserIdIsNull()).thenReturn(List.of(legacyResume));
        when(sessionRepository.findAllByUserIdIsNull()).thenReturn(List.of(migratable, alreadyOwnedResumeSession, orphan));

        LegacyOwnershipMigrationService.MigrationResult result = service(
            userRepository, resumeRepository, sessionRepository, auditRepository).preview(7L);

        assertFalse(result.applied());
        assertEquals(1, result.migratedResumeCount());
        assertEquals(1, result.migratedSessionCount());
        assertEquals(2, result.skippedOrphanSessionCount());
        assertNull(legacyResume.getUserId());
        assertNull(alreadyOwnedResumeSession.getUserId());
        verify(resumeRepository, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
        verify(sessionRepository, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
        verify(auditRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void applyMigratesOnlyUnownedResumeSessionsAndWritesAudit() {
        UserRepository userRepository = mock(UserRepository.class);
        ResumeRepository resumeRepository = mock(ResumeRepository.class);
        InterviewSessionRepository sessionRepository = mock(InterviewSessionRepository.class);
        LegacyOwnershipMigrationAuditRepository auditRepository = mock(LegacyOwnershipMigrationAuditRepository.class);
        ResumeEntity legacyResume = new ResumeEntity();
        legacyResume.setId(11L);
        InterviewSessionEntity migratable = session(legacyResume);
        ResumeEntity alreadyOwnedResume = new ResumeEntity();
        alreadyOwnedResume.setId(12L);
        alreadyOwnedResume.setUserId(7L);
        InterviewSessionEntity alreadyOwnedResumeSession = session(alreadyOwnedResume);
        InterviewSessionEntity orphan = session(null);

        when(userRepository.existsById(7L)).thenReturn(true);
        when(resumeRepository.findAllByUserIdIsNull()).thenReturn(List.of(legacyResume));
        when(sessionRepository.findAllByUserIdIsNull()).thenReturn(List.of(migratable, alreadyOwnedResumeSession, orphan));

        LegacyOwnershipMigrationService.MigrationResult result = service(
            userRepository, resumeRepository, sessionRepository, auditRepository).apply(7L);

        assertTrue(result.applied());
        assertEquals(7L, legacyResume.getUserId());
        assertEquals(7L, migratable.getUserId());
        assertNull(alreadyOwnedResumeSession.getUserId());
        assertNull(orphan.getUserId());
        verify(resumeRepository).saveAll(List.of(legacyResume));
        verify(sessionRepository).saveAll(List.of(migratable));
        ArgumentCaptor<LegacyOwnershipMigrationAuditEntity> audit = ArgumentCaptor.forClass(LegacyOwnershipMigrationAuditEntity.class);
        verify(auditRepository).save(audit.capture());
        assertEquals(7L, audit.getValue().getTargetUserId());
        assertEquals(1, audit.getValue().getMigratedResumeCount());
        assertEquals(1, audit.getValue().getMigratedSessionCount());
        assertEquals(2, audit.getValue().getSkippedOrphanSessionCount());
    }

    private LegacyOwnershipMigrationService service(UserRepository userRepository, ResumeRepository resumeRepository,
                                                    InterviewSessionRepository sessionRepository,
                                                    LegacyOwnershipMigrationAuditRepository auditRepository) {
        return new LegacyOwnershipMigrationService(userRepository, resumeRepository, sessionRepository, auditRepository);
    }

    private InterviewSessionEntity session(ResumeEntity resume) {
        InterviewSessionEntity session = new InterviewSessionEntity();
        session.setResume(resume);
        return session;
    }
}
