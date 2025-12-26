package interview.guide.service;

import interview.guide.dto.*;
import interview.guide.dto.InterviewSessionDTO.SessionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 面试会话管理服务
 * 管理面试会话的生命周期
 */
@Service
public class InterviewSessionService {
    
    private static final Logger log = LoggerFactory.getLogger(InterviewSessionService.class);
    
    private final InterviewQuestionService questionService;
    private final AnswerEvaluationService evaluationService;
    
    // 内存存储会话（生产环境应使用Redis）
    private final Map<String, InterviewSession> sessions = new ConcurrentHashMap<>();
    
    public InterviewSessionService(InterviewQuestionService questionService, 
                                   AnswerEvaluationService evaluationService) {
        this.questionService = questionService;
        this.evaluationService = evaluationService;
    }
    
    /**
     * 创建新的面试会话
     */
    public InterviewSessionDTO createSession(CreateInterviewRequest request) {
        String sessionId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        
        log.info("创建面试会话: {}, 题目数量: {}", sessionId, request.questionCount());
        
        // 生成面试问题
        List<InterviewQuestionDTO> questions = questionService.generateQuestions(
            request.resumeText(), 
            request.questionCount()
        );
        
        InterviewSession session = new InterviewSession(
            sessionId,
            request.resumeText(),
            questions,
            0,
            SessionStatus.CREATED
        );
        
        sessions.put(sessionId, session);
        
        return toDTO(session);
    }
    
    /**
     * 获取会话信息
     */
    public InterviewSessionDTO getSession(String sessionId) {
        InterviewSession session = sessions.get(sessionId);
        if (session == null) {
            throw new RuntimeException("会话不存在: " + sessionId);
        }
        return toDTO(session);
    }
    
    /**
     * 获取当前问题
     */
    public InterviewQuestionDTO getCurrentQuestion(String sessionId) {
        InterviewSession session = sessions.get(sessionId);
        if (session == null) {
            throw new RuntimeException("会话不存在: " + sessionId);
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
            throw new RuntimeException("会话不存在: " + request.sessionId());
        }
        
        int index = request.questionIndex();
        if (index < 0 || index >= session.questions.size()) {
            throw new RuntimeException("无效的问题索引: " + index);
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
            throw new RuntimeException("会话不存在: " + sessionId);
        }
        
        if (session.status != SessionStatus.COMPLETED && session.status != SessionStatus.EVALUATED) {
            throw new RuntimeException("面试尚未完成，无法生成报告");
        }
        
        log.info("生成面试报告: {}", sessionId);
        
        InterviewReportDTO report = evaluationService.evaluateInterview(
            sessionId,
            session.resumeText,
            session.questions
        );
        
        session.status = SessionStatus.EVALUATED;
        
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
        final List<InterviewQuestionDTO> questions;
        int currentIndex;
        SessionStatus status;
        
        InterviewSession(String sessionId, String resumeText, 
                        List<InterviewQuestionDTO> questions,
                        int currentIndex, SessionStatus status) {
            this.sessionId = sessionId;
            this.resumeText = resumeText;
            this.questions = new ArrayList<>(questions);
            this.currentIndex = currentIndex;
            this.status = status;
        }
    }
    
    /**
     * 提交答案响应
     */
    public record SubmitAnswerResponse(
        boolean hasNextQuestion,
        InterviewQuestionDTO nextQuestion,
        int currentIndex,
        int totalQuestions
    ) {}
}
