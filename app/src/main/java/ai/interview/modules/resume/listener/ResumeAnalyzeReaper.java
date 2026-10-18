package ai.interview.modules.resume.listener;

import ai.interview.common.model.AsyncTaskStatus;
import ai.interview.modules.resume.repository.ResumeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;

/**
 * 把「认领后一直没被消费」的简历分析任务改回 FAILED。
 *
 * <p>处理的是入队阶段的缺口：{@code reanalyze} 先提交 PENDING 再写 Stream，
 * 如果进程在两者之间退出，任务永远不会被消费，而重试入口拒绝 PENDING——
 * 简历就永久卡住。这里把超过阈值仍是 PENDING 的记录压回 FAILED，用户可重新触发。
 *
 * <p>只处理登记过的记录（见 {@link AnalyzeTaskTracker}）；条件更新保证不会误伤
 * 已被消费者推进到 PROCESSING 或已经完成的任务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResumeAnalyzeReaper {

    private static final int BATCH_SIZE = 50;

    private final AnalyzeTaskTracker tracker;
    private final ResumeRepository resumeRepository;

    /**
     * 超过这个时长仍是 PENDING，就判定为「消息没入队」。
     *
     * <p>消费者通常几秒内就会转 PROCESSING，但消费者停机时消息会一直排队：
     * 阈值必须显著大于最坏排队时间，否则会把还在队列里的任务误压成 FAILED
     * （用户重试后旧消息恢复执行，会产生两次分析）。
     */
    @Value("${app.resume.stale-pending-minutes:15}")
    private long stalePendingMinutes;

    @Scheduled(fixedDelayString = "${app.resume.reaper-interval-ms:60000}")
    public void failStalePendingTasks() {
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(stalePendingMinutes));
        Collection<Long> candidates = tracker.claimedBefore(cutoff, BATCH_SIZE);
        if (candidates.isEmpty()) {
            return;
        }

        for (Long resumeId : candidates) {
            int flipped = resumeRepository.failIfStillPending(
                resumeId,
                AsyncTaskStatus.FAILED,
                "分析任务未能在预期时间内进入处理队列，请重新发起分析",
                AsyncTaskStatus.PENDING
            );
            if (flipped == 1) {
                log.warn("已回收长期 PENDING 的简历分析任务: resumeId={}", resumeId);
            }
        }
        // 只清仍然超时的登记：期间被并发重试刷新过分值的成员会保留
        tracker.clearStale(cutoff);
    }
}
