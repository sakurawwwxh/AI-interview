package ai.interview.modules.target.repository;
import ai.interview.modules.target.model.JobTargetEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface JobTargetRepository extends JpaRepository<JobTargetEntity, Long> {
    List<JobTargetEntity> findAllByUserIdOrderByUpdatedAtDesc(Long userId);
    Optional<JobTargetEntity> findByIdAndUserId(Long id, Long userId);
    List<JobTargetEntity> findByUserIdAndActiveTrue(Long userId);
}
