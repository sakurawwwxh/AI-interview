package interview.guide.modules.interview.listener;

import interview.guide.common.model.AsyncTaskStatus;
import interview.guide.infrastructure.redis.RedisService;
import interview.guide.modules.interview.model.InterviewSessionEntity;
import interview.guide.modules.interview.repository.InterviewSessionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 面试评估任务生产者单元测试
 */
class EvaluateStreamProducerTest {

    private final RedisService redisService = mock(RedisService.class);
    private final InterviewSessionRepository sessionRepository = mock(InterviewSessionRepository.class);

    private final EvaluateStreamProducer producer = new EvaluateStreamProducer(redisService, sessionRepository);

    @Nested
    @DisplayName("sendEvaluateTask 发送评估任务")
    class SendEvaluateTask {

        @Test
        @DisplayName("发送成功时调用 streamAdd")
        void shouldCallStreamAddOnSuccess() {
            when(redisService.streamAdd(anyString(), anyMap(), anyInt()))
                .thenReturn("msg-123");

            producer.sendEvaluateTask(1L, "session-1");

            verify(redisService).streamAdd(anyString(), anyMap(), anyInt());
            verify(sessionRepository, never()).findBySessionId(anyString());
        }

        @Test
        @DisplayName("发送失败时回写 FAILED 状态")
        void shouldUpdateStatusOnFailure() {
            when(redisService.streamAdd(anyString(), anyMap(), anyInt()))
                .thenThrow(new RuntimeException("Redis 连接失败"));

            InterviewSessionEntity session = new InterviewSessionEntity();
            session.setSessionId("session-1");
            when(sessionRepository.findBySessionId("session-1"))
                .thenReturn(Optional.of(session));
            when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            producer.sendEvaluateTask(1L, "session-1");

            assertEquals(AsyncTaskStatus.FAILED, session.getEvaluateStatus());
            assertNotNull(session.getEvaluateError());
            verify(sessionRepository).save(session);
        }

        @Test
        @DisplayName("发送失败且会话不存在时不抛异常")
        void shouldNotThrowWhenSessionNotFoundOnFailure() {
            when(redisService.streamAdd(anyString(), anyMap(), anyInt()))
                .thenThrow(new RuntimeException("Redis 连接失败"));
            when(sessionRepository.findBySessionId("missing"))
                .thenReturn(Optional.empty());

            assertDoesNotThrow(() -> producer.sendEvaluateTask(1L, "missing"));
            verify(sessionRepository, never()).save(any());
        }
    }
}
