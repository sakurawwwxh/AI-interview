package ai.interview.modules.resume.listener;

import ai.interview.infrastructure.redis.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 记录「已认领、但还没确认进入消费流程」的简历分析任务。
 *
 * <p>简历进入 PENDING 到消息真正写进 Stream 之间有一个不可恢复的窗口：状态已经提交，
 * 进程被杀或 Redis 瞬时不可用且补偿写失败，任务就再也不会被消费。而重试入口对
 * PENDING 一律拒绝，用户只能等一个不会到来的任务。
 *
 * <p>这里在入队前把简历 ID 以认领时间写进 Redis 有序集合，由
 * {@link ResumeAnalyzeReaper} 把超时后仍是 PENDING 的记录改回 FAILED。
 *
 * <p>不用给 resumes 加时间戳列：生产是 {@code ddl-auto=validate}，加列要连带改部署。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyzeTaskTracker {

    static final String PENDING_KEY = "resume:analyze:pending";

    private final RedisService redisService;

    /** 入队前登记。ZADD 对同一成员是更新分值，重复登记只会把超时点往后推。 */
    public void markQueued(Long resumeId) {
        try {
            redisService.zAdd(PENDING_KEY, Instant.now().toEpochMilli(), resumeId.toString());
        } catch (Exception e) {
            // 追踪失败不能影响主流程：最坏是这条记录不会被自动回收
            log.warn("登记简历分析任务失败，将无法自动回收: resumeId={}, error={}", resumeId, e.getMessage());
        }
    }

    public void clear(Long resumeId) {
        try {
            redisService.zRemove(PENDING_KEY, resumeId.toString());
        } catch (Exception e) {
            log.warn("清理简历分析任务登记失败: resumeId={}, error={}", resumeId, e.getMessage());
        }
    }

    /**
     * 清掉所有在 cutoff 之前登记、且之后没有被刷新过的成员。
     *
     * <p>按分值区间删除而不是逐个删：并发重试会刷新分值，刷新过的成员落在区间外，
     * 不会被这一轮误删——否则刚重新认领的任务会失去回收兜底。
     * 顺带清掉分值很旧的脏成员（无法解析的成员）。
     */
    public void clearStale(Instant cutoff) {
        try {
            redisService.zRemoveRangeByScore(PENDING_KEY, Double.NEGATIVE_INFINITY,
                cutoff.toEpochMilli());
        } catch (Exception e) {
            log.warn("清理过期的简历分析任务登记失败: error={}", e.getMessage());
        }
    }

    /** 取出在 cutoff 之前登记、且尚未被清理的简历 ID。 */
    public Collection<Long> claimedBefore(Instant cutoff, int limit) {
        List<Long> resumeIds = new ArrayList<>();
        for (String member : redisService.zRangeByScore(PENDING_KEY, Double.NEGATIVE_INFINITY,
                cutoff.toEpochMilli(), limit)) {
            try {
                resumeIds.add(Long.valueOf(member));
            } catch (NumberFormatException e) {
                // 一条脏数据（旧版本写入格式、人工塞入）不能把整轮回收卡死
                log.warn("忽略无法解析的简历分析任务登记项: member={}", member);
            }
        }
        return resumeIds;
    }
}
