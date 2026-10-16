package interview.guide.modules.interview.repository;

import interview.guide.modules.interview.model.InterviewSessionEntity;
import interview.guide.modules.interview.model.InterviewSessionEntity.SessionStatus;
import interview.guide.modules.resume.model.ResumeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 面试会话 Repository
 */
@Repository
public interface InterviewSessionRepository extends JpaRepository<InterviewSessionEntity, Long> {

    /**
     * 根据会话ID查找
     */
    Optional<InterviewSessionEntity> findBySessionId(String sessionId);

    /**
     * 根据会话ID查找（同时加载关联的简历）
     */
    @Query("SELECT s FROM InterviewSessionEntity s JOIN FETCH s.resume WHERE s.sessionId = :sessionId")
    Optional<InterviewSessionEntity> findBySessionIdWithResume(@Param("sessionId") String sessionId);

    /**
     * 根据会话ID和用户ID查找（鉴权）
     */
    Optional<InterviewSessionEntity> findBySessionIdAndUserId(String sessionId, Long userId);

    /**
     * 根据简历查找所有面试记录
     */
    List<InterviewSessionEntity> findByResumeOrderByCreatedAtDesc(ResumeEntity resume);

    /**
     * 根据简历ID查找所有面试记录
     */
    List<InterviewSessionEntity> findByResumeIdOrderByCreatedAtDesc(Long resumeId);

    /**
     * 统计看板的会话行：只含聚合用到的标量，外加「题目序号:题目类型」串。
     * <p>
     * 这里刻意不再返回实体或 JOIN FETCH，原因有两个，都会让远端库上的单次请求从约 0.3 秒涨到十几秒：
     * <ol>
     *   <li>会话表的 questions_json 等 TEXT 列可达几十 KB。一旦与会话下的答案做 JOIN，
     *       这些列会按答案条数逐行重复——2 场面试就能把响应撑到 1.4 MB。</li>
     *   <li>答案自身的 user_answer / feedback / reference_answer 同样是大文本，
     *       而统计只需要 question_index、category、score。</li>
     * </ol>
     * 题目类型需要从 questions_json 里取，因此在数据库侧就裁成
     * {@code questionIndex:type} 再返回，而不是把整段 JSON 搬到应用层解析。
     * 取值用 case 包住并先过 pg_input_is_valid（PostgreSQL 16+，与 docker-compose 的
     * pgvector:pg16 基线一致）：个别损坏的 JSON 退化为空串，行为等价于原先应用层解析失败后
     * 回退到答案自身的 category，而不是让整个统计接口报错。
     * <p>
     * 取最近的 limit 场（倒序），由调用方反转成时间正序用于趋势展示。
     */
    interface StatisticsSessionRow {
        Long getId();

        String getSessionId();

        LocalDateTime getCompletedAt();

        LocalDateTime getCreatedAt();

        Integer getOverallScore();

        /** 形如 {@code 0:PROJECT,1:JAVA_BASIC}；questions_json 缺失或损坏时为空串 */
        String getQuestionTypes();
    }

    @Query(value = """
        select s.id as "id",
               s.session_id as "sessionId",
               s.completed_at as "completedAt",
               s.created_at as "createdAt",
               s.overall_score as "overallScore",
               case when s.questions_json is not null
                     and pg_input_is_valid(s.questions_json, 'jsonb')
                    then case when jsonb_typeof(s.questions_json::jsonb) = 'array'
                              then coalesce((
                                  select string_agg(coalesce(q ->> 'questionIndex', '') || ':' || coalesce(q ->> 'type', ''), ','
                                                    order by (q ->> 'questionIndex'))
                                  from jsonb_array_elements(s.questions_json::jsonb) q), '')
                              else '' end
                    else '' end as "questionTypes"
        from interview_sessions s
        where s.overall_score is not null
          and s.user_id = :userId
        order by s.completed_at desc, s.created_at desc
        limit :limit
        """, nativeQuery = true)
    List<StatisticsSessionRow> findStatisticsSessions(@Param("userId") Long userId, @Param("limit") int limit);

    List<InterviewSessionEntity> findAllByUserIdIsNull();

    /**
     * 根据简历ID查找最近的面试记录（用于历史题去重）
     */
    List<InterviewSessionEntity> findTop10ByResumeIdOrderByCreatedAtDesc(Long resumeId);

    /**
     * 查找简历的未完成面试（CREATED或IN_PROGRESS状态）
     */
    Optional<InterviewSessionEntity> findFirstByResumeIdAndStatusInOrderByCreatedAtDesc(
        Long resumeId,
        List<SessionStatus> statuses
    );

    /**
     * 根据简历ID和状态查找会话
     */
    Optional<InterviewSessionEntity> findByResumeIdAndStatusIn(
        Long resumeId,
        List<SessionStatus> statuses
    );

    /**
     * 面试记录列表行：只投影列表真正渲染的列。
     * <p>
     * 这里刻意不用实体 + JOIN FETCH resume：会话的 questions_json / template_json 等
     * 大文本列（单场可达几十 KB）和简历的 resume_text 都会随实体一起取回，而这些列
     * 列表一个都不需要。远端库上实测载荷差约 110 倍（72 KB → 0.6 KB），
     * 在丢包链路上直接表现为记录列表十几秒打不开。
     * <p>
     * status / evaluate_status 以字符串读取：实体上是 {@code @Enumerated(EnumType.STRING)}，
     * 库里存的就是枚举名，和原先 {@code getStatus().name()} 的取值一致。
     */
    interface InterviewListRow {
        Long getId();

        String getSessionId();

        Long getResumeId();

        String getResumeFilename();

        Integer getTotalQuestions();

        String getStatus();

        String getEvaluateStatus();

        String getEvaluateError();

        Integer getEvaluateProgress();

        Integer getOverallScore();

        String getOverallFeedback();

        LocalDateTime getCreatedAt();

        LocalDateTime getCompletedAt();
    }

    @Query(value = """
        select s.id as "id",
               s.session_id as "sessionId",
               r.id as "resumeId",
               r.original_filename as "resumeFilename",
               s.total_questions as "totalQuestions",
               s.status as "status",
               s.evaluate_status as "evaluateStatus",
               s.evaluate_error as "evaluateError",
               s.evaluate_progress as "evaluateProgress",
               s.overall_score as "overallScore",
               s.overall_feedback as "overallFeedback",
               s.created_at as "createdAt",
               s.completed_at as "completedAt"
        from interview_sessions s
        left join resumes r on r.id = s.resume_id
        where s.user_id = :userId
        order by s.created_at desc
        """, nativeQuery = true)
    List<InterviewListRow> findListRowsByUserId(@Param("userId") Long userId);

    /**
     * 简历详情里的面试历史行。同一份详情页只需要 9 个标量，
     * 此前走实体查询会把每场的 questions_json / template_json / 点评等大 TEXT 一起取回
     * （按简历下会话数累加，多的能到几百 KB），在远端库上要好几秒——与列表页同一病根。
     */
    interface ResumeInterviewRow {
        Long getId();

        String getSessionId();

        Integer getTotalQuestions();

        /** 实体上是 {@code @Enumerated(EnumType.STRING)}，库里存的就是枚举名 */
        String getStatus();

        String getEvaluateStatus();

        String getEvaluateError();

        Integer getOverallScore();

        LocalDateTime getCreatedAt();

        LocalDateTime getCompletedAt();
    }

    @Query(value = """
        select s.id as "id",
               s.session_id as "sessionId",
               s.total_questions as "totalQuestions",
               s.status as "status",
               s.evaluate_status as "evaluateStatus",
               s.evaluate_error as "evaluateError",
               s.overall_score as "overallScore",
               s.created_at as "createdAt",
               s.completed_at as "completedAt"
        from interview_sessions s
        where s.resume_id = :resumeId
        order by s.created_at desc
        """, nativeQuery = true)
    List<ResumeInterviewRow> findResumeInterviewRowsByResumeId(@Param("resumeId") Long resumeId);
}
