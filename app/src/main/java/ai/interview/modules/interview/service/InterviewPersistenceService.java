package ai.interview.modules.interview.service;

import ai.interview.common.exception.BusinessException;
import ai.interview.common.exception.ErrorCode;
import ai.interview.common.model.AsyncTaskStatus;
import ai.interview.modules.interview.model.InterviewAnswerEntity;
import ai.interview.modules.interview.model.InterviewQuestionDTO;
import ai.interview.modules.interview.model.InterviewReportDTO;
import ai.interview.modules.interview.model.InterviewSessionEntity;
import ai.interview.modules.interview.model.InterviewTemplateConfig;
import ai.interview.modules.interview.repository.InterviewAnswerRepository;
import ai.interview.modules.interview.repository.InterviewSessionRepository;
import ai.interview.modules.resume.model.ResumeEntity;
import ai.interview.modules.resume.repository.ResumeRepository;
import ai.interview.modules.practice.service.PracticeTaskService;
import ai.interview.modules.user.security.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 面试持久化服务
 * 面试会话和答案的持久化
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewPersistenceService {
    
    private final InterviewSessionRepository sessionRepository;
    private final InterviewAnswerRepository answerRepository;
    private final ResumeRepository resumeRepository;
    private final ObjectMapper objectMapper;
    private final PracticeTaskService practiceTaskService;

    /**
     * 校验当前用户拥有指定简历。
     *
     * <p>创建会话、读取历史题目等所有依赖 resumeId 的流程都必须先调用此方法，
     * 不能只把归属校验放在某个可被跳过的分支里。</p>
     */
    public void requireOwnedResume(Long resumeId) {
        Long userId = UserContext.getCurrentUserIdOrThrow();
        if (resumeRepository.findByIdAndUserId(resumeId, userId).isEmpty()) {
            throw new BusinessException(ErrorCode.RESUME_NOT_FOUND);
        }
    }
    
    /**
     * 保存新的面试会话
     */
    @Transactional(rollbackFor = Exception.class)
    public InterviewSessionEntity saveSession(String sessionId, Long resumeId, 
                                              int totalQuestions, 
                                              List<InterviewQuestionDTO> questions,
                                              InterviewTemplateConfig template) {
        try {
            Long userId = UserContext.getCurrentUserIdOrThrow();
            Optional<ResumeEntity> resumeOpt = resumeRepository.findByIdAndUserId(resumeId, userId);
            if (resumeOpt.isEmpty()) {
                throw new BusinessException(ErrorCode.RESUME_NOT_FOUND);
            }
            
            InterviewSessionEntity session = new InterviewSessionEntity();
            session.setSessionId(sessionId);
            session.setUserId(userId);
            session.setResume(resumeOpt.get());
            session.setTotalQuestions(totalQuestions);
            session.setCurrentQuestionIndex(0);
            session.setStatus(InterviewSessionEntity.SessionStatus.CREATED);
            session.setQuestionsJson(objectMapper.writeValueAsString(questions));
            session.setTemplateJson(objectMapper.writeValueAsString(template));
            
            InterviewSessionEntity saved = sessionRepository.save(session);
            log.info("面试会话已保存: sessionId={}, resumeId={}", sessionId, resumeId);
            
            return saved;
        } catch (JacksonException e) {
            log.error("序列化问题列表失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "保存会话失败");
        }
    }
    
    /**
     * 更新会话状态
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateSessionStatus(String sessionId, InterviewSessionEntity.SessionStatus status) {
        Long userId = UserContext.getCurrentUserIdOrThrow();
        Optional<InterviewSessionEntity> sessionOpt = sessionRepository.findBySessionIdAndUserId(sessionId, userId);
        if (sessionOpt.isEmpty()) {
            throw new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND);
        }
        InterviewSessionEntity session = sessionOpt.get();
        session.setStatus(status);
        if (status == InterviewSessionEntity.SessionStatus.COMPLETED ||
            status == InterviewSessionEntity.SessionStatus.EVALUATED) {
            session.setCompletedAt(LocalDateTime.now());
        }
        sessionRepository.save(session);
    }

    /**
     * 更新评估状态
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateEvaluateStatus(String sessionId, AsyncTaskStatus status, String error) {
        Long userId = UserContext.getCurrentUserIdOrThrow();
        Optional<InterviewSessionEntity> sessionOpt = sessionRepository.findBySessionIdAndUserId(sessionId, userId);
        if (sessionOpt.isEmpty()) {
            throw new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND);
        }
        InterviewSessionEntity session = sessionOpt.get();
        session.setEvaluateStatus(status);
        if (error != null) {
            session.setEvaluateError(error.length() > 500 ? error.substring(0, 500) : error);
        } else {
            session.setEvaluateError(null);
        }
        // 状态切换时同步进度：等待=0，处理中至少 5，完成=100
        if (status == AsyncTaskStatus.PENDING) {
            session.setEvaluateProgress(0);
        } else if (status == AsyncTaskStatus.PROCESSING && session.getEvaluateProgress() == null) {
            session.setEvaluateProgress(5);
        } else if (status == AsyncTaskStatus.COMPLETED) {
            session.setEvaluateProgress(100);
        }
        sessionRepository.save(session);
        log.debug("评估状态已更新: sessionId={}, status={}", sessionId, status);
    }

    /**
     * 仅更新评估进度（异步消费者可直接调用，按 sessionId 定位）
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateEvaluateProgress(String sessionId, int progress) {
        sessionRepository.findBySessionId(sessionId).ifPresent(session -> {
            session.setEvaluateProgress(Math.min(100, Math.max(0, progress)));
            sessionRepository.save(session);
        });
    }
    
    /**
     * 更新当前问题索引
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateCurrentQuestionIndex(String sessionId, int index) {
        Long userId = UserContext.getCurrentUserIdOrThrow();
        Optional<InterviewSessionEntity> sessionOpt = sessionRepository.findBySessionIdAndUserId(sessionId, userId);
        if (sessionOpt.isEmpty()) {
            throw new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND);
        }
        InterviewSessionEntity session = sessionOpt.get();
        session.setCurrentQuestionIndex(index);
        session.setStatus(InterviewSessionEntity.SessionStatus.IN_PROGRESS);
        sessionRepository.save(session);
    }

    /**
     * 更新问题列表 JSON（动态追问追加后回写，保证异步评估能读到追问）
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateQuestionsJson(String sessionId, List<InterviewQuestionDTO> questions) {
        try {
            Long userId = UserContext.getCurrentUserIdOrThrow();
            Optional<InterviewSessionEntity> sessionOpt = sessionRepository.findBySessionIdAndUserId(sessionId, userId);
            if (sessionOpt.isEmpty()) {
                throw new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND);
            }
            InterviewSessionEntity session = sessionOpt.get();
            session.setQuestionsJson(objectMapper.writeValueAsString(questions));
            session.setTotalQuestions(questions.size());
            sessionRepository.save(session);
        } catch (BusinessException e) {
            throw e;
        } catch (JacksonException e) {
            log.error("序列化问题列表失败: sessionId={}, error={}", sessionId, e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "保存问题列表失败");
        }
    }
    
    /**
     * 保存面试答案
     */
    @Transactional(rollbackFor = Exception.class)
    public InterviewAnswerEntity saveAnswer(String sessionId, int questionIndex,
                                            String question, String category,
                                            String userAnswer, int score, String feedback,
                                            Integer answerDurationSeconds) {
        Long userId = UserContext.getCurrentUserIdOrThrow();
        Optional<InterviewSessionEntity> sessionOpt = sessionRepository.findBySessionIdAndUserId(sessionId, userId);
        if (sessionOpt.isEmpty()) {
            throw new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND);
        }

        InterviewAnswerEntity answer = answerRepository
            .findBySession_SessionIdAndQuestionIndex(sessionId, questionIndex)
            .orElseGet(() -> {
                InterviewAnswerEntity created = new InterviewAnswerEntity();
                created.setSession(sessionOpt.get());
                created.setQuestionIndex(questionIndex);
                return created;
            });

        answer.setQuestion(question);
        answer.setCategory(category);
        answer.setUserAnswer(userAnswer);
        answer.setScore(score);
        answer.setFeedback(feedback);
        answer.setAnswerDurationSeconds(answerDurationSeconds);

        InterviewAnswerEntity saved = answerRepository.save(answer);
        log.info("面试答案已保存: sessionId={}, questionIndex={}, score={}", 
                sessionId, questionIndex, score);
        
        return saved;
    }

    /**
     * 在同一个事务中保存答题结果及会话进度。
     *
     * <p>答题会同时修改答案、当前题目索引、会话状态和题目 JSON。
     * 这些数据必须一起提交，否则其中一步失败时数据库会留下无法恢复的半成品。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void persistSubmittedAnswer(String sessionId, int questionIndex,
                                       String question, String category,
                                       String userAnswer, Integer answerDurationSeconds,
                                       List<InterviewQuestionDTO> questions,
                                       int newIndex, boolean completed) {
        Long userId = UserContext.getCurrentUserIdOrThrow();
        InterviewSessionEntity session = sessionRepository.findBySessionIdAndUserId(sessionId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND));
        if (session.getStatus() == InterviewSessionEntity.SessionStatus.COMPLETED
            || session.getStatus() == InterviewSessionEntity.SessionStatus.EVALUATED) {
            throw new BusinessException(ErrorCode.INTERVIEW_ALREADY_COMPLETED);
        }

        InterviewAnswerEntity answer = answerRepository
            .findBySession_SessionIdAndQuestionIndex(sessionId, questionIndex)
            .orElseGet(() -> {
                InterviewAnswerEntity created = new InterviewAnswerEntity();
                created.setSession(session);
                created.setQuestionIndex(questionIndex);
                return created;
            });
        answer.setQuestion(question);
        answer.setCategory(category);
        answer.setUserAnswer(userAnswer);
        answer.setScore(0);
        answer.setFeedback(null);
        answer.setAnswerDurationSeconds(answerDurationSeconds);
        answerRepository.save(answer);

        try {
            session.setCurrentQuestionIndex(newIndex);
            session.setStatus(completed
                ? InterviewSessionEntity.SessionStatus.COMPLETED
                : InterviewSessionEntity.SessionStatus.IN_PROGRESS);
            if (completed) {
                session.setCompletedAt(LocalDateTime.now());
                session.setEvaluateStatus(AsyncTaskStatus.PENDING);
                session.setEvaluateProgress(0);
                session.setEvaluateError(null);
            }
            session.setQuestionsJson(objectMapper.writeValueAsString(questions));
            session.setTotalQuestions(questions.size());
            sessionRepository.save(session);
        } catch (JacksonException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "保存面试进度失败");
        }
    }

    /**
     * 在同一个事务中保存暂存答案及进行中状态。
     */
    @Transactional(rollbackFor = Exception.class)
    public void persistDraftAnswer(String sessionId, int questionIndex,
                                   String question, String category,
                                   String userAnswer, Integer answerDurationSeconds) {
        Long userId = UserContext.getCurrentUserIdOrThrow();
        InterviewSessionEntity session = sessionRepository.findBySessionIdAndUserId(sessionId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND));
        if (session.getStatus() == InterviewSessionEntity.SessionStatus.COMPLETED
            || session.getStatus() == InterviewSessionEntity.SessionStatus.EVALUATED) {
            throw new BusinessException(ErrorCode.INTERVIEW_ALREADY_COMPLETED);
        }

        InterviewAnswerEntity answer = answerRepository
            .findBySession_SessionIdAndQuestionIndex(sessionId, questionIndex)
            .orElseGet(() -> {
                InterviewAnswerEntity created = new InterviewAnswerEntity();
                created.setSession(session);
                created.setQuestionIndex(questionIndex);
                return created;
            });
        answer.setQuestion(question);
        answer.setCategory(category);
        answer.setUserAnswer(userAnswer);
        answer.setScore(0);
        answer.setFeedback(null);
        answer.setAnswerDurationSeconds(answerDurationSeconds);
        answerRepository.save(answer);

        session.setStatus(InterviewSessionEntity.SessionStatus.IN_PROGRESS);
        sessionRepository.save(session);
    }

    /**
     * 原子地标记面试完成并创建待评估状态。
     */
    @Transactional(rollbackFor = Exception.class)
    public void markCompletedForEvaluation(String sessionId) {
        Long userId = UserContext.getCurrentUserIdOrThrow();
        InterviewSessionEntity session = sessionRepository.findBySessionIdAndUserId(sessionId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND));
        session.setStatus(InterviewSessionEntity.SessionStatus.COMPLETED);
        session.setCompletedAt(LocalDateTime.now());
        session.setEvaluateStatus(AsyncTaskStatus.PENDING);
        session.setEvaluateProgress(0);
        session.setEvaluateError(null);
        sessionRepository.save(session);
    }
    
    /**
     * 保存面试报告
     */
    /**
     * 保存面试报告（请求线程调用，从 SecurityContext 取 userId）
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveReport(String sessionId, InterviewReportDTO report) {
        Long userId = UserContext.getCurrentUserId();
        if (userId != null) {
            saveReport(sessionId, userId, report);
        } else {
            // 理论上不应走到这里，保留兜底以避免请求线程异常时丢失报告
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "无法获取当前用户信息");
        }
    }

    /**
     * 保存面试报告（显式传入 userId，供异步评估消费者调用）
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveReport(String sessionId, Long userId, InterviewReportDTO report) {
        try {
            Optional<InterviewSessionEntity> sessionOpt = sessionRepository.findBySessionIdAndUserId(sessionId, userId);
            if (sessionOpt.isEmpty()) {
                throw new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND);
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

            // 查询已存在的答案，建立索引
            List<InterviewAnswerEntity> existingAnswers = answerRepository.findBySession_SessionIdOrderByQuestionIndex(sessionId);
            java.util.Map<Integer, InterviewAnswerEntity> answerMap = existingAnswers.stream()
                .collect(java.util.stream.Collectors.toMap(
                    InterviewAnswerEntity::getQuestionIndex,
                    a -> a,
                    (a1, a2) -> a1
                ));

            // 建立参考答案索引
            java.util.Map<Integer, InterviewReportDTO.ReferenceAnswer> refAnswerMap = report.referenceAnswers().stream()
                .collect(java.util.stream.Collectors.toMap(
                    InterviewReportDTO.ReferenceAnswer::questionIndex,
                    r -> r,
                    (r1, r2) -> r1
                ));

            List<InterviewAnswerEntity> answersToSave = new java.util.ArrayList<>();

            // 遍历所有评估结果，更新或创建答案记录
            for (InterviewReportDTO.QuestionEvaluation eval : report.questionDetails()) {
                InterviewAnswerEntity answer = answerMap.get(eval.questionIndex());

                if (answer == null) {
                    // 未回答的题目，创建新记录
                    answer = new InterviewAnswerEntity();
                    answer.setSession(session);
                    answer.setQuestionIndex(eval.questionIndex());
                    answer.setQuestion(eval.question());
                    answer.setCategory(eval.category());
                    answer.setUserAnswer(null);  // 未回答
                    log.debug("为未回答的题目 {} 创建答案记录", eval.questionIndex());
                }

                // 更新评分和反馈
                answer.setScore(eval.score());
                answer.setFeedback(eval.feedback());

                // 设置参考答案和关键点
                InterviewReportDTO.ReferenceAnswer refAns = refAnswerMap.get(eval.questionIndex());
                if (refAns != null) {
                    answer.setReferenceAnswer(refAns.referenceAnswer());
                    if (refAns.keyPoints() != null && !refAns.keyPoints().isEmpty()) {
                        answer.setKeyPointsJson(objectMapper.writeValueAsString(refAns.keyPoints()));
                    }
                }

                answersToSave.add(answer);
            }

            answerRepository.saveAll(answersToSave);
            practiceTaskService.createTasksForEvaluatedAnswers(session.getUserId(), answersToSave);
            log.info("面试报告已保存: sessionId={}, score={}, 答案数={}",
                sessionId, report.overallScore(), answersToSave.size());

        } catch (JacksonException e) {
            log.error("序列化报告失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "保存面试报告失败");
        }
    }
    
    /**
     * 根据会话ID获取会话
     */
    public Optional<InterviewSessionEntity> findBySessionId(String sessionId) {
        return sessionRepository.findBySessionId(sessionId);
    }

    /**
     * 根据会话ID获取会话（鉴权：仅返回属于当前登录用户的会话）
     */
    public Optional<InterviewSessionEntity> findBySessionIdForCurrentUser(String sessionId) {
        Long userId = UserContext.getCurrentUserIdOrThrow();
        return sessionRepository.findBySessionIdAndUserId(sessionId, userId);
    }
    
    /**
     * 获取简历的所有面试记录
     */
    public List<InterviewSessionEntity> findByResumeId(Long resumeId) {
        return sessionRepository.findByResumeIdOrderByCreatedAtDesc(resumeId);
    }

    /**
     * 简历详情页用的面试历史：只取展示需要的 9 个标量列，不把每场的大文本一起拉回来。
     */
    public List<InterviewSessionRepository.ResumeInterviewRow> findResumeInterviewRows(Long resumeId) {
        return sessionRepository.findResumeInterviewRowsByResumeId(resumeId);
    }
    
    /**
     * 删除简历的所有面试会话
     * 由于InterviewSessionEntity设置了cascade = CascadeType.ALL, orphanRemoval = true
     * 删除会话会自动删除关联的答案
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteSessionsByResumeId(Long resumeId) {
        List<InterviewSessionEntity> sessions = sessionRepository.findByResumeIdOrderByCreatedAtDesc(resumeId);
        if (!sessions.isEmpty()) {
            sessionRepository.deleteAll(sessions);
            log.info("已删除 {} 个面试会话（包含所有答案）", sessions.size());
        }
    }
    
    /**
     * 删除单个面试会话
     * 由于InterviewSessionEntity设置了cascade = CascadeType.ALL, orphanRemoval = true
     * 删除会话会自动删除关联的答案
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteSessionBySessionId(String sessionId) {
        Long userId = UserContext.getCurrentUserIdOrThrow();
        Optional<InterviewSessionEntity> sessionOpt = sessionRepository.findBySessionIdAndUserId(sessionId, userId);
        if (sessionOpt.isEmpty()) {
            throw new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND);
        }
        sessionRepository.delete(sessionOpt.get());
        log.info("已删除面试会话: sessionId={}", sessionId);
    }
    
    /**
     * 查找未完成的面试会话（CREATED或IN_PROGRESS状态）
     */
    public Optional<InterviewSessionEntity> findUnfinishedSession(Long resumeId) {
        Long userId = UserContext.getCurrentUserIdOrThrow();
        if (resumeRepository.findByIdAndUserId(resumeId, userId).isEmpty()) {
            throw new BusinessException(ErrorCode.RESUME_NOT_FOUND);
        }
        List<InterviewSessionEntity.SessionStatus> unfinishedStatuses = List.of(
            InterviewSessionEntity.SessionStatus.CREATED,
            InterviewSessionEntity.SessionStatus.IN_PROGRESS
        );
        return sessionRepository.findFirstByResumeIdAndStatusInOrderByCreatedAtDesc(resumeId, unfinishedStatuses);
    }
    
    /**
     * 根据会话ID查找所有答案
     */
    public List<InterviewAnswerEntity> findAnswersBySessionId(String sessionId) {
        return answerRepository.findBySession_SessionIdOrderByQuestionIndex(sessionId);
    }

    /**
     * 获取简历的历史提问列表（限制最近的 N 条）
     */
    public List<String> getHistoricalQuestionsByResumeId(Long resumeId) {
        // 只查询最近的 10 个会话，避免加载过多历史数据
        List<InterviewSessionEntity> sessions = sessionRepository.findTop10ByResumeIdOrderByCreatedAtDesc(resumeId);
        
        return sessions.stream()
            .map(InterviewSessionEntity::getQuestionsJson)
            .filter(json -> json != null && !json.isEmpty())
            .flatMap(json -> {
                try {
                    List<InterviewQuestionDTO> questions = objectMapper.readValue(json, 
                        new TypeReference<List<InterviewQuestionDTO>>() {});
                    // 过滤掉追问，只保留主问题作为历史参考
                    return questions.stream()
                        .filter(q -> !q.isFollowUp())
                        .map(InterviewQuestionDTO::question);
                } catch (Exception e) {
                    log.error("解析历史问题JSON失败", e);
                    return java.util.stream.Stream.empty();
                }
            })
            .distinct()
            .limit(30) // 核心改动：只保留最近的 30 道题
            .toList();
    }

    /**
     * 从已落库的会话与答案还原面试报告；还没有出过报告时返回 empty。
     *
     * <p>报告字段与逐题评分在 saveReport 时都已落库，这里只做读回与重算、不调用模型——
     * 否则每打开一次报告就重跑一遍 AI 评估（既让用户等，也重复花钱）。
     */
    public Optional<InterviewReportDTO> restoreReport(String sessionId) {
        Optional<InterviewSessionEntity> sessionOpt = sessionRepository.findBySessionId(sessionId);
        if (sessionOpt.isEmpty()) {
            return Optional.empty();
        }
        InterviewSessionEntity session = sessionOpt.get();
        if (session.getOverallScore() == null || session.getOverallFeedback() == null) {
            return Optional.empty();
        }

        List<InterviewQuestionDTO> questions = readReportJson(session.getQuestionsJson(),
            new TypeReference<List<InterviewQuestionDTO>>() {});
        List<String> strengths = readReportJson(session.getStrengthsJson(),
            new TypeReference<List<String>>() {});
        List<String> improvements = readReportJson(session.getImprovementsJson(),
            new TypeReference<List<String>>() {});
        List<InterviewReportDTO.ReferenceAnswer> referenceAnswers = readReportJson(
            session.getReferenceAnswersJson(),
            new TypeReference<List<InterviewReportDTO.ReferenceAnswer>>() {});

        Map<Integer, InterviewAnswerEntity> answerByIndex = answerRepository
            .findBySession_SessionIdOrderByQuestionIndex(sessionId).stream()
            .filter(answer -> answer.getQuestionIndex() != null)
            .collect(java.util.stream.Collectors.toMap(
                InterviewAnswerEntity::getQuestionIndex, answer -> answer, (first, second) -> first));

        List<InterviewReportDTO.QuestionEvaluation> details = new ArrayList<>();
        Map<String, List<Integer>> scoresByCategory = new LinkedHashMap<>();
        for (InterviewQuestionDTO question : questions) {
            InterviewAnswerEntity answer = answerByIndex.get(question.questionIndex());
            int score = answer != null && answer.getScore() != null ? answer.getScore() : 0;
            String feedback = answer != null && answer.getFeedback() != null
                ? answer.getFeedback() : "该题未成功生成评估反馈。";
            details.add(new InterviewReportDTO.QuestionEvaluation(
                question.questionIndex(), question.question(), question.category(),
                question.userAnswer(), score, feedback));
            scoresByCategory.computeIfAbsent(question.category(), ignored -> new ArrayList<>()).add(score);
        }

        List<InterviewReportDTO.CategoryScore> categoryScores = scoresByCategory.entrySet().stream()
            .map(entry -> new InterviewReportDTO.CategoryScore(
                entry.getKey(),
                (int) entry.getValue().stream().mapToInt(Integer::intValue).average().orElse(0),
                entry.getValue().size()))
            .toList();

        return Optional.of(new InterviewReportDTO(
            sessionId, questions.size(), session.getOverallScore(), categoryScores,
            details, session.getOverallFeedback(), strengths, improvements, referenceAnswers));
    }

    /** 报告里的 JSON 列解析失败时按空列表处理：宁可少一段展示，也不要让报告接口 500。 */
    private <T> List<T> readReportJson(String json, TypeReference<List<T>> type) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.warn("解析面试报告字段失败: {}", e.getMessage());
            return List.of();
        }
    }
}
