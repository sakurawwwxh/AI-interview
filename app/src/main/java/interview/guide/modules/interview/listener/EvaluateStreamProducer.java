package interview.guide.modules.interview.listener;

import interview.guide.common.async.AbstractStreamProducer;
import interview.guide.common.constant.AsyncTaskStreamConstants;
import interview.guide.common.model.AsyncTaskStatus;
import interview.guide.infrastructure.redis.RedisService;
import interview.guide.modules.interview.repository.InterviewSessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 面试评估任务生产者
 * 负责发送评估任务到 Redis Stream
 */
@Slf4j
@Component
public class EvaluateStreamProducer extends AbstractStreamProducer<EvaluateStreamProducer.EvaluateTaskPayload> {

    private final InterviewSessionRepository sessionRepository;

    public EvaluateStreamProducer(RedisService redisService, InterviewSessionRepository sessionRepository) {
        super(redisService);
        this.sessionRepository = sessionRepository;
    }

    /**
     * 发送评估任务到 Redis Stream
     *
     * @param userId    面试会话所属用户ID
     * @param sessionId 面试会话ID
     */
    public void sendEvaluateTask(Long userId, String sessionId) {
        sendTask(new EvaluateTaskPayload(userId, sessionId));
    }

    @Override
    protected String taskDisplayName() {
        return "评估";
    }

    @Override
    protected String streamKey() {
        return AsyncTaskStreamConstants.INTERVIEW_EVALUATE_STREAM_KEY;
    }

    @Override
    protected Map<String, String> buildMessage(EvaluateTaskPayload payload) {
        return Map.of(
            AsyncTaskStreamConstants.FIELD_SESSION_ID, payload.sessionId(),
            AsyncTaskStreamConstants.FIELD_USER_ID, String.valueOf(payload.userId()),
            AsyncTaskStreamConstants.FIELD_RETRY_COUNT, "0"
        );
    }

    @Override
    protected String payloadIdentifier(EvaluateTaskPayload payload) {
        return "sessionId=" + payload.sessionId() + ",userId=" + payload.userId();
    }

    @Override
    protected void onSendFailed(EvaluateTaskPayload payload, String error) {
        updateEvaluateStatus(payload.sessionId(), AsyncTaskStatus.FAILED, truncateError(error));
    }

    /**
     * 更新评估状态
     */
    private void updateEvaluateStatus(String sessionId, AsyncTaskStatus status, String error) {
        sessionRepository.findBySessionId(sessionId).ifPresent(session -> {
            session.setEvaluateStatus(status);
            if (error != null) {
                session.setEvaluateError(error.length() > 500 ? error.substring(0, 500) : error);
            }
            sessionRepository.save(session);
        });
    }

    /** 评估任务载荷，携带 userId 以便消费者校验归属。 */
    public record EvaluateTaskPayload(Long userId, String sessionId) {}
}
