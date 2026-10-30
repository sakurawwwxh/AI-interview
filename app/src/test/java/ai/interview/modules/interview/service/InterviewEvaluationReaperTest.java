package ai.interview.modules.interview.service;

import ai.interview.common.model.AsyncTaskStatus;
import ai.interview.modules.interview.repository.InterviewSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewEvaluationReaperTest {

    @Mock private InterviewSessionRepository sessionRepository;

    private InterviewEvaluationReaper reaper;

    @BeforeEach
    void setUp() {
        reaper = new InterviewEvaluationReaper(sessionRepository);
        ReflectionTestUtils.setField(reaper, "stalePendingMinutes", 15L);
    }

    @Test
    @DisplayName("超时仍是 PENDING 的评估压回 FAILED")
    void flipsStalePendingToFailed() {
        when(sessionRepository.findStalePendingEvaluationIds(eq(AsyncTaskStatus.PENDING), any(), any()))
            .thenReturn(List.of("s-1"));
        when(sessionRepository.failIfStillPendingEvaluation(eq("s-1"), eq(AsyncTaskStatus.FAILED),
            anyString(), eq(AsyncTaskStatus.PENDING))).thenReturn(1);

        reaper.failStalePendingEvaluations();

        verify(sessionRepository).failIfStillPendingEvaluation(eq("s-1"), eq(AsyncTaskStatus.FAILED),
            anyString(), eq(AsyncTaskStatus.PENDING));
    }

    @Test
    @DisplayName("没有候选时不做任何更新")
    void doesNothingWithoutCandidates() {
        when(sessionRepository.findStalePendingEvaluationIds(eq(AsyncTaskStatus.PENDING), any(), any()))
            .thenReturn(List.of());

        reaper.failStalePendingEvaluations();

        verify(sessionRepository, never()).failIfStillPendingEvaluation(any(), any(), anyString(), any());
    }
}
