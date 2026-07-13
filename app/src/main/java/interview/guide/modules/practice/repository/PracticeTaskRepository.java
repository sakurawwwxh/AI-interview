package interview.guide.modules.practice.repository;

import interview.guide.modules.practice.model.PracticeTaskEntity;
import interview.guide.modules.practice.model.PracticeTaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PracticeTaskRepository extends JpaRepository<PracticeTaskEntity, Long> {

    boolean existsBySourceAnswerId(Long sourceAnswerId);

    Optional<PracticeTaskEntity> findByIdAndUserId(Long id, Long userId);

    Page<PracticeTaskEntity> findByUserId(Long userId, Pageable pageable);
    Page<PracticeTaskEntity> findByUserIdAndStatus(Long userId, PracticeTaskStatus status, Pageable pageable);
    Page<PracticeTaskEntity> findByUserIdAndCategoryIgnoreCase(Long userId, String category, Pageable pageable);
    Page<PracticeTaskEntity> findByUserIdAndStatusAndCategoryIgnoreCase(Long userId, PracticeTaskStatus status,
                                                                         String category, Pageable pageable);

    long countByUserIdAndStatus(Long userId, PracticeTaskStatus status);
    long countByUserIdAndCompletedAtGreaterThanEqual(Long userId, LocalDateTime completedAt);

    @Query("select avg(t.lastScore - t.originalScore) from PracticeTaskEntity t " +
           "where t.userId = :userId and t.status = :status")
    Double averageImprovementByUserId(@Param("userId") Long userId, @Param("status") PracticeTaskStatus status);
}
