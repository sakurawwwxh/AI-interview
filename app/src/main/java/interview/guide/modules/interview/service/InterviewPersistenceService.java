package interview.guide.modules.interview.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.model.InterviewAnswerEntity;
import interview.guide.modules.interview.model.InterviewQuestionDTO;
import interview.guide.modules.interview.model.InterviewReportDTO;
import interview.guide.modules.interview.model.InterviewSessionEntity;
import interview.guide.modules.interview.repository.InterviewAnswerRepository;
import interview.guide.modules.interview.repository.InterviewSessionRepository;
import interview.guide.modules.resume.model.ResumeEntity;
import interview.guide.modules.resume.repository.ResumeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 面试持久化服务
 * Interview Persistence Service
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewPersistenceService {
    
    private final InterviewSessionRepository sessionRepository;
    private final InterviewAnswerRepository answerRepository;
    private final ResumeRepository resumeRepository;
    private final ObjectMapper objectMapper;
    
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
                throw new BusinessException(ErrorCode.RESUME_NOT_FOUND);
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
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "保存会话失败");
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
            throw new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND);
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
                log.warn("会话不存在: {}", sessionId);
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
            
            // 直接从数据库查询答案并更新
            List<InterviewAnswerEntity> answers = answerRepository.findBySessionSessionIdOrderByQuestionIndex(sessionId);
            log.info("查询到 {} 个答案需要更新", answers.size());
            
            if (!answers.isEmpty()) {
                // 更新每个答案的得分和评价
                for (InterviewReportDTO.QuestionEvaluation eval : report.questionDetails()) {
                    for (InterviewAnswerEntity answer : answers) {
                        if (answer.getQuestionIndex() != null && 
                            answer.getQuestionIndex() == eval.questionIndex()) {
                            answer.setScore(eval.score());
                            answer.setFeedback(eval.feedback());
                            log.debug("更新答案 {} 的评价: score={}", eval.questionIndex(), eval.score());
                            break;
                        }
                    }
                }
                
                // 设置参考答案和关键点
                for (InterviewReportDTO.ReferenceAnswer refAns : report.referenceAnswers()) {
                    for (InterviewAnswerEntity answer : answers) {
                        if (answer.getQuestionIndex() != null && 
                            answer.getQuestionIndex() == refAns.questionIndex()) {
                            answer.setReferenceAnswer(refAns.referenceAnswer());
                            if (refAns.keyPoints() != null && !refAns.keyPoints().isEmpty()) {
                                answer.setKeyPointsJson(objectMapper.writeValueAsString(refAns.keyPoints()));
                            }
                            log.debug("更新答案 {} 的参考答案", refAns.questionIndex());
                            break;
                        }
                    }
                }
                
                answerRepository.saveAll(answers);
                log.info("已更新 {} 个答案的评价和参考答案", answers.size());
            }
            
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
