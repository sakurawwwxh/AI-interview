package ai.interview.modules.resume.listener;

import ai.interview.common.async.AbstractStreamProducer;
import ai.interview.common.constant.AsyncTaskStreamConstants;
import ai.interview.common.model.AsyncTaskStatus;
import ai.interview.infrastructure.redis.RedisService;
import ai.interview.modules.resume.repository.ResumeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 简历分析任务生产者
 * 负责发送分析任务到 Redis Stream
 */
@Slf4j
@Component
public class AnalyzeStreamProducer extends AbstractStreamProducer<AnalyzeStreamProducer.AnalyzeTaskPayload> {

    private final ResumeRepository resumeRepository;
    private final AnalyzeTaskTracker tracker;

    record AnalyzeTaskPayload(Long resumeId, Long userId, String content) {}

    public AnalyzeStreamProducer(RedisService redisService, ResumeRepository resumeRepository,
                                 AnalyzeTaskTracker tracker) {
        super(redisService);
        this.resumeRepository = resumeRepository;
        this.tracker = tracker;
    }

    /**
     * 发送分析任务到 Redis Stream
     *
     * @param resumeId 简历ID
     * @param content  简历内容
     */
    public void sendAnalyzeTask(Long resumeId, Long userId, String content) {
        // 先登记再入队：入队失败时 onSendFailed 会把状态改成 FAILED，回收器看到非 PENDING 就只清登记
        tracker.markQueued(resumeId);
        String messageId = sendTask(new AnalyzeTaskPayload(resumeId, userId, content));
        if (messageId != null) {
            // 记下消息 ID，回收器据此区分「消息丢了」与「还在队列里排队」
            tracker.attachMessage(resumeId, messageId);
        }
    }

    @Override
    protected String taskDisplayName() {
        return "分析";
    }

    @Override
    protected String streamKey() {
        return AsyncTaskStreamConstants.RESUME_ANALYZE_STREAM_KEY;
    }

    @Override
    protected Map<String, String> buildMessage(AnalyzeTaskPayload payload) {
        return Map.of(
            AsyncTaskStreamConstants.FIELD_RESUME_ID, payload.resumeId().toString(),
            AsyncTaskStreamConstants.FIELD_USER_ID, payload.userId().toString(),
            AsyncTaskStreamConstants.FIELD_CONTENT, payload.content(),
            AsyncTaskStreamConstants.FIELD_RETRY_COUNT, "0"
        );
    }

    @Override
    protected String payloadIdentifier(AnalyzeTaskPayload payload) {
        return "resumeId=" + payload.resumeId() + ",userId=" + payload.userId();
    }

    @Override
    protected void onSendFailed(AnalyzeTaskPayload payload, String error) {
        updateAnalyzeStatus(payload.resumeId(), AsyncTaskStatus.FAILED, truncateError(error));
    }

    /**
     * 更新分析状态
     */
    private void updateAnalyzeStatus(Long resumeId, AsyncTaskStatus status, String error) {
        resumeRepository.findById(resumeId).ifPresent(resume -> {
            resume.setAnalyzeStatus(status);
            if (error != null) {
                resume.setAnalyzeError(error.length() > 500 ? error.substring(0, 500) : error);
            }
            resumeRepository.save(resume);
        });
    }
}
