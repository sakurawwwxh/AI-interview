package interview.guide.modules.resume.repository;

import interview.guide.modules.resume.model.ResumeVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ResumeVersionRepository extends JpaRepository<ResumeVersionEntity, Long> {
    List<ResumeVersionEntity> findByResumeIdAndUserIdOrderByVersionNumberDesc(Long resumeId, Long userId);
    Optional<ResumeVersionEntity> findByIdAndResumeIdAndUserId(Long id, Long resumeId, Long userId);
    long countByResumeIdAndUserId(Long resumeId, Long userId);
    void deleteByResumeId(Long resumeId);
}
