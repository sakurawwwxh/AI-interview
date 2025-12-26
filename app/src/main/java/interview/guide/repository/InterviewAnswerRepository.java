package interview.guide.repository;

import interview.guide.entity.InterviewAnswerEntity;
import interview.guide.entity.InterviewSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

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
}
