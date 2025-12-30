package interview.guide.modules.interview.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.model.*;
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
    private final ObjectMapper objectMapper;
    
    // 内存存储会话（生产环境应使用Redis）
    private final Map<String, InterviewSession> sessions = new ConcurrentHashMap<>();
    
    public InterviewSessionService(InterviewQuestionService questionService, 
                                   AnswerEvaluationService evaluationService,
                                   InterviewPersistenceService persistenceService,
                                   ObjectMapper objectMapper) {
        this.questionService = questionService;
        this.evaluationService = evaluationService;
        this.persistenceService = persistenceService;
        this.objectMapper = objectMapper;
    }
    
    /**
     * 创建新的面试会话
     * 注意：如果已有未完成的会话，不会创建新的，而是返回现有会话
     * 前端应该先调用 findUnfinishedSession 检查，或者使用 forceCreate 参数强制创建
     */
    public InterviewSessionDTO createSession(CreateInterviewRequest request) {
        // 如果指定了resumeId且未强制创建，检查是否有未完成的会话
        if (request.resumeId() != null && !Boolean.TRUE.equals(request.forceCreate())) {
            Optional<InterviewSessionDTO> unfinishedOpt = findUnfinishedSession(request.resumeId());
            if (unfinishedOpt.isPresent()) {
                log.info("检测到未完成的面试会话，返回现有会话: resumeId={}, sessionId={}", 
                    request.resumeId(), unfinishedOpt.get().sessionId());
                return unfinishedOpt.get();
            }
        }
        
        String sessionId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        
        log.info("创建新面试会话: {}, 题目数量: {}, resumeId: {}", 
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
     * 获取会话信息（如果内存中没有，尝试从数据库恢复）
     */
    public InterviewSessionDTO getSession(String sessionId) {
        InterviewSession session = sessions.get(sessionId);
        if (session == null) {
            // 尝试从数据库恢复
            session = restoreSessionFromDatabase(sessionId);
            if (session == null) {
                throw new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND);
            }
        }
        return toDTO(session);
    }
    
    /**
     * 查找并恢复未完成的面试会话
     */
    public Optional<InterviewSessionDTO> findUnfinishedSession(Long resumeId) {
        try {
            Optional<InterviewSessionEntity> entityOpt = persistenceService.findUnfinishedSession(resumeId);
            if (entityOpt.isEmpty()) {
                return Optional.empty();
            }
            
            InterviewSessionEntity entity = entityOpt.get();
            // 如果内存中已有，直接返回
            InterviewSession session = sessions.get(entity.getSessionId());
            if (session != null) {
                return Optional.of(toDTO(session));
            }
            
            // 从数据库恢复
            InterviewSession restoredSession = restoreSessionFromEntity(entity);
            if (restoredSession != null) {
                sessions.put(entity.getSessionId(), restoredSession);
                return Optional.of(toDTO(restoredSession));
            }
        } catch (Exception e) {
            log.error("恢复未完成会话失败: {}", e.getMessage(), e);
        }
        return Optional.empty();
    }
    
    /**
     * 查找并恢复未完成的面试会话，如果不存在则抛出异常
     */
    public InterviewSessionDTO findUnfinishedSessionOrThrow(Long resumeId) {
        return findUnfinishedSession(resumeId)
            .orElseThrow(() -> new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND, "未找到未完成的面试会话"));
    }
    
    /**
     * 从数据库恢复会话
     */
    private InterviewSession restoreSessionFromDatabase(String sessionId) {
        try {
            Optional<InterviewSessionEntity> entityOpt = persistenceService.findBySessionId(sessionId);
            return entityOpt.map(this::restoreSessionFromEntity).orElse(null);
        } catch (Exception e) {
            log.error("从数据库恢复会话失败: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 从实体恢复会话
     */
    private InterviewSession restoreSessionFromEntity(InterviewSessionEntity entity) {
        try {
            // 解析问题列表
            List<InterviewQuestionDTO> questions = objectMapper.readValue(
                entity.getQuestionsJson(),
                    new TypeReference<>() {
                    }
            );
            
            // 恢复已保存的答案
            List<InterviewAnswerEntity> answers = persistenceService.findAnswersBySessionId(entity.getSessionId());
            for (InterviewAnswerEntity answer : answers) {
                int index = answer.getQuestionIndex();
                if (index >= 0 && index < questions.size()) {
                    InterviewQuestionDTO question = questions.get(index);
                    questions.set(index, question.withAnswer(answer.getUserAnswer()));
                }
            }
            
            InterviewSession session = new InterviewSession(
                entity.getSessionId(),
                entity.getResume().getResumeText(),
                entity.getResume().getId(),
                questions,
                entity.getCurrentQuestionIndex(),
                convertStatus(entity.getStatus())
            );
            
            log.info("从数据库恢复会话: sessionId={}, currentIndex={}, status={}", 
                entity.getSessionId(), entity.getCurrentQuestionIndex(), entity.getStatus());
            
            return session;
        } catch (Exception e) {
            log.error("恢复会话失败: {}", e.getMessage(), e);
            return null;
        }
    }
    
    private SessionStatus convertStatus(InterviewSessionEntity.SessionStatus status) {
        return switch (status) {
            case CREATED -> SessionStatus.CREATED;
            case IN_PROGRESS -> SessionStatus.IN_PROGRESS;
            case COMPLETED -> SessionStatus.COMPLETED;
            case EVALUATED -> SessionStatus.EVALUATED;
        };
    }
    
    /**
     * 获取当前问题的响应（包含完成状态）
     */
    public Map<String, Object> getCurrentQuestionResponse(String sessionId) {
        InterviewQuestionDTO question = getCurrentQuestion(sessionId);
        if (question == null) {
            return Map.of(
                "completed", true,
                "message", "所有问题已回答完毕"
            );
        }
        return Map.of(
            "completed", false,
            "question", question
        );
    }
    
    /**
     * 获取当前问题（支持从数据库恢复）
     */
    public InterviewQuestionDTO getCurrentQuestion(String sessionId) {
        InterviewSession session = getOrRestoreSession(sessionId);
        
        if (session.currentIndex >= session.questions.size()) {
            return null; // 所有问题已回答完
        }
        
        // 更新状态为进行中
        if (session.status == SessionStatus.CREATED) {
            session.status = SessionStatus.IN_PROGRESS;
            // 同步到数据库
            if (session.resumeId != null) {
                try {
                    persistenceService.updateSessionStatus(sessionId, 
                        InterviewSessionEntity.SessionStatus.IN_PROGRESS);
                } catch (Exception e) {
                    log.warn("更新会话状态失败: {}", e.getMessage());
                }
            }
        }
        
        return session.questions.get(session.currentIndex);
    }
    
    /**
     * 提交答案（并进入下一题）
     */
    public SubmitAnswerResponse submitAnswer(SubmitAnswerRequest request) {
        InterviewSession session = getOrRestoreSession(request.sessionId());
        
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
                persistenceService.updateSessionStatus(request.sessionId(), 
                    session.status == SessionStatus.COMPLETED 
                        ? InterviewSessionEntity.SessionStatus.COMPLETED
                        : InterviewSessionEntity.SessionStatus.IN_PROGRESS);
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
     * 暂存答案（不进入下一题）
     */
    public void saveAnswer(SubmitAnswerRequest request) {
        InterviewSession session = getOrRestoreSession(request.sessionId());
        
        int index = request.questionIndex();
        if (index < 0 || index >= session.questions.size()) {
            throw new BusinessException(ErrorCode.INTERVIEW_QUESTION_NOT_FOUND, "无效的问题索引: " + index);
        }
        
        // 更新问题答案
        InterviewQuestionDTO question = session.questions.get(index);
        InterviewQuestionDTO answeredQuestion = question.withAnswer(request.answer());
        session.questions.set(index, answeredQuestion);
        
        // 更新状态为进行中
        if (session.status == SessionStatus.CREATED) {
            session.status = SessionStatus.IN_PROGRESS;
        }
        
        // 保存答案到数据库（不更新currentIndex）
        if (session.resumeId != null) {
            try {
                persistenceService.saveAnswer(
                    request.sessionId(), index, 
                    question.question(), question.category(),
                    request.answer(), 0, null
                );
                persistenceService.updateSessionStatus(request.sessionId(), 
                    InterviewSessionEntity.SessionStatus.IN_PROGRESS);
            } catch (Exception e) {
                log.warn("暂存答案到数据库失败: {}", e.getMessage());
            }
        }
        
        log.info("会话 {} 暂存答案: 问题{}", request.sessionId(), index);
    }
    
    /**
     * 提前交卷
     */
    public void completeInterview(String sessionId) {
        InterviewSession session = getOrRestoreSession(sessionId);
        
        if (session.status == SessionStatus.COMPLETED || session.status == SessionStatus.EVALUATED) {
            throw new BusinessException(ErrorCode.INTERVIEW_ALREADY_COMPLETED);
        }
        
        session.status = SessionStatus.COMPLETED;
        
        // 更新数据库状态
        if (session.resumeId != null) {
            try {
                persistenceService.updateSessionStatus(sessionId, 
                    InterviewSessionEntity.SessionStatus.COMPLETED);
            } catch (Exception e) {
                log.warn("更新会话状态失败: {}", e.getMessage());
            }
        }
        
        log.info("会话 {} 提前交卷", sessionId);
    }
    
    /**
     * 获取或恢复会话
     */
    private InterviewSession getOrRestoreSession(String sessionId) {
        InterviewSession session = sessions.get(sessionId);
        if (session == null) {
            session = restoreSessionFromDatabase(sessionId);
            if (session == null) {
                throw new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND);
            }
            sessions.put(sessionId, session);
        }
        return session;
    }
    
    /**
     * 生成评估报告（支持从数据库恢复）
     */
    public InterviewReportDTO generateReport(String sessionId) {
        InterviewSession session = getOrRestoreSession(sessionId);
        
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
