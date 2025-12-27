package interview.guide.modules.interview.service;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.model.CreateInterviewRequest;
import interview.guide.modules.interview.model.SubmitAnswerRequest;
import interview.guide.modules.interview.model.InterviewQuestionDTO;
import interview.guide.modules.interview.model.InterviewReportDTO;
import interview.guide.modules.interview.model.InterviewSessionDTO;
import interview.guide.modules.interview.model.SubmitAnswerResponse;
import interview.guide.modules.interview.model.InterviewSessionDTO.SessionStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 面试会话管理服务
 * 管理面试会话的生命周期
 */
@Slf4j
@Service
public class InterviewSessionService {
    
    private final InterviewQuestionService questionService;
    private final AnswerEvaluationService evaluationService;
    private final InterviewPersistenceService persistenceService;
    
    // 内存存储会话（生产环境应使用Redis）
    private final Map<String, InterviewSession> sessions = new ConcurrentHashMap<>();
    
    public InterviewSessionService(InterviewQuestionService questionService, 
                                   AnswerEvaluationService evaluationService,
                                   InterviewPersistenceService persistenceService) {
        this.questionService = questionService;
        this.evaluationService = evaluationService;
        this.persistenceService = persistenceService;
    }
    
    /**
     * 创建新的面试会话
     */
    public InterviewSessionDTO createSession(CreateInterviewRequest request) {
        String sessionId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        
        log.info("创建面试会话: {}, 题目数量: {}, resumeId: {}", 
                sessionId, request.questionCount(), request.resumeId());
        
        // 生成面试问题
        List<InterviewQuestionDTO> questions = questionService.generateQuestions(
            request.resumeText(), 
            request.questionCount()
        );
        
        InterviewSession session = new InterviewSession(
            sessionId,
            request.resumeText(),
            request.resumeId(),
            questions,
            0,
            SessionStatus.CREATED
        );
        
        sessions.put(sessionId, session);
        
        // 保存到数据库
        if (request.resumeId() != null) {
            try {
                persistenceService.saveSession(sessionId, request.resumeId(), 
                        request.questionCount(), questions);
            } catch (Exception e) {
                log.warn("保存面试会话到数据库失败: {}", e.getMessage());
            }
        }
        
        return toDTO(session);
    }
    
    /**
     * 获取会话信息
     */
    public InterviewSessionDTO getSession(String sessionId) {
        InterviewSession session = sessions.get(sessionId);
        if (session == null) {
            throw new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND);
        }
        return toDTO(session);
    }
    
    /**
     * 获取当前问题
     */
    public InterviewQuestionDTO getCurrentQuestion(String sessionId) {
        InterviewSession session = sessions.get(sessionId);
        if (session == null) {
            throw new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND);
        }
        
        if (session.currentIndex >= session.questions.size()) {
            return null; // 所有问题已回答完
        }
        
        // 更新状态为进行中
        if (session.status == SessionStatus.CREATED) {
            session.status = SessionStatus.IN_PROGRESS;
        }
        
        return session.questions.get(session.currentIndex);
    }
    
    /**
     * 提交答案
     */
    public SubmitAnswerResponse submitAnswer(SubmitAnswerRequest request) {
        InterviewSession session = sessions.get(request.sessionId());
        if (session == null) {
            throw new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND);
        }
        
        int index = request.questionIndex();
        if (index < 0 || index >= session.questions.size()) {
            throw new BusinessException(ErrorCode.INTERVIEW_QUESTION_NOT_FOUND, "无效的问题索引: " + index);
        }
        
        // 更新问题答案
        InterviewQuestionDTO question = session.questions.get(index);
        InterviewQuestionDTO answeredQuestion = question.withAnswer(request.answer());
        session.questions.set(index, answeredQuestion);
        
        // 移动到下一题
        session.currentIndex = index + 1;
        
        // 检查是否全部完成
        boolean hasNextQuestion = session.currentIndex < session.questions.size();
        InterviewQuestionDTO nextQuestion = hasNextQuestion 
            ? session.questions.get(session.currentIndex) 
            : null;
        
        if (!hasNextQuestion) {
            session.status = SessionStatus.COMPLETED;
        }
        
        // 保存答案到数据库
        if (session.resumeId != null) {
            try {
                persistenceService.saveAnswer(
                    request.sessionId(), index, 
                    question.question(), question.category(),
                    request.answer(), 0, null  // 分数在报告生成时更新
                );
                persistenceService.updateCurrentQuestionIndex(request.sessionId(), session.currentIndex);
            } catch (Exception e) {
                log.warn("保存答案到数据库失败: {}", e.getMessage());
            }
        }
        
        log.info("会话 {} 提交答案: 问题{}, 剩余{}题", 
            request.sessionId(), index, session.questions.size() - session.currentIndex);
        
        return new SubmitAnswerResponse(
            hasNextQuestion,
            nextQuestion,
            session.currentIndex,
            session.questions.size()
        );
    }
    
    /**
     * 生成评估报告
     */
    public InterviewReportDTO generateReport(String sessionId) {
        InterviewSession session = sessions.get(sessionId);
        if (session == null) {
            throw new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND);
        }
        
        if (session.status != SessionStatus.COMPLETED && session.status != SessionStatus.EVALUATED) {
            throw new BusinessException(ErrorCode.INTERVIEW_NOT_COMPLETED, "面试尚未完成，无法生成报告");
        }
        
        log.info("生成面试报告: {}", sessionId);
        
        InterviewReportDTO report = evaluationService.evaluateInterview(
            sessionId,
            session.resumeText,
            session.questions
        );
        
        session.status = SessionStatus.EVALUATED;
        
        // 保存报告到数据库
        if (session.resumeId != null) {
            try {
                persistenceService.saveReport(sessionId, report);
            } catch (Exception e) {
                log.warn("保存报告到数据库失败: {}", e.getMessage());
            }
        }
        
        return report;
    }
    
    private InterviewSessionDTO toDTO(InterviewSession session) {
        return new InterviewSessionDTO(
            session.sessionId,
            session.resumeText,
            session.questions.size(),
            session.currentIndex,
            session.questions,
            session.status
        );
    }
    
    /**
     * 内部会话实体
     */
    private static class InterviewSession {
        final String sessionId;
        final String resumeText;
        final Long resumeId;
        final List<InterviewQuestionDTO> questions;
        int currentIndex;
        SessionStatus status;
        
        InterviewSession(String sessionId, String resumeText, Long resumeId,
                        List<InterviewQuestionDTO> questions,
                        int currentIndex, SessionStatus status) {
            this.sessionId = sessionId;
            this.resumeText = resumeText;
            this.resumeId = resumeId;
            this.questions = new ArrayList<>(questions);
            this.currentIndex = currentIndex;
            this.status = status;
        }
    }
}
