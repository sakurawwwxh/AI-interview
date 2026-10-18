package ai.interview.modules.resume.listener;

import ai.interview.infrastructure.redis.RedisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyzeTaskTrackerTest {

    @Mock private RedisService redisService;

    private AnalyzeTaskTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new AnalyzeTaskTracker(redisService);
    }

    @Test
    @DisplayName("登记时写入当前时间作为分值")
    void markQueuedStoresCurrentTimestamp() {
        long before = System.currentTimeMillis();

        tracker.markQueued(5L);

        ArgumentCaptor<Double> score = ArgumentCaptor.forClass(Double.class);
        verify(redisService).zAdd(eq(AnalyzeTaskTracker.PENDING_KEY), score.capture(), eq("5"));
        assertTrue(score.getValue() >= before - 1000 && score.getValue() <= System.currentTimeMillis() + 1000,
            "分值应当是登记时刻的毫秒时间戳");
    }

    @Test
    @DisplayName("取出候选时把成员还原成简历 ID")
    void claimedBeforeReturnsResumeIds() {
        Instant cutoff = Instant.now();
        when(redisService.zRangeByScore(eq(AnalyzeTaskTracker.PENDING_KEY),
            eq(Double.NEGATIVE_INFINITY), eq((double) cutoff.toEpochMilli()), eq(10)))
            .thenReturn(List.of("5", "6"));

        assertEquals(List.of(5L, 6L), tracker.claimedBefore(cutoff, 10));
    }

    @Test
    @DisplayName("清理只按分值区间删，刷新过的登记保留")
    void clearStaleRemovesOnlyExpiredEntries() {
        Instant cutoff = Instant.now();

        tracker.clearStale(cutoff);

        verify(redisService).zRemoveRangeByScore(AnalyzeTaskTracker.PENDING_KEY,
            Double.NEGATIVE_INFINITY, cutoff.toEpochMilli());
    }

    @Test
    @DisplayName("脏成员被跳过，不影响同批其他记录")
    void skipsUnparsableMembers() {
        Instant cutoff = Instant.now();
        when(redisService.zRangeByScore(eq(AnalyzeTaskTracker.PENDING_KEY),
            eq(Double.NEGATIVE_INFINITY), eq((double) cutoff.toEpochMilli()), eq(10)))
            .thenReturn(List.of("5", "\u0001\u0002garbage", "6"));

        assertEquals(List.of(5L, 6L), tracker.claimedBefore(cutoff, 10));
    }

    @Test
    @DisplayName("Redis 故障时追踪只记日志，不影响主流程")
    void redisFailuresDoNotBreakTheCaller() {
        doThrow(new IllegalStateException("redis down"))
            .when(redisService).zAdd(anyString(), anyDouble(), anyString());
        doThrow(new IllegalStateException("redis down"))
            .when(redisService).zRemove(anyString(), anyString());

        assertDoesNotThrow(() -> tracker.markQueued(5L));
        assertDoesNotThrow(() -> tracker.clear(5L));
    }
}
