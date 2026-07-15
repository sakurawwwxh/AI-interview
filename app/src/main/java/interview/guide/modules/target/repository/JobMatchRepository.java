package interview.guide.modules.target.repository;

import interview.guide.modules.target.model.JobMatchEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JobMatchRepository extends JpaRepository<JobMatchEntity, Long> {

    /** 某简历 + 某岗位 最近一次匹配 */
    Optional<JobMatchEntity> findFirstByUserIdAndResumeIdAndJobTargetIdOrderByAnalyzedAtDesc(
        Long userId, Long resumeId, Long jobTargetId);

    /** 某简历下全部匹配记录（新→旧） */
    List<JobMatchEntity> findByUserIdAndResumeIdOrderByAnalyzedAtDesc(Long userId, Long resumeId);

    void deleteByResumeId(Long resumeId);

    void deleteByJobTargetId(Long jobTargetId);
}
