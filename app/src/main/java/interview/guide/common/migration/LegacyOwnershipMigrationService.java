package interview.guide.common.migration;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.model.InterviewSessionEntity;
import interview.guide.modules.interview.repository.InterviewSessionRepository;
import interview.guide.modules.knowledgebase.model.KnowledgeBaseEntity;
import interview.guide.modules.knowledgebase.model.RagChatSessionEntity;
import interview.guide.modules.knowledgebase.repository.KnowledgeBaseRepository;
import interview.guide.modules.knowledgebase.repository.RagChatSessionRepository;
import interview.guide.modules.resume.model.ResumeEntity;
import interview.guide.modules.resume.repository.ResumeRepository;
import interview.guide.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class LegacyOwnershipMigrationService {

    private final UserRepository userRepository;
    private final ResumeRepository resumeRepository;
    private final InterviewSessionRepository sessionRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final RagChatSessionRepository ragChatSessionRepository;
    private final LegacyOwnershipMigrationAuditRepository auditRepository;

    @Transactional(readOnly = true)
    public MigrationResult preview(Long targetUserId) {
        requireTargetUser(targetUserId);
        List<ResumeEntity> resumes = resumeRepository.findAllByUserIdIsNull();
        List<InterviewSessionEntity> sessions = sessionRepository.findAllByUserIdIsNull();
        List<KnowledgeBaseEntity> knowledgeBases = knowledgeBaseRepository.findAllByUserIdIsNull();
        List<RagChatSessionEntity> ragChatSessions = ragChatSessionRepository.findAllByUserIdIsNull();
        int migratableSessions = sessionsFor(resumes, sessions).size();
        return new MigrationResult(targetUserId, false,
            resumes.size(), migratableSessions, sessions.size() - migratableSessions,
            knowledgeBases.size(), ragChatSessions.size());
    }

    @Transactional
    public MigrationResult apply(Long targetUserId) {
        requireTargetUser(targetUserId);
        List<ResumeEntity> resumes = resumeRepository.findAllByUserIdIsNull();
        List<InterviewSessionEntity> sessions = sessionRepository.findAllByUserIdIsNull();
        List<KnowledgeBaseEntity> knowledgeBases = knowledgeBaseRepository.findAllByUserIdIsNull();
        List<RagChatSessionEntity> ragChatSessions = ragChatSessionRepository.findAllByUserIdIsNull();
        List<InterviewSessionEntity> migratableSessions = sessionsFor(resumes, sessions);

        resumes.forEach(resume -> resume.setUserId(targetUserId));
        resumeRepository.saveAll(resumes);

        migratableSessions.forEach(session -> session.setUserId(targetUserId));
        sessionRepository.saveAll(migratableSessions);

        knowledgeBases.forEach(kb -> kb.setUserId(targetUserId));
        knowledgeBaseRepository.saveAll(knowledgeBases);

        ragChatSessions.forEach(session -> session.setUserId(targetUserId));
        ragChatSessionRepository.saveAll(ragChatSessions);

        int orphanedSessions = sessions.size() - migratableSessions.size();
        auditRepository.save(new LegacyOwnershipMigrationAuditEntity(
            targetUserId, resumes.size(), migratableSessions.size(), orphanedSessions,
            knowledgeBases.size(), ragChatSessions.size()));
        return new MigrationResult(targetUserId, true,
            resumes.size(), migratableSessions.size(), orphanedSessions,
            knowledgeBases.size(), ragChatSessions.size());
    }

    private void requireTargetUser(Long targetUserId) {
        if (targetUserId == null || !userRepository.existsById(targetUserId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "Legacy migration target user does not exist");
        }
    }

    private List<InterviewSessionEntity> sessionsFor(List<ResumeEntity> resumes,
                                                      List<InterviewSessionEntity> sessions) {
        Set<Long> migratedResumeIds = resumes.stream()
            .map(ResumeEntity::getId)
            .filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.toSet());
        return sessions.stream()
            .filter(session -> session.getResume() != null
                && migratedResumeIds.contains(session.getResume().getId()))
            .toList();
    }

    public record MigrationResult(Long targetUserId, boolean applied, int migratedResumeCount,
                                  int migratedSessionCount, int skippedOrphanSessionCount,
                                  int migratedKnowledgeBaseCount, int migratedRagChatSessionCount) {
    }
}
