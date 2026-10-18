package ai.interview.modules.resume.repository;

import ai.interview.common.model.AsyncTaskStatus;
import ai.interview.modules.resume.model.ResumeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Collection;

/**
 * 简历 Repository（按用户隔离）
 */
@Repository
public interface ResumeRepository extends JpaRepository<ResumeEntity, Long> {

    /**
     * 简历列表的投影。使用一条 PostgreSQL 查询取回最新评分和面试次数，避免列表页 N+1 往返。
     */
    interface ResumeListRow {
        Long getId();
        String getFilename();
        Long getFileSize();
        LocalDateTime getUploadedAt();
        Integer getAccessCount();
        Integer getLatestScore();
        LocalDateTime getLastAnalyzedAt();
        Long getInterviewCount();
    }

    @Query(value = """
        select r.id as "id",
               r.original_filename as "filename",
               r.file_size as "fileSize",
               r.uploaded_at as "uploadedAt",
               r.access_count as "accessCount",
               latest.overall_score as "latestScore",
               latest.analyzed_at as "lastAnalyzedAt",
               interviews.interview_count as "interviewCount"
        from resumes r
        left join lateral (
            select ra.overall_score, ra.analyzed_at
            from resume_analyses ra
            where ra.resume_id = r.id
            order by ra.analyzed_at desc, ra.id desc
            limit 1
        ) latest on true
        left join lateral (
            select count(*) as interview_count
            from interview_sessions s
            where s.resume_id = r.id
        ) interviews on true
        where r.user_id = :userId
        order by r.uploaded_at desc
        """, nativeQuery = true)
    List<ResumeListRow> findListRowsByUserId(@Param("userId") Long userId);

    /**
     * 根据文件哈希和用户 ID 查找简历（用于去重）
     */
    Optional<ResumeEntity> findByFileHashAndUserId(String fileHash, Long userId);

    /**
     * 检查文件哈希在指定用户下是否存在
     */
    boolean existsByFileHashAndUserId(String fileHash, Long userId);

    /**
     * 查找用户的所有简历（按上传时间倒序）
     */
    List<ResumeEntity> findAllByUserIdOrderByUploadedAtDesc(Long userId);

    /**
     * 按 ID 和用户 ID 查找（鉴权）
     */
    Optional<ResumeEntity> findByIdAndUserId(Long id, Long userId);

    /**
     * Atomically claims a terminal resume for re-analysis. The status predicate is important:
     * two concurrent retry requests may both read COMPLETED, but only one may transition it to PENDING.
     *
     * <p>正文回填与状态变更放在同一条语句里：分开写的话，claim 提交后、回填前如果失败，
     * 简历会永久停在 PENDING —— 而重试入口对 PENDING 一律拒绝，用户只能等一个不会到来的任务。
     * {@code resumeText} 传 null 表示沿用库里已有的正文。
     *
     * <p>自带事务：调用方不再为「下载并解析简历」这类耗时动作开事务，这条语句必须能独立提交。
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update ResumeEntity r
           set r.analyzeStatus = :pending,
               r.analyzeError = null,
               r.resumeText = coalesce(cast(:resumeText as string), r.resumeText)
         where r.id = :resumeId
           and r.userId = :userId
           and (r.analyzeStatus is null or r.analyzeStatus in :retryableStatuses)
        """)
    int claimForReanalysis(
        @Param("resumeId") Long resumeId,
        @Param("userId") Long userId,
        @Param("pending") AsyncTaskStatus pending,
        @Param("retryableStatuses") Collection<AsyncTaskStatus> retryableStatuses,
        @Param("resumeText") String resumeText
    );

    List<ResumeEntity> findAllByUserIdIsNull();
}
