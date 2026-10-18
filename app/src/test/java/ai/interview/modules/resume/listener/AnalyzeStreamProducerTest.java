package ai.interview.modules.resume.listener;

import ai.interview.infrastructure.redis.RedisService;
import ai.interview.modules.resume.repository.ResumeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyzeStreamProducerTest {

    @Mock private RedisService redisService;
    @Mock private ResumeRepository resumeRepository;
    @Mock private AnalyzeTaskTracker tracker;

    private AnalyzeStreamProducer producer;

    @BeforeEach
    void setUp() {
        producer = new AnalyzeStreamProducer(redisService, resumeRepository, tracker);
    }

    @Test
    @DisplayName("先登记再入队：入队失败时才有据可查")
    void marksQueuedBeforeSending() {
        when(redisService.streamAdd(anyString(), anyMap(), anyInt())).thenReturn("1-1");

        producer.sendAnalyzeTask(5L, 7L, "正文");

        InOrder inOrder = inOrder(tracker, redisService);
        inOrder.verify(tracker).markQueued(5L);
        inOrder.verify(redisService).streamAdd(anyString(), anyMap(), anyInt());
    }
}
