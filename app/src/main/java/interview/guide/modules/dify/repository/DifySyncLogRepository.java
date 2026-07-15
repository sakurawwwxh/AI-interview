package interview.guide.modules.dify.repository;

import interview.guide.modules.dify.model.DifySyncDirection;
import interview.guide.modules.dify.model.DifySyncLogEntity;
import interview.guide.modules.dify.model.DifySyncStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
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

    void deleteByCreatedAtBefore(LocalDateTime dateTime);
}
