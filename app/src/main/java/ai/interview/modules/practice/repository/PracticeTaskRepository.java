package ai.interview.modules.practice.repository;

import ai.interview.modules.practice.model.PracticeTaskEntity;
import ai.interview.modules.practice.model.PracticeTaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

public interface PracticeTaskRepository extends JpaRepository<PracticeTaskEntity, Long> {

    boolean existsBySourceAnswerId(Long sourceAnswerId);

    Optional<PracticeTaskEntity> findByIdAndUserId(Long id, Long userId);

    /** 提交复练时锁行，避免并发提交把 attemptCount 覆盖成同一个值。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM PracticeTaskEntity t WHERE t.id = :id AND t.userId = :userId")
    Optional<PracticeTaskEntity> findByIdAndUserIdForUpdate(@Param("id") Long id, @Param("userId") Long userId);

    Page<PracticeTaskEntity> findByUserId(Long userId, Pageable pageable);
    Page<PracticeTaskEntity> findByUserIdAndStatus(Long userId, PracticeTaskStatus status, Pageable pageable);
    Page<PracticeTaskEntity> findByUserIdAndCategoryIgnoreCase(Long userId, String category, Pageable pageable);
    Page<PracticeTaskEntity> findByUserIdAndStatusAndCategoryIgnoreCase(Long userId, PracticeTaskStatus status,
                                                                         String category, Pageable pageable);

    long countByUserIdAndStatus(Long userId, PracticeTaskStatus status);
    long countByUserIdAndCompletedAtGreaterThanEqual(Long userId, LocalDateTime completedAt);
    List<PracticeTaskEntity> findByUserIdAndStatusAndNextReviewAtLessThanEqual(Long userId, PracticeTaskStatus status, LocalDateTime reviewAt);

    @Query("select avg(t.lastScore - t.originalScore) from PracticeTaskEntity t " +
           "where t.userId = :userId and t.status = :status")
    Double averageImprovementByUserId(@Param("userId") Long userId, @Param("status") PracticeTaskStatus status);
}
