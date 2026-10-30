package ai.interview.modules.target.repository;

import ai.interview.modules.target.model.JobMatchEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface JobMatchRepository extends JpaRepository<JobMatchEntity, Long> {

    /** 某简历 + 某岗位 最近一次匹配 */
    Optional<JobMatchEntity> findFirstByUserIdAndResumeIdAndJobTargetIdOrderByAnalyzedAtDesc(
        Long userId, Long resumeId, Long jobTargetId);

    /** 某简历下全部匹配记录（新→旧） */
    List<JobMatchEntity> findByUserIdAndResumeIdOrderByAnalyzedAtDesc(Long userId, Long resumeId);

    /**
     * 派生 delete 在只读事务里不会真正落库（SimpleJpaRepository 类级 readOnly），
     * 这里改成显式修改查询，行为确定且能被调用方的事务覆盖。
     */
    @Transactional
    @Modifying
    @Query("DELETE FROM JobMatchEntity m WHERE m.resumeId = :resumeId")
    int deleteByResumeId(@Param("resumeId") Long resumeId);

    @Transactional
    @Modifying
    @Query("DELETE FROM JobMatchEntity m WHERE m.jobTargetId = :jobTargetId")
    int deleteByJobTargetId(@Param("jobTargetId") Long jobTargetId);
}
