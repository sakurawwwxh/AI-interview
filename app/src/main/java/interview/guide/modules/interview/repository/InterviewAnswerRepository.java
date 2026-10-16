package interview.guide.modules.interview.repository;

import interview.guide.modules.interview.model.InterviewAnswerEntity;
import interview.guide.modules.interview.model.InterviewSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 面试答案Repository
 */
@Repository
public interface InterviewAnswerRepository extends JpaRepository<InterviewAnswerEntity, Long> {
    
    /**
     * 根据会话查找所有答案
     */
    List<InterviewAnswerEntity> findBySessionOrderByQuestionIndex(InterviewSessionEntity session);
    
    /**
     * 根据会话ID查找所有答案
     */
    List<InterviewAnswerEntity> findBySessionIdOrderByQuestionIndex(Long sessionId);
    
    /**
     * 根据会话 sessionId 字符串查找所有答案
     */
    List<InterviewAnswerEntity> findBySession_SessionIdOrderByQuestionIndex(String sessionId);

    /**
     * 根据会话 sessionId 和问题索引查找单条答案（用于 upsert）
     */
    Optional<InterviewAnswerEntity> findBySession_SessionIdAndQuestionIndex(String sessionId, Integer questionIndex);

    /**
     * 统计看板专用的答案行。
     * <p>
     * 只投影聚合需要的三列：答案实体上的 user_answer / feedback / reference_answer /
     * key_points_json 都是大文本，按实体取回会让远端库上的统计请求从约 0.3 秒涨到十几秒。
     * 已评分的会话里 score 为空的行对聚合没有贡献，直接在 SQL 里过滤掉。
     */
    interface AnswerScoreRow {
        Long getSessionId();

        Integer getQuestionIndex();

        String getCategory();

        Integer getScore();
    }

    @Query("""
        SELECT a.session.id AS sessionId,
               a.questionIndex AS questionIndex,
               a.category AS category,
               a.score AS score
        FROM InterviewAnswerEntity a
        WHERE a.score IS NOT NULL
          AND a.session.id IN :sessionIds
        """)
    List<AnswerScoreRow> findAnswerScoresBySessionIds(@Param("sessionIds") Collection<Long> sessionIds);
}
