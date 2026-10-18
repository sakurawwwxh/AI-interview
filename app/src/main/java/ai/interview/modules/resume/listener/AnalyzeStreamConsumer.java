package ai.interview.modules.resume.listener;

import ai.interview.common.async.AbstractStreamConsumer;
import ai.interview.common.config.AsyncTaskProperties;
import ai.interview.common.constant.AsyncTaskStreamConstants;
import ai.interview.common.model.AsyncTaskStatus;
import ai.interview.infrastructure.redis.RedisService;
import ai.interview.modules.interview.model.ResumeAnalysisResponse;
import ai.interview.modules.resume.model.ResumeEntity;
import ai.interview.modules.resume.repository.ResumeRepository;
import ai.interview.modules.resume.service.ResumeGradingService;
import ai.interview.modules.resume.service.ResumePersistenceService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.stream.StreamMessageId;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 简历分析 Stream 消费者
 * 负责从 Redis Stream 消费消息并执行 AI 分析
 */
@Slf4j
@Component
@Profile("!legacy-migration")
public class AnalyzeStreamConsumer extends AbstractStreamConsumer<AnalyzeStreamConsumer.AnalyzePayload> {

    private final ResumeGradingService gradingService;
    private final ResumePersistenceService persistenceService;
    private final ResumeRepository resumeRepository;
    private final AnalyzeTaskTracker tracker;

    public AnalyzeStreamConsumer(
        RedisService redisService,
        ResumeGradingService gradingService,
        ResumePersistenceService persistenceService,
        ResumeRepository resumeRepository,
        AnalyzeTaskTracker tracker,
        AsyncTaskProperties asyncTaskProperties
    ) {
        super(redisService, asyncTaskProperties.getMaxConcurrency());
        this.gradingService = gradingService;
        this.persistenceService = persistenceService;
        this.resumeRepository = resumeRepository;
        this.tracker = tracker;
    }

    record AnalyzePayload(Long resumeId, Long userId, String content) {}

    @Override
    protected String taskDisplayName() {
        return "简历分析";
    }

    @Override
    protected String streamKey() {
        return AsyncTaskStreamConstants.RESUME_ANALYZE_STREAM_KEY;
    }

    @Override
    protected String groupName() {
        return AsyncTaskStreamConstants.RESUME_ANALYZE_GROUP_NAME;
    }

    @Override
    protected String consumerPrefix() {
        return AsyncTaskStreamConstants.RESUME_ANALYZE_CONSUMER_PREFIX;
    }

    @Override
    protected String threadName() {
        return "analyze-consumer";
    }

    @Override
    protected AnalyzePayload parsePayload(StreamMessageId messageId, Map<String, String> data) {
        String resumeIdStr = data.get(AsyncTaskStreamConstants.FIELD_RESUME_ID);
        String userIdStr = data.get(AsyncTaskStreamConstants.FIELD_USER_ID);
        String content = data.get(AsyncTaskStreamConstants.FIELD_CONTENT);
        if (resumeIdStr == null || content == null) {
            log.warn("消息格式错误，跳过: messageId={}", messageId);
            return null;
        }
        try {
            Long resumeId = Long.parseLong(resumeIdStr);
            // Messages created before userId was added remain processable during a rolling restart.
            Long userId = userIdStr == null
                ? resumeRepository.findById(resumeId).map(ResumeEntity::getUserId).orElse(null)
                : Long.parseLong(userIdStr);
            if (userId == null) {
                log.warn("简历或所属用户不存在，跳过: messageId={}, resumeId={}", messageId, resumeId);
                return null;
            }
            return new AnalyzePayload(resumeId, userId, content);
        } catch (NumberFormatException e) {
            log.warn("简历分析任务 ID 格式错误，跳过: messageId={}", messageId);
            return null;
        }
    }

    @Override
    protected String payloadIdentifier(AnalyzePayload payload) {
        return "resumeId=" + payload.resumeId() + ",userId=" + payload.userId();
    }

    @Override
    protected void markProcessing(AnalyzePayload payload) {
        updateAnalyzeStatus(payload.resumeId(), AsyncTaskStatus.PROCESSING, null);
        // 已经开始消费，不再需要回收兜底；留着会让「排队久但仍会执行」的任务被误判
        tracker.clear(payload.resumeId());
    }

    @Override
    protected void processBusiness(AnalyzePayload payload) {
        Long resumeId = payload.resumeId();
        ResumeEntity resume = resumeRepository.findById(resumeId).orElse(null);
        if (resume == null) {
            log.warn("简历已被删除，跳过分析任务: resumeId={}", resumeId);
            return;
        }
        if (!resume.getUserId().equals(payload.userId())) {
            log.warn("简历分析任务归属不匹配，跳过: resumeId={}, msgUserId={}, resumeUserId={}",
                resumeId, payload.userId(), resume.getUserId());
            return;
        }

        var securityContext = org.springframework.security.core.context.SecurityContextHolder.getContext();
        var previousAuthentication = securityContext.getAuthentication();
        securityContext.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
            payload.userId(), "async-resume-analysis", java.util.List.of()));
        try {
            ResumeAnalysisResponse analysis = gradingService.analyzeResume(payload.content());
            persistenceService.saveAnalysis(resume, analysis);
        } finally {
            securityContext.setAuthentication(previousAuthentication);
        }
    }

    @Override
    protected void markCompleted(AnalyzePayload payload) {
        updateAnalyzeStatus(payload.resumeId(), AsyncTaskStatus.COMPLETED, null);
    }

    @Override
    protected void markFailed(AnalyzePayload payload, String error) {
        updateAnalyzeStatus(payload.resumeId(), AsyncTaskStatus.FAILED, error);
    }

    @Override
    protected void retryMessage(AnalyzePayload payload, int retryCount) {
        Long resumeId = payload.resumeId();
        String content = payload.content();
        try {
            Map<String, String> message = Map.of(
                AsyncTaskStreamConstants.FIELD_RESUME_ID, resumeId.toString(),
                AsyncTaskStreamConstants.FIELD_USER_ID, payload.userId().toString(),
                AsyncTaskStreamConstants.FIELD_CONTENT, content,
                AsyncTaskStreamConstants.FIELD_RETRY_COUNT, String.valueOf(retryCount)
            );

            redisService().streamAdd(
                AsyncTaskStreamConstants.RESUME_ANALYZE_STREAM_KEY,
                message,
                AsyncTaskStreamConstants.STREAM_MAX_LEN
            );
            log.info("简历分析任务已重新入队: resumeId={}, retryCount={}", resumeId, retryCount);

        } catch (Exception e) {
            log.error("重试入队失败: resumeId={}, error={}", resumeId, e.getMessage(), e);
            updateAnalyzeStatus(resumeId, AsyncTaskStatus.FAILED, truncateError("重试入队失败: " + e.getMessage()));
        }
    }

    /**
     * 更新分析状态
     */
    private void updateAnalyzeStatus(Long resumeId, AsyncTaskStatus status, String error) {
        try {
            resumeRepository.findById(resumeId).ifPresent(resume -> {
                resume.setAnalyzeStatus(status);
                resume.setAnalyzeError(error);
                resumeRepository.save(resume);
                log.debug("分析状态已更新: resumeId={}, status={}", resumeId, status);
            });
        } catch (Exception e) {
            log.error("更新分析状态失败: resumeId={}, status={}, error={}", resumeId, status, e.getMessage(), e);
        }
    }

}
