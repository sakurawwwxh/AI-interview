package ai.interview.modules.dify.repository;

import ai.interview.modules.dify.model.DifySyncAction;
import ai.interview.modules.dify.model.DifySyncDirection;
import ai.interview.modules.dify.model.DifySyncLogEntity;
import ai.interview.modules.dify.model.DifySyncStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Dify 同步日志仓库
 */
@Repository
public interface DifySyncLogRepository extends JpaRepository<DifySyncLogEntity, Long> {

    Optional<DifySyncLogEntity> findFirstByKnowledgeBaseIdOrderByCreatedAtDesc(Long knowledgeBaseId);

    List<DifySyncLogEntity> findByKnowledgeBaseIdOrderByCreatedAtDesc(Long knowledgeBaseId, PageRequest pageRequest);

    List<DifySyncLogEntity> findBySyncStatus(DifySyncStatus status);

    List<DifySyncLogEntity> findBySyncDirection(DifySyncDirection direction);

    List<DifySyncLogEntity> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    @Query("SELECT COUNT(s) FROM DifySyncLogEntity s WHERE s.knowledgeBaseId = :kbId AND s.syncStatus = :status")
    long countByKnowledgeBaseIdAndStatus(@Param("kbId") Long knowledgeBaseId, @Param("status") DifySyncStatus status);

    /**
     * 该 Dify 文档是否存在本地删除墓碑。存在即表示「本地主动删过」，反向同步不得再把它拉回来。
     */
    boolean existsByDifyDocumentIdAndSyncAction(String difyDocumentId, DifySyncAction syncAction);

    /**
     * 最近一次针对某个 Dify 文档的某类操作日志（用于更新墓碑状态）。
     */
    Optional<DifySyncLogEntity> findFirstByDifyDocumentIdAndSyncActionOrderByCreatedAtDesc(
        String difyDocumentId, DifySyncAction syncAction);

    /**
     * 需要重试的删除墓碑：未成功且还在重试窗口内的记录，按时间从旧到新。
     */
    @Query("""
        SELECT s FROM DifySyncLogEntity s
         WHERE s.syncAction = :action
           AND s.syncStatus IN :statuses
           AND s.createdAt >= :createdAfter
         ORDER BY s.createdAt ASC
        """)
    List<DifySyncLogEntity> findRetryable(
        @Param("action") DifySyncAction action,
        @Param("statuses") Collection<DifySyncStatus> statuses,
        @Param("createdAfter") LocalDateTime createdAfter,
        Pageable pageable);

    /**
     * 超过重试窗口仍未完成的墓碑数量，用于告警——这些远端文档不会再被自动清理。
     */
    long countBySyncActionAndSyncStatusInAndCreatedAtBefore(
        DifySyncAction syncAction, Collection<DifySyncStatus> syncStatuses, LocalDateTime createdAt);

    /**
     * 清理已完成的旧墓碑。删除成功意味着远端文档已不存在，不再需要拦着反向同步。
     */
    @Transactional
    @Modifying
    long deleteBySyncActionAndSyncStatusAndCreatedAtBefore(
        DifySyncAction syncAction, DifySyncStatus syncStatus, LocalDateTime createdAt);

    void deleteByCreatedAtBefore(LocalDateTime dateTime);
}
