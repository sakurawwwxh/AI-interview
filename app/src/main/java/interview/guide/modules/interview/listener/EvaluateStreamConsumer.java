package interview.guide.modules.interview.listener;

import interview.guide.common.async.AbstractStreamConsumer;
import interview.guide.common.constant.AsyncTaskStreamConstants;
import interview.guide.common.model.AsyncTaskStatus;
import interview.guide.infrastructure.redis.RedisService;
import interview.guide.modules.interview.model.InterviewQuestionDTO;
import interview.guide.modules.interview.model.InterviewReportDTO;
import interview.guide.modules.interview.model.InterviewSessionEntity;
import interview.guide.modules.interview.repository.InterviewSessionRepository;
import interview.guide.modules.interview.service.AnswerEvaluationService;
import interview.guide.modules.interview.service.InterviewPersistenceService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.stream.StreamMessageId;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 面试评估 Stream 消费者
 * 负责从 Redis Stream 消费消息并执行评估
 */
@Slf4j
@Component
@Profile("!legacy-migration")
public class EvaluateStreamConsumer extends AbstractStreamConsumer<EvaluateStreamConsumer.EvaluatePayload> {

    private final InterviewSessionRepository sessionRepository;
    private final AnswerEvaluationService evaluationService;
    private final InterviewPersistenceService persistenceService;
    private final ObjectMapper objectMapper;

    public EvaluateStreamConsumer(
        RedisService redisService,
        InterviewSessionRepository sessionRepository,
        AnswerEvaluationService evaluationService,
        InterviewPersistenceService persistenceService,
        ObjectMapper objectMapper
    ) {
        super(redisService);
        this.sessionRepository = sessionRepository;
        this.evaluationService = evaluationService;
        this.persistenceService = persistenceService;
        this.objectMapper = objectMapper;
    }

    record EvaluatePayload(Long userId, String sessionId) {}

    @Override
    protected String taskDisplayName() {
        return "评估";
    }

    @Override
    protected String streamKey() {
        return AsyncTaskStreamConstants.INTERVIEW_EVALUATE_STREAM_KEY;
    }

    @Override
    protected String groupName() {
        return AsyncTaskStreamConstants.INTERVIEW_EVALUATE_GROUP_NAME;
    }

    @Override
    protected String consumerPrefix() {
        return AsyncTaskStreamConstants.INTERVIEW_EVALUATE_CONSUMER_PREFIX;
    }

    @Override
    protected String threadName() {
        return "evaluate-consumer";
    }

    @Override
    protected EvaluatePayload parsePayload(StreamMessageId messageId, Map<String, String> data) {
        String sessionId = data.get(AsyncTaskStreamConstants.FIELD_SESSION_ID);
        String userIdStr = data.get(AsyncTaskStreamConstants.FIELD_USER_ID);
        if (sessionId == null || userIdStr == null) {
            log.warn("消息格式错误，跳过: messageId={}", messageId);
            return null;
        }
        try {
            return new EvaluatePayload(Long.valueOf(userIdStr), sessionId);
        } catch (NumberFormatException e) {
            log.warn("userId 格式错误，跳过: messageId={}, userId={}", messageId, userIdStr);
            return null;
        }
    }

    @Override
    protected String payloadIdentifier(EvaluatePayload payload) {
        return "sessionId=" + payload.sessionId() + ",userId=" + payload.userId();
    }

    @Override
    protected void markProcessing(EvaluatePayload payload) {
        updateEvaluateStatus(payload.sessionId(), AsyncTaskStatus.PROCESSING, null);
    }

    @Override
    protected void processBusiness(EvaluatePayload payload) {
        String sessionId = payload.sessionId();
        Optional<InterviewSessionEntity> sessionOpt = sessionRepository.findBySessionIdWithResume(sessionId);
        if (sessionOpt.isEmpty()) {
            log.warn("会话已被删除，跳过评估任务: sessionId={}", sessionId);
            return;
        }

        InterviewSessionEntity session = sessionOpt.get();

        // 校验异步任务归属：消息携带的 userId 必须与会话实际归属一致
        if (!session.getUserId().equals(payload.userId())) {
            log.warn("评估任务归属不匹配，跳过: sessionId={}, msgUserId={}, sessionUserId={}",
                sessionId, payload.userId(), session.getUserId());
            return;
        }

        List<InterviewQuestionDTO> questions = objectMapper.readValue(
            session.getQuestionsJson(),
            new TypeReference<>() {}
        );

        List<interview.guide.modules.interview.model.InterviewAnswerEntity> answers =
            persistenceService.findAnswersBySessionId(sessionId);
        for (interview.guide.modules.interview.model.InterviewAnswerEntity answer : answers) {
            int index = answer.getQuestionIndex();
            if (index >= 0 && index < questions.size()) {
                InterviewQuestionDTO question = questions.get(index);
                questions.set(index, question.withAnswer(answer.getUserAnswer()));
            }
        }

        var securityContext = org.springframework.security.core.context.SecurityContextHolder.getContext();
        var previousAuthentication = securityContext.getAuthentication();
        securityContext.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
            payload.userId(), "async-evaluation", java.util.List.of()));
        try {
            String resumeText = session.getResume().getResumeText();
            InterviewReportDTO report = evaluationService.evaluateInterview(sessionId, resumeText, questions);
            persistenceService.saveReport(sessionId, payload.userId(), report);
        } finally {
            securityContext.setAuthentication(previousAuthentication);
        }
    }

    @Override
    protected void markCompleted(EvaluatePayload payload) {
        updateEvaluateStatus(payload.sessionId(), AsyncTaskStatus.COMPLETED, null);
    }

    @Override
    protected void markFailed(EvaluatePayload payload, String error) {
        updateEvaluateStatus(payload.sessionId(), AsyncTaskStatus.FAILED, error);
    }

    @Override
    protected void retryMessage(EvaluatePayload payload, int retryCount) {
        String sessionId = payload.sessionId();
        try {
            Map<String, String> message = Map.of(
                AsyncTaskStreamConstants.FIELD_SESSION_ID, sessionId,
                AsyncTaskStreamConstants.FIELD_USER_ID, String.valueOf(payload.userId()),
                AsyncTaskStreamConstants.FIELD_RETRY_COUNT, String.valueOf(retryCount)
            );

            redisService().streamAdd(
                AsyncTaskStreamConstants.INTERVIEW_EVALUATE_STREAM_KEY,
                message,
                AsyncTaskStreamConstants.STREAM_MAX_LEN
            );
            log.info("评估任务已重新入队: sessionId={}, userId={}, retryCount={}", sessionId, payload.userId(), retryCount);

        } catch (Exception e) {
            log.error("重试入队失败: sessionId={}, error={}", sessionId, e.getMessage(), e);
            updateEvaluateStatus(sessionId, AsyncTaskStatus.FAILED, truncateError("重试入队失败: " + e.getMessage()));
        }
    }

    /**
     * 更新评估状态
     */
    private void updateEvaluateStatus(String sessionId, AsyncTaskStatus status, String error) {
        try {
            sessionRepository.findBySessionId(sessionId).ifPresent(session -> {
                session.setEvaluateStatus(status);
                session.setEvaluateError(error);
                if (status == AsyncTaskStatus.PENDING) {
                    session.setEvaluateProgress(0);
                } else if (status == AsyncTaskStatus.PROCESSING
                    && (session.getEvaluateProgress() == null || session.getEvaluateProgress() < 5)) {
                    session.setEvaluateProgress(5);
                } else if (status == AsyncTaskStatus.COMPLETED) {
                    session.setEvaluateProgress(100);
                }
                sessionRepository.save(session);
                log.debug("评估状态已更新: sessionId={}, status={}, progress={}",
                    sessionId, status, session.getEvaluateProgress());
            });
        } catch (Exception e) {
            log.error("更新评估状态失败: sessionId={}, status={}, error={}", sessionId, status, e.getMessage(), e);
        }
    }

}
