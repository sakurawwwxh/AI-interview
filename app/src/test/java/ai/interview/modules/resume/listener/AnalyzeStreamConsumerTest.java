package ai.interview.modules.resume.listener;

import ai.interview.common.config.AsyncTaskProperties;
import ai.interview.infrastructure.redis.RedisService;
import ai.interview.modules.resume.repository.ResumeRepository;
import ai.interview.modules.resume.service.ResumeGradingService;
import ai.interview.modules.resume.service.ResumePersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyzeStreamConsumerTest {

    @Mock private RedisService redisService;
    @Mock private ResumeGradingService gradingService;
    @Mock private ResumePersistenceService persistenceService;
    @Mock private ResumeRepository resumeRepository;
    @Mock private AnalyzeTaskTracker tracker;

    private AnalyzeStreamConsumer consumer;

    @BeforeEach
    void setUp() {
        AsyncTaskProperties properties = mock(AsyncTaskProperties.class);
        when(properties.getMaxConcurrency()).thenReturn(2);
        consumer = new AnalyzeStreamConsumer(redisService, gradingService, persistenceService,
            resumeRepository, tracker, properties);
    }

    @Test
    @DisplayName("开始消费即清掉登记，避免排队久的任务被回收器误判")
    void clearsTrackingWhenProcessingStarts() {
        when(resumeRepository.findById(5L)).thenReturn(Optional.empty());

        consumer.markProcessing(new AnalyzeStreamConsumer.AnalyzePayload(5L, 7L, "正文"));

        verify(tracker).clear(5L);
    }
}
