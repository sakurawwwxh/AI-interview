package interview.guide.common.migration;

import interview.guide.modules.interview.model.InterviewSessionEntity;
import interview.guide.modules.interview.repository.InterviewSessionRepository;
import interview.guide.modules.knowledgebase.model.KnowledgeBaseEntity;
import interview.guide.modules.knowledgebase.model.RagChatSessionEntity;
import interview.guide.modules.knowledgebase.repository.KnowledgeBaseRepository;
import interview.guide.modules.knowledgebase.repository.RagChatSessionRepository;
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
        var mocks = mocks();
        ResumeEntity legacyResume = new ResumeEntity();
        legacyResume.setId(11L);
        InterviewSessionEntity migratable = session(legacyResume);
        ResumeEntity alreadyOwnedResume = new ResumeEntity();
        alreadyOwnedResume.setId(12L);
        alreadyOwnedResume.setUserId(7L);
        InterviewSessionEntity alreadyOwnedResumeSession = session(alreadyOwnedResume);
        InterviewSessionEntity orphan = session(null);
        KnowledgeBaseEntity legacyKb = new KnowledgeBaseEntity();
        legacyKb.setId(21L);
        RagChatSessionEntity legacyRagSession = new RagChatSessionEntity();

        when(mocks.userRepository.existsById(7L)).thenReturn(true);
        when(mocks.resumeRepository.findAllByUserIdIsNull()).thenReturn(List.of(legacyResume));
        when(mocks.sessionRepository.findAllByUserIdIsNull()).thenReturn(List.of(migratable, alreadyOwnedResumeSession, orphan));
        when(mocks.knowledgeBaseRepository.findAllByUserIdIsNull()).thenReturn(List.of(legacyKb));
        when(mocks.ragChatSessionRepository.findAllByUserIdIsNull()).thenReturn(List.of(legacyRagSession));

        LegacyOwnershipMigrationService.MigrationResult result = mocks.service().preview(7L);

        assertFalse(result.applied());
        assertEquals(1, result.migratedResumeCount());
        assertEquals(1, result.migratedSessionCount());
        assertEquals(2, result.skippedOrphanSessionCount());
        assertEquals(1, result.migratedKnowledgeBaseCount());
        assertEquals(1, result.migratedRagChatSessionCount());
        assertNull(legacyResume.getUserId());
        assertNull(legacyKb.getUserId());
        assertNull(legacyRagSession.getUserId());
        verify(mocks.resumeRepository, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
        verify(mocks.sessionRepository, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
        verify(mocks.knowledgeBaseRepository, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
        verify(mocks.ragChatSessionRepository, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
        verify(mocks.auditRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void applyMigratesOnlyUnownedResumeSessionsAndWritesAudit() {
        var mocks = mocks();
        ResumeEntity legacyResume = new ResumeEntity();
        legacyResume.setId(11L);
        InterviewSessionEntity migratable = session(legacyResume);
        ResumeEntity alreadyOwnedResume = new ResumeEntity();
        alreadyOwnedResume.setId(12L);
        alreadyOwnedResume.setUserId(7L);
        InterviewSessionEntity alreadyOwnedResumeSession = session(alreadyOwnedResume);
        InterviewSessionEntity orphan = session(null);
        KnowledgeBaseEntity legacyKb = new KnowledgeBaseEntity();
        legacyKb.setId(21L);
        RagChatSessionEntity legacyRagSession = new RagChatSessionEntity();

        when(mocks.userRepository.existsById(7L)).thenReturn(true);
        when(mocks.resumeRepository.findAllByUserIdIsNull()).thenReturn(List.of(legacyResume));
        when(mocks.sessionRepository.findAllByUserIdIsNull()).thenReturn(List.of(migratable, alreadyOwnedResumeSession, orphan));
        when(mocks.knowledgeBaseRepository.findAllByUserIdIsNull()).thenReturn(List.of(legacyKb));
        when(mocks.ragChatSessionRepository.findAllByUserIdIsNull()).thenReturn(List.of(legacyRagSession));

        LegacyOwnershipMigrationService.MigrationResult result = mocks.service().apply(7L);

        assertTrue(result.applied());
        assertEquals(7L, legacyResume.getUserId());
        assertEquals(7L, migratable.getUserId());
        assertEquals(7L, legacyKb.getUserId());
        assertEquals(7L, legacyRagSession.getUserId());
        assertNull(alreadyOwnedResumeSession.getUserId());
        assertNull(orphan.getUserId());
        verify(mocks.resumeRepository).saveAll(List.of(legacyResume));
        verify(mocks.sessionRepository).saveAll(List.of(migratable));
        verify(mocks.knowledgeBaseRepository).saveAll(List.of(legacyKb));
        verify(mocks.ragChatSessionRepository).saveAll(List.of(legacyRagSession));
        ArgumentCaptor<LegacyOwnershipMigrationAuditEntity> audit = ArgumentCaptor.forClass(LegacyOwnershipMigrationAuditEntity.class);
        verify(mocks.auditRepository).save(audit.capture());
        assertEquals(7L, audit.getValue().getTargetUserId());
        assertEquals(1, audit.getValue().getMigratedResumeCount());
        assertEquals(1, audit.getValue().getMigratedSessionCount());
        assertEquals(2, audit.getValue().getSkippedOrphanSessionCount());
        assertEquals(1, audit.getValue().getMigratedKnowledgeBaseCount());
        assertEquals(1, audit.getValue().getMigratedRagChatSessionCount());
    }

    private record Mocks(
        UserRepository userRepository,
        ResumeRepository resumeRepository,
        InterviewSessionRepository sessionRepository,
        KnowledgeBaseRepository knowledgeBaseRepository,
        RagChatSessionRepository ragChatSessionRepository,
        LegacyOwnershipMigrationAuditRepository auditRepository
    ) {
        LegacyOwnershipMigrationService service() {
            return new LegacyOwnershipMigrationService(userRepository, resumeRepository, sessionRepository,
                knowledgeBaseRepository, ragChatSessionRepository, auditRepository);
        }
    }

    private Mocks mocks() {
        return new Mocks(
            mock(UserRepository.class),
            mock(ResumeRepository.class),
            mock(InterviewSessionRepository.class),
            mock(KnowledgeBaseRepository.class),
            mock(RagChatSessionRepository.class),
            mock(LegacyOwnershipMigrationAuditRepository.class)
        );
    }

    private InterviewSessionEntity session(ResumeEntity resume) {
        InterviewSessionEntity session = new InterviewSessionEntity();
        session.setResume(resume);
        return session;
    }
}
