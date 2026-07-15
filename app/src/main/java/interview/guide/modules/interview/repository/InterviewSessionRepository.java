package interview.guide.modules.interview.repository;

import interview.guide.modules.interview.model.InterviewSessionEntity;
import interview.guide.modules.interview.model.InterviewSessionEntity.SessionStatus;
import interview.guide.modules.resume.model.ResumeEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 面试会话 Repository
 */
@Repository
public interface InterviewSessionRepository extends JpaRepository<InterviewSessionEntity, Long> {

    /**
     * 根据会话ID查找
     */
    Optional<InterviewSessionEntity> findBySessionId(String sessionId);

    /**
     * 根据会话ID查找（同时加载关联的简历）
     */
    @Query("SELECT s FROM InterviewSessionEntity s JOIN FETCH s.resume WHERE s.sessionId = :sessionId")
    Optional<InterviewSessionEntity> findBySessionIdWithResume(@Param("sessionId") String sessionId);

    /**
     * 根据会话ID和用户ID查找（鉴权）
     */
    Optional<InterviewSessionEntity> findBySessionIdAndUserId(String sessionId, Long userId);

    /**
     * 根据简历查找所有面试记录
     */
    List<InterviewSessionEntity> findByResumeOrderByCreatedAtDesc(ResumeEntity resume);

    /**
     * 根据简历ID查找所有面试记录
     */
    List<InterviewSessionEntity> findByResumeIdOrderByCreatedAtDesc(Long resumeId);

    /**
     * 查询用户所有已有评分的面试及其答案，用于统计看板。
     * 使用 Pageable 限制数量，避免会话增长后全表扫描 + JOIN FETCH 笛卡尔积膨胀。
     */
    @Query("SELECT s.id FROM InterviewSessionEntity s " +
           "WHERE s.overallScore IS NOT NULL AND s.userId = :userId " +
           "ORDER BY s.completedAt ASC, s.createdAt ASC")
    List<Long> findEvaluatedIdsByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT DISTINCT s FROM InterviewSessionEntity s LEFT JOIN FETCH s.answers WHERE s.id IN :ids")
    List<InterviewSessionEntity> findEvaluatedWithAnswersByIdIn(@Param("ids") List<Long> ids);

    List<InterviewSessionEntity> findAllByUserIdIsNull();

    /**
     * 根据简历ID查找最近的面试记录（用于历史题去重）
     */
    List<InterviewSessionEntity> findTop10ByResumeIdOrderByCreatedAtDesc(Long resumeId);

    /**
     * 查找简历的未完成面试（CREATED或IN_PROGRESS状态）
     */
    Optional<InterviewSessionEntity> findFirstByResumeIdAndStatusInOrderByCreatedAtDesc(
        Long resumeId,
        List<SessionStatus> statuses
    );

    /**
     * 根据简历ID和状态查找会话
     */
    Optional<InterviewSessionEntity> findByResumeIdAndStatusIn(
        Long resumeId,
        List<SessionStatus> statuses
    );

    /**
     * 按用户拉取全部面试（含简历，用于记录列表，避免前端 N+1）
     */
    @Query("SELECT s FROM InterviewSessionEntity s JOIN FETCH s.resume WHERE s.userId = :userId ORDER BY s.createdAt DESC")
    List<InterviewSessionEntity> findAllByUserIdWithResumeOrderByCreatedAtDesc(@Param("userId") Long userId);
}
