package ai.interview.modules.interview.service;

import ai.interview.common.exception.BusinessException;
import ai.interview.common.exception.ErrorCode;
import ai.interview.common.model.AsyncTaskStatus;
import ai.interview.infrastructure.redis.InterviewSessionCache;
import ai.interview.infrastructure.redis.InterviewSessionCache.CachedSession;
import ai.interview.infrastructure.redis.RedisService;
import ai.interview.modules.interview.listener.EvaluateStreamProducer;
import ai.interview.modules.interview.model.*;
import ai.interview.modules.interview.model.InterviewSessionDTO.SessionStatus;
import ai.interview.modules.user.security.UserContext;
import ai.interview.modules.target.service.JobTargetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 面试会话管理服务
 * 管理面试会话的生命周期，使用 Redis 缓存会话状态
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewSessionService {

    private final InterviewQuestionService questionService;
    private final AnswerEvaluationService evaluationService;
    private final InterviewPersistenceService persistenceService;
    private final InterviewSessionCache sessionCache;
    private final ObjectMapper objectMapper;
    private final EvaluateStreamProducer evaluateStreamProducer;
    private final RedisService redisService;
    private final JobTargetService jobTargetService;

    /**
     * 创建新的面试会话
     * 注意：如果已有未完成的会话，不会创建新的，而是返回现有会话
     * 前端应该先调用 findUnfinishedSession 检查，或者使用 forceCreate 参数强制创建
     */
    public InterviewSessionDTO createSession(CreateInterviewRequest request) {
        Long userId = UserContext.getCurrentUserIdOrThrow();
        // 无论是否强制创建，都必须先校验 resumeId 归属；forceCreate 只影响是否复用会话。
        if (request.resumeId() != null) {
            persistenceService.requireOwnedResume(request.resumeId());
        }

        // 如果指定了 resumeId 且未强制创建，检查是否有未完成的会话
        if (request.resumeId() != null && !Boolean.TRUE.equals(request.forceCreate())) {
            Optional<InterviewSessionDTO> unfinishedOpt = findUnfinishedSession(request.resumeId());
            if (unfinishedOpt.isPresent()) {
                log.info("检测到未完成的面试会话，返回现有会话: resumeId={}, sessionId={}",
                    request.resumeId(), unfinishedOpt.get().sessionId());
                return unfinishedOpt.get();
            }
        }

        String sessionId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        InterviewTemplateConfig template = (request.template() == null
            ? InterviewTemplateConfig.defaultBackend(questionService.getDefaultFollowUpCount())
            : request.template()).normalize(questionService.getDefaultFollowUpCount());

        log.info("创建新面试会话: {}, 题目数量: {}, resumeId: {}",
            sessionId, request.questionCount(), request.resumeId());

        // 获取历史问题
        List<String> historicalQuestions = null;
        if (request.resumeId() != null) {
            historicalQuestions = persistenceService.getHistoricalQuestionsByResumeId(request.resumeId());
        }

        // 生成面试问题
        String targetJob = null;
        if (request.jobTargetId() != null) {
            var target = jobTargetService.getOwned(request.jobTargetId());
            targetJob = "岗位：" + target.getTitle() + "\nJD：\n" + target.getJobDescription();
        }
        QuestionGenerationResult generation = questionService.generateQuestionsWithSource(
            request.resumeText(),
            request.questionCount(),
            historicalQuestions,
            template,
            targetJob
        );
        List<InterviewQuestionDTO> questions = generation.questions();
        String questionsSource = generation.source();
        if (InterviewSessionDTO.SOURCE_DEFAULT.equals(questionsSource)) {
            log.warn("会话 {} 使用默认题库出题", sessionId);
        }

        // 先持久化；数据库失败时不写 Redis，避免向客户端返回无法恢复的“成功会话”。
        if (request.resumeId() != null) {
            persistenceService.saveSession(sessionId, request.resumeId(),
                questions.size(), questions, template);
        }

        // 数据库成功后再保存到 Redis 缓存。
        sessionCache.saveSession(
            userId,
            sessionId,
            request.resumeText(),
            request.resumeId(),
            questions,
            0,
            SessionStatus.CREATED
        );

        return new InterviewSessionDTO(
            sessionId,
            request.resumeText(),
            questions.size(),
            0,
            questions,
            SessionStatus.CREATED,
            questionsSource
        );
    }

    /**
     * 获取会话信息（优先从缓存获取，缓存未命中则从数据库恢复）
     */
    public InterviewSessionDTO getSession(String sessionId) {
        Long userId = UserContext.getCurrentUserIdOrThrow();
        // 1. 尝试从 Redis 缓存获取
        Optional<CachedSession> cachedOpt = sessionCache.getSession(userId, sessionId);
        if (cachedOpt.isPresent() && belongsToCurrentUser(cachedOpt.get())) {
            return toDTO(cachedOpt.get());
        }

        // 2. 缓存未命中，从数据库恢复
        CachedSession restoredSession = restoreSessionFromDatabase(sessionId);
        if (restoredSession == null) {
            throw new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND);
        }

        return toDTO(restoredSession);
    }

    /**
     * 查找并恢复未完成的面试会话
     */
    public Optional<InterviewSessionDTO> findUnfinishedSession(Long resumeId) {
        try {
            Long userId = UserContext.getCurrentUserIdOrThrow();
            // 1. 先从 Redis 缓存查找
            Optional<String> cachedSessionIdOpt = sessionCache.findUnfinishedSessionId(userId, resumeId);
            if (cachedSessionIdOpt.isPresent()) {
                String sessionId = cachedSessionIdOpt.get();
                Optional<CachedSession> cachedOpt = sessionCache.getSession(userId, sessionId);
                if (cachedOpt.isPresent() && belongsToCurrentUser(cachedOpt.get())) {
                    log.debug("从 Redis 缓存找到未完成会话: resumeId={}, sessionId={}", resumeId, sessionId);
                    return Optional.of(toDTO(cachedOpt.get()));
                }
            }

            // 2. 缓存未命中，从数据库查找
            Optional<InterviewSessionEntity> entityOpt = persistenceService.findUnfinishedSession(resumeId);
            if (entityOpt.isEmpty()) {
                return Optional.empty();
            }

            InterviewSessionEntity entity = entityOpt.get();
            CachedSession restoredSession = restoreSessionFromEntity(entity);
            if (restoredSession != null) {
                return Optional.of(toDTO(restoredSession));
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("恢复未完成会话失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "恢复未完成面试会话失败");
        }
        return Optional.empty();
    }

    /**
     * 从数据库恢复会话并缓存到 Redis
     */
    private CachedSession restoreSessionFromDatabase(String sessionId) {
        try {
            // 缓存未命中走 DB 恢复时鉴权：仅返回属于当前登录用户的会话
            Optional<InterviewSessionEntity> entityOpt = persistenceService.findBySessionIdForCurrentUser(sessionId);
            return entityOpt.map(this::restoreSessionFromEntity).orElse(null);
        } catch (Exception e) {
            log.error("从数据库恢复会话失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 从实体恢复会话并缓存到 Redis
     */
    private CachedSession restoreSessionFromEntity(InterviewSessionEntity entity) {
        try {
            // 解析问题列表
            List<InterviewQuestionDTO> questions = objectMapper.readValue(
                entity.getQuestionsJson(),
                new TypeReference<>() {}
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

            SessionStatus status = convertStatus(entity.getStatus());

            // 保存到 Redis 缓存
            sessionCache.saveSession(
                entity.getUserId(),
                entity.getSessionId(),
                entity.getResume().getResumeText(),
                entity.getResume().getId(),
                questions,
                entity.getCurrentQuestionIndex(),
                status
            );

            log.info("从数据库恢复会话到 Redis: sessionId={}, currentIndex={}, status={}",
                entity.getSessionId(), entity.getCurrentQuestionIndex(), entity.getStatus());

            // 返回缓存的会话
            return sessionCache.getSession(entity.getUserId(), entity.getSessionId()).orElse(null);
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
     * 获取当前问题
     */
    public InterviewQuestionDTO getCurrentQuestion(String sessionId) {
        Long userId = UserContext.getCurrentUserIdOrThrow();
        CachedSession session = getOrRestoreSession(sessionId);
        List<InterviewQuestionDTO> questions = session.getQuestions(objectMapper);

        if (session.getCurrentIndex() < 0 || session.getCurrentIndex() >= questions.size()) {
            return null; // 所有问题已回答完
        }

        // 更新状态为进行中
        if (session.getStatus() == SessionStatus.CREATED) {
            // 先更新数据库；失败则不改变缓存状态，也不向客户端伪造成功。
            persistenceService.updateSessionStatus(sessionId,
                InterviewSessionEntity.SessionStatus.IN_PROGRESS);
            sessionCache.updateSessionStatus(userId, sessionId, SessionStatus.IN_PROGRESS);
        }

        return questions.get(session.getCurrentIndex());
    }

    /**
     * 提交答案（并进入下一题）
     * 如果是最后一题，自动触发异步评估
     */
    public SubmitAnswerResponse submitAnswer(SubmitAnswerRequest request) {
        // 分布式锁防止同一会话并发提交导致 Redis 状态覆盖
        String lockKey = "interview:session:lock:" + request.sessionId();
        try {
            // leaseTime=-1 使用 Redisson 看门狗续期，避免 AI 追问超过固定租期后并发提交。
            return redisService.executeWithLock(lockKey, 3, -1, java.util.concurrent.TimeUnit.SECONDS,
                () -> doSubmitAnswer(request));
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("获取锁失败")) {
                throw new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND,
                    "操作过于频繁，请稍后重试");
            }
            throw e;
        }
    }

    private SubmitAnswerResponse doSubmitAnswer(SubmitAnswerRequest request) {
        Long userId = UserContext.getCurrentUserIdOrThrow();
        CachedSession session = getOrRestoreSession(request.sessionId());
        List<InterviewQuestionDTO> questions = session.getQuestions(objectMapper);

        int index = request.questionIndex();
        if (index < 0 || index >= questions.size()) {
            throw new BusinessException(ErrorCode.INTERVIEW_QUESTION_NOT_FOUND, "无效的问题索引: " + index);
        }

        // 更新问题答案
        InterviewQuestionDTO question = questions.get(index);
        InterviewQuestionDTO answeredQuestion = question.withAnswer(request.answer());
        questions.set(index, answeredQuestion);

        Optional<InterviewQuestionDTO> dynamicFollowUp = generateDynamicFollowUp(
            request.sessionId(), questions, question, request.answer()
        );
        dynamicFollowUp.ifPresent(questions::add);

        int newIndex = dynamicFollowUp
            .map(InterviewQuestionDTO::questionIndex)
            .orElseGet(() -> findNextUnansweredQuestionIndex(questions));
        boolean hasNextQuestion = newIndex >= 0;
        InterviewQuestionDTO nextQuestion = hasNextQuestion ? questions.get(newIndex) : null;

        SessionStatus newStatus = hasNextQuestion ? SessionStatus.IN_PROGRESS : SessionStatus.COMPLETED;

        // 先持久化关键状态；任一步失败都向客户端返回失败，缓存保持可从数据库恢复的旧状态。
        persistenceService.persistSubmittedAnswer(
            request.sessionId(), index,
            question.question(), question.category(),
            request.answer(), request.answerDurationSeconds(),
            questions, newIndex, !hasNextQuestion
        );

        // 所有持久化成功后再更新 Redis 缓存。
        sessionCache.updateQuestions(userId, request.sessionId(), questions);
        sessionCache.updateCurrentIndex(userId, request.sessionId(), newIndex);
        if (newStatus == SessionStatus.COMPLETED) {
            sessionCache.updateSessionStatus(userId, request.sessionId(), SessionStatus.COMPLETED);
        }

        if (!hasNextQuestion) {
            evaluateStreamProducer.sendEvaluateTask(userId, request.sessionId());
            log.info("会话 {} 已完成所有问题，评估任务已入队", request.sessionId());
        }

        log.info("会话 {} 提交答案: 问题{}, 剩余{}题",
            request.sessionId(), index, questions.size() - newIndex);

        return new SubmitAnswerResponse(
            hasNextQuestion,
            nextQuestion,
            newIndex,
            questions.size()
        );
    }

    private Optional<InterviewQuestionDTO> generateDynamicFollowUp(
        String sessionId,
        List<InterviewQuestionDTO> questions,
        InterviewQuestionDTO question,
        String answer
    ) {
        InterviewTemplateConfig template = loadTemplate(sessionId);
        int parentQuestionIndex = question.isFollowUp() && question.parentQuestionIndex() != null
            ? question.parentQuestionIndex()
            : question.questionIndex();
        int existingFollowUpCount = (int) questions.stream()
            .filter(InterviewQuestionDTO::isFollowUp)
            .filter(item -> Integer.valueOf(parentQuestionIndex).equals(item.parentQuestionIndex()))
            .count();
        return questionService.generateDynamicFollowUp(
            question,
            answer,
            questions.size(),
            parentQuestionIndex,
            existingFollowUpCount,
            template.followUpCount()
        );
    }

    private InterviewTemplateConfig loadTemplate(String sessionId) {
        try {
            Optional<InterviewSessionEntity> session = persistenceService.findBySessionId(sessionId);
            if (session.isPresent() && session.get().getTemplateJson() != null) {
                return objectMapper.readValue(session.get().getTemplateJson(), InterviewTemplateConfig.class)
                    .normalize(questionService.getDefaultFollowUpCount());
            }
        } catch (Exception e) {
            log.warn("读取面试模板快照失败，将使用默认模板: {}", e.getMessage());
        }
        return InterviewTemplateConfig.defaultBackend(questionService.getDefaultFollowUpCount());
    }

    private int findNextUnansweredQuestionIndex(List<InterviewQuestionDTO> questions) {
        for (int i = 0; i < questions.size(); i++) {
            InterviewQuestionDTO question = questions.get(i);
            if (question.userAnswer() == null || question.userAnswer().isBlank()) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 暂存答案（不进入下一题）
     */
    public void saveAnswer(SubmitAnswerRequest request) {
        Long userId = UserContext.getCurrentUserIdOrThrow();
        CachedSession session = getOrRestoreSession(request.sessionId());
        List<InterviewQuestionDTO> questions = session.getQuestions(objectMapper);

        int index = request.questionIndex();
        if (index < 0 || index >= questions.size()) {
            throw new BusinessException(ErrorCode.INTERVIEW_QUESTION_NOT_FOUND, "无效的问题索引: " + index);
        }

        // 更新问题答案
        InterviewQuestionDTO question = questions.get(index);
        InterviewQuestionDTO answeredQuestion = question.withAnswer(request.answer());
        questions.set(index, answeredQuestion);

        // 保存答案到数据库（不更新 currentIndex）；失败时不更新缓存。
        persistenceService.persistDraftAnswer(
            request.sessionId(), index,
            question.question(), question.category(),
            request.answer(), request.answerDurationSeconds()
        );

        sessionCache.updateQuestions(userId, request.sessionId(), questions);
        if (session.getStatus() == SessionStatus.CREATED) {
            sessionCache.updateSessionStatus(userId, request.sessionId(), SessionStatus.IN_PROGRESS);
        }

        log.info("会话 {} 暂存答案: 问题{}", request.sessionId(), index);
    }

    /**
     * 提前交卷（触发异步评估）
     */
    public void completeInterview(String sessionId) {
        Long userId = UserContext.getCurrentUserIdOrThrow();
        CachedSession session = getOrRestoreSession(sessionId);

        if (session.getStatus() == SessionStatus.COMPLETED || session.getStatus() == SessionStatus.EVALUATED) {
            throw new BusinessException(ErrorCode.INTERVIEW_ALREADY_COMPLETED);
        }

        // 先更新数据库状态；失败时不更新缓存、不投递评估任务。
        persistenceService.markCompletedForEvaluation(sessionId);

        sessionCache.updateSessionStatus(userId, sessionId, SessionStatus.COMPLETED);

        // 发送评估任务到 Redis Stream
        evaluateStreamProducer.sendEvaluateTask(userId, sessionId);

        log.info("会话 {} 提前交卷，评估任务已入队", sessionId);
    }

    /**
     * 评估失败后一键重试：重置为 PENDING 并重新入队 Stream。
     * 仅允许 FAILED；进行中/已完成不可重复触发。
     */
    public void retryEvaluation(String sessionId) {
        Long userId = UserContext.getCurrentUserIdOrThrow();
        InterviewSessionEntity entity = persistenceService.findBySessionIdForCurrentUser(sessionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND));

        InterviewSessionEntity.SessionStatus status = entity.getStatus();
        if (status != InterviewSessionEntity.SessionStatus.COMPLETED
            && status != InterviewSessionEntity.SessionStatus.EVALUATED) {
            throw new BusinessException(ErrorCode.INTERVIEW_EVALUATION_NOT_RETRYABLE,
                "仅已交卷的面试可重新评估");
        }

        AsyncTaskStatus evaluateStatus = entity.getEvaluateStatus();
        if (evaluateStatus == AsyncTaskStatus.PROCESSING || evaluateStatus == AsyncTaskStatus.PENDING) {
            throw new BusinessException(ErrorCode.INTERVIEW_EVALUATION_NOT_RETRYABLE,
                "评估正在进行中，请稍后再试");
        }
        // 已成功出分则不可重试
        if (evaluateStatus == AsyncTaskStatus.COMPLETED && entity.getOverallScore() != null) {
            throw new BusinessException(ErrorCode.INTERVIEW_EVALUATION_NOT_RETRYABLE,
                "评估已完成，无需重试");
        }
        // 允许：FAILED；或无 evaluateStatus / 未出分的异常历史数据
        boolean retryable = evaluateStatus == null
            || evaluateStatus == AsyncTaskStatus.FAILED
            || (evaluateStatus == AsyncTaskStatus.COMPLETED && entity.getOverallScore() == null);
        if (!retryable) {
            throw new BusinessException(ErrorCode.INTERVIEW_EVALUATION_NOT_RETRYABLE);
        }

        try {
            // 确保会话状态回到 COMPLETED，便于消费者落库报告
            if (status == InterviewSessionEntity.SessionStatus.EVALUATED
                && entity.getOverallScore() == null) {
                persistenceService.updateSessionStatus(sessionId,
                    InterviewSessionEntity.SessionStatus.COMPLETED);
            }
            persistenceService.updateEvaluateStatus(sessionId, AsyncTaskStatus.PENDING, null);
            sessionCache.updateSessionStatus(userId, sessionId, SessionStatus.COMPLETED);
        } catch (Exception e) {
            log.warn("重置评估状态失败: sessionId={}, error={}", sessionId, e.getMessage());
            throw new BusinessException(ErrorCode.INTERVIEW_EVALUATION_FAILED, "重置评估状态失败，请重试");
        }

        evaluateStreamProducer.sendEvaluateTask(userId, sessionId);
        log.info("会话 {} 评估已重新入队", sessionId);
    }

    /**
     * 获取或恢复会话（优先从缓存获取）
     */
    private CachedSession getOrRestoreSession(String sessionId) {
        Long userId = UserContext.getCurrentUserIdOrThrow();
        // 1. 尝试从 Redis 缓存获取
        Optional<CachedSession> cachedOpt = sessionCache.getSession(userId, sessionId);
        if (cachedOpt.isPresent() && belongsToCurrentUser(cachedOpt.get())) {
            // 刷新 TTL
            sessionCache.refreshSessionTTL(userId, sessionId);
            return cachedOpt.get();
        }

        // 2. 缓存未命中，从数据库恢复
        CachedSession restoredSession = restoreSessionFromDatabase(sessionId);
        if (restoredSession == null) {
            throw new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND);
        }

        return restoredSession;
    }

    private boolean belongsToCurrentUser(CachedSession session) {
        Long userId = UserContext.getCurrentUserIdOrThrow();
        return userId.equals(session.getUserId());
    }

    /**
     * 生成评估报告
     */
    public InterviewReportDTO generateReport(String sessionId) {
        Long userId = UserContext.getCurrentUserIdOrThrow();
        CachedSession session = getOrRestoreSession(sessionId);

        if (session.getStatus() != SessionStatus.COMPLETED && session.getStatus() != SessionStatus.EVALUATED) {
            throw new BusinessException(ErrorCode.INTERVIEW_NOT_COMPLETED, "面试尚未完成，无法生成报告");
        }

        // 异步评估还在跑时不要再起一路同步评估：两路会并发写同一份报告（后写覆盖，
        // 练习题唯一键还可能撞车导致报告事务整体回滚）。让前端继续轮询，等异步结果落库。
        AsyncTaskStatus evaluateStatus = persistenceService.findBySessionIdForCurrentUser(sessionId)
            .map(InterviewSessionEntity::getEvaluateStatus)
            .orElse(null);
        if (evaluateStatus == AsyncTaskStatus.PENDING || evaluateStatus == AsyncTaskStatus.PROCESSING) {
            throw new BusinessException(ErrorCode.INTERVIEW_EVALUATION_IN_PROGRESS, "面试评估正在进行中，请稍候");
        }

        log.info("生成面试报告: {}", sessionId);

        List<InterviewQuestionDTO> questions = session.getQuestions(objectMapper);

        InterviewReportDTO report = evaluationService.evaluateInterview(
            sessionId,
            session.getResumeText(),
            questions
        );

        // 先保存报告，确保缓存中的“已评估”状态可从数据库恢复。
        persistenceService.saveReport(sessionId, report);
        sessionCache.updateSessionStatus(userId, sessionId, SessionStatus.EVALUATED);

        return report;
    }

    /**
     * 将缓存会话转换为 DTO
     */
    private InterviewSessionDTO toDTO(CachedSession session) {
        List<InterviewQuestionDTO> questions = session.getQuestions(objectMapper);
        return new InterviewSessionDTO(
            session.getSessionId(),
            session.getResumeText(),
            questions.size(),
            session.getCurrentIndex(),
            questions,
            session.getStatus()
        );
    }
}
