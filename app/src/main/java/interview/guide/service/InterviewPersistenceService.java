package interview.guide.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import interview.guide.dto.InterviewQuestionDTO;
import interview.guide.dto.InterviewReportDTO;
import interview.guide.entity.InterviewAnswerEntity;
import interview.guide.entity.InterviewSessionEntity;
import interview.guide.entity.ResumeEntity;
import interview.guide.repository.InterviewAnswerRepository;
import interview.guide.repository.InterviewSessionRepository;
import interview.guide.repository.ResumeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 面试持久化服务
 * Interview Persistence Service
 */
@Service
public class InterviewPersistenceService {
    
    private static final Logger log = LoggerFactory.getLogger(InterviewPersistenceService.class);
    
    private final InterviewSessionRepository sessionRepository;
    private final InterviewAnswerRepository answerRepository;
    private final ResumeRepository resumeRepository;
    private final ObjectMapper objectMapper;
    
    public InterviewPersistenceService(InterviewSessionRepository sessionRepository,
                                       InterviewAnswerRepository answerRepository,
                                       ResumeRepository resumeRepository,
                                       ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.answerRepository = answerRepository;
        this.resumeRepository = resumeRepository;
        this.objectMapper = objectMapper;
    }
    
    /**
     * 保存新的面试会话
     */
    @Transactional
    public InterviewSessionEntity saveSession(String sessionId, Long resumeId, 
                                              int totalQuestions, 
                                              List<InterviewQuestionDTO> questions) {
        try {
            Optional<ResumeEntity> resumeOpt = resumeRepository.findById(resumeId);
            if (resumeOpt.isEmpty()) {
                throw new RuntimeException("简历不存在: " + resumeId);
            }
            
            InterviewSessionEntity session = new InterviewSessionEntity();
            session.setSessionId(sessionId);
            session.setResume(resumeOpt.get());
            session.setTotalQuestions(totalQuestions);
            session.setCurrentQuestionIndex(0);
            session.setStatus(InterviewSessionEntity.SessionStatus.CREATED);
            session.setQuestionsJson(objectMapper.writeValueAsString(questions));
            
            InterviewSessionEntity saved = sessionRepository.save(session);
            log.info("面试会话已保存: sessionId={}, resumeId={}", sessionId, resumeId);
            
            return saved;
        } catch (JsonProcessingException e) {
            log.error("序列化问题列表失败: {}", e.getMessage(), e);
            throw new RuntimeException("保存会话失败", e);
        }
    }
    
    /**
     * 更新会话状态
     */
    @Transactional
    public void updateSessionStatus(String sessionId, InterviewSessionEntity.SessionStatus status) {
        Optional<InterviewSessionEntity> sessionOpt = sessionRepository.findBySessionId(sessionId);
        if (sessionOpt.isPresent()) {
            InterviewSessionEntity session = sessionOpt.get();
            session.setStatus(status);
            if (status == InterviewSessionEntity.SessionStatus.COMPLETED || 
                status == InterviewSessionEntity.SessionStatus.EVALUATED) {
                session.setCompletedAt(LocalDateTime.now());
            }
            sessionRepository.save(session);
        }
    }
    
    /**
     * 更新当前问题索引
     */
    @Transactional
    public void updateCurrentQuestionIndex(String sessionId, int index) {
        Optional<InterviewSessionEntity> sessionOpt = sessionRepository.findBySessionId(sessionId);
        if (sessionOpt.isPresent()) {
            InterviewSessionEntity session = sessionOpt.get();
            session.setCurrentQuestionIndex(index);
            session.setStatus(InterviewSessionEntity.SessionStatus.IN_PROGRESS);
            sessionRepository.save(session);
        }
    }
    
    /**
     * 保存面试答案
     */
    @Transactional
    public InterviewAnswerEntity saveAnswer(String sessionId, int questionIndex,
                                            String question, String category,
                                            String userAnswer, int score, String feedback) {
        Optional<InterviewSessionEntity> sessionOpt = sessionRepository.findBySessionId(sessionId);
        if (sessionOpt.isEmpty()) {
            throw new RuntimeException("会话不存在: " + sessionId);
        }
        
        InterviewAnswerEntity answer = new InterviewAnswerEntity();
        answer.setSession(sessionOpt.get());
        answer.setQuestionIndex(questionIndex);
        answer.setQuestion(question);
        answer.setCategory(category);
        answer.setUserAnswer(userAnswer);
        answer.setScore(score);
        answer.setFeedback(feedback);
        
        InterviewAnswerEntity saved = answerRepository.save(answer);
        log.info("面试答案已保存: sessionId={}, questionIndex={}, score={}", 
                sessionId, questionIndex, score);
        
        return saved;
    }
    
    /**
     * 保存面试报告
     */
    @Transactional
    public void saveReport(String sessionId, InterviewReportDTO report) {
        try {
            Optional<InterviewSessionEntity> sessionOpt = sessionRepository.findBySessionId(sessionId);
            if (sessionOpt.isEmpty()) {
                return;
            }
            
            InterviewSessionEntity session = sessionOpt.get();
            session.setOverallScore(report.overallScore());
            session.setOverallFeedback(report.overallFeedback());
            session.setStrengthsJson(objectMapper.writeValueAsString(report.strengths()));
            session.setImprovementsJson(objectMapper.writeValueAsString(report.improvements()));
            session.setReferenceAnswersJson(objectMapper.writeValueAsString(report.referenceAnswers()));
            session.setStatus(InterviewSessionEntity.SessionStatus.EVALUATED);
            session.setCompletedAt(LocalDateTime.now());
            
            sessionRepository.save(session);
            log.info("面试报告已保存: sessionId={}, score={}", sessionId, report.overallScore());
            
        } catch (JsonProcessingException e) {
            log.error("序列化报告失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 根据会话ID获取会话
     */
    public Optional<InterviewSessionEntity> findBySessionId(String sessionId) {
        return sessionRepository.findBySessionId(sessionId);
    }
    
    /**
     * 获取简历的所有面试记录
     */
    public List<InterviewSessionEntity> findByResumeId(Long resumeId) {
        return sessionRepository.findByResumeIdOrderByCreatedAtDesc(resumeId);
    }
}
