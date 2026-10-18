package ai.interview.modules.resume.listener;

import ai.interview.common.model.AsyncTaskStatus;
import ai.interview.modules.resume.repository.ResumeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResumeAnalyzeReaperTest {

    private static final long RESUME_ID = 5L;

    @Mock private AnalyzeTaskTracker tracker;
    @Mock private ResumeRepository resumeRepository;

    private ResumeAnalyzeReaper reaper;

    @BeforeEach
    void setUp() {
        reaper = new ResumeAnalyzeReaper(tracker, resumeRepository);
        ReflectionTestUtils.setField(reaper, "stalePendingMinutes", 10L);
    }

    @Test
    @DisplayName("超时仍是 PENDING：压回 FAILED 并清掉登记")
    void flipsStalePendingToFailed() {
        when(tracker.claimedBefore(any(), anyInt())).thenReturn(List.of(RESUME_ID));
        when(resumeRepository.failIfStillPending(eq(RESUME_ID), eq(AsyncTaskStatus.FAILED),
            anyString(), eq(AsyncTaskStatus.PENDING))).thenReturn(1);

        reaper.failStalePendingTasks();

        verify(resumeRepository).failIfStillPending(eq(RESUME_ID), eq(AsyncTaskStatus.FAILED),
            anyString(), eq(AsyncTaskStatus.PENDING));
        verify(tracker).clearStale(any());
    }

    @Test
    @DisplayName("任务已推进到别的状态：只清登记，不动数据")
    void onlyUnregistersWhenStatusMovedOn() {
        when(tracker.claimedBefore(any(), anyInt())).thenReturn(List.of(RESUME_ID));
        when(resumeRepository.failIfStillPending(eq(RESUME_ID), any(), anyString(), any())).thenReturn(0);

        reaper.failStalePendingTasks();

        verify(tracker).clearStale(any());
    }

    @Test
    @DisplayName("没有候选时不碰数据库")
    void doesNothingWithoutCandidates() {
        when(tracker.claimedBefore(any(), anyInt())).thenReturn(List.of());

        reaper.failStalePendingTasks();

        verifyNoInteractions(resumeRepository);
        verify(tracker, never()).clearStale(any());
    }

    @Test
    @DisplayName("候选的时间边界是「当前时间减去阈值」")
    void usesTheStaleThresholdAsCutoff() {
        when(tracker.claimedBefore(any(), anyInt())).thenReturn(List.of(RESUME_ID));
        when(resumeRepository.failIfStillPending(eq(RESUME_ID), any(), anyString(), any())).thenReturn(0);

        Instant before = Instant.now();
        reaper.failStalePendingTasks();
        Instant after = Instant.now();

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(tracker).claimedBefore(cutoff.capture(), anyInt());
        assertTrue(cutoff.getValue().isAfter(before.minus(Duration.ofMinutes(10)).minusSeconds(1))
                && cutoff.getValue().isBefore(after.minus(Duration.ofMinutes(10)).plusSeconds(1)),
            "cutoff 应当是当前时间往前 10 分钟");
        // 清理用同一个 cutoff：只删分值仍超时的登记
        verify(tracker).clearStale(cutoff.getValue());
    }
}
