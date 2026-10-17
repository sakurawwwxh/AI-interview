package ai.interview.modules.practice.repository;

import ai.interview.modules.practice.model.PracticeAttemptEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PracticeAttemptRepository extends JpaRepository<PracticeAttemptEntity, Long> {
    List<PracticeAttemptEntity> findByTaskIdOrderByCreatedAtDesc(Long taskId);
}
