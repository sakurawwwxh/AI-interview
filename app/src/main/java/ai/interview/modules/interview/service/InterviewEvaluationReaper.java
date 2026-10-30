package ai.interview.modules.interview.service;

import ai.interview.common.model.AsyncTaskStatus;
import ai.interview.modules.interview.repository.InterviewSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 回收「交卷后一直没被消费」的面试评估任务。
 *
 * <p>交卷时先把 evaluate_status 写成 PENDING、再写 Stream；进程在这两步之间退出时任务不会
 * 被消费，而重试入口对 PENDING 一律拒绝、报告接口也拒绝出分，会话就永久卡在「评估中」。
 * 这里把超过阈值仍是 PENDING 的记录改成 FAILED，让用户能一键重试。
 *
 * <p>只压 PENDING：已被消费者推进到 PROCESSING 的任务由 Stream 的 PEL 回收负责。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InterviewEvaluationReaper {

    private static final int BATCH_SIZE = 50;

    private final InterviewSessionRepository sessionRepository;

    /** 消费者通常几秒内就会把 PENDING 推进到 PROCESSING；超过这个时长视为消息没入队。 */
    @Value("${app.interview.evaluation.stale-pending-minutes:15}")
    private long stalePendingMinutes;

    @Scheduled(fixedDelayString = "${app.interview.evaluation.reaper-interval-ms:60000}")
    public void failStalePendingEvaluations() {
        LocalDateTime cutoff = LocalDateTime.now().minus(Duration.ofMinutes(stalePendingMinutes));
        List<String> sessionIds = sessionRepository.findStalePendingEvaluationIds(
            AsyncTaskStatus.PENDING, cutoff, PageRequest.of(0, BATCH_SIZE));
        for (String sessionId : sessionIds) {
            int flipped = sessionRepository.failIfStillPendingEvaluation(
                sessionId,
                AsyncTaskStatus.FAILED,
                "评估任务未能在预期时间内进入处理队列，请重新发起评估",
                AsyncTaskStatus.PENDING
            );
            if (flipped == 1) {
                log.warn("已回收长期 PENDING 的面试评估任务: sessionId={}", sessionId);
            }
        }
    }
}
