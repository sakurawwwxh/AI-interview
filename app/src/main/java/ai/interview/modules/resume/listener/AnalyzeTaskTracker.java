package ai.interview.modules.resume.listener;

import ai.interview.common.constant.AsyncTaskStreamConstants;
import ai.interview.infrastructure.redis.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
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
    private static final String MESSAGE_KEY_PREFIX = "resume:analyze:message:";
    private static final Duration MESSAGE_TTL = Duration.ofHours(6);

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

    /** 入队成功后记下消息 ID：回收前据此判断任务是否还在队列里。 */
    public void attachMessage(Long resumeId, String messageId) {
        try {
            redisService.set(messageKey(resumeId), messageId, MESSAGE_TTL);
        } catch (Exception e) {
            log.warn("记录简历分析消息失败: resumeId={}, error={}", resumeId, e.getMessage());
        }
    }

    /**
     * 任务是否仍留在 Stream 中（未消费，或已投递未 ACK）。
     *
     * <p>查不到消息 ID 时返回 false——那说明任务压根没入队成功，正是需要回收的情况。
     * 查询本身失败时保守返回 true：宁可晚一轮回收，也不要把还在队列里的任务压成 FAILED。
     */
    public boolean isMessageStillQueued(Long resumeId) {
        try {
            String messageId = redisService.get(messageKey(resumeId));
            return messageId != null
                && redisService.streamContainsMessage(AsyncTaskStreamConstants.RESUME_ANALYZE_STREAM_KEY, messageId);
        } catch (Exception e) {
            log.warn("查询简历分析任务队列状态失败，本轮跳过回收: resumeId={}, error={}", resumeId, e.getMessage());
            return true;
        }
    }

    public void clear(Long resumeId) {
        try {
            redisService.zRemove(PENDING_KEY, resumeId.toString());
            redisService.delete(messageKey(resumeId));
        } catch (Exception e) {
            log.warn("清理简历分析任务登记失败: resumeId={}, error={}", resumeId, e.getMessage());
        }
    }

    /**
     * 只清理「分值仍是超时值」的登记。
     *
     * <p>并发重试会给同一成员写入新分值（新的认领），无条件删除会把新登记一起删掉，
     * 让刚重新发起的任务失去回收兜底。分值已被刷新时保留，连消息绑定也不动。
     */
    public void clear(Long resumeId, Instant cutoff) {
        try {
            boolean removed = redisService.zRemoveIfScoreAtMost(
                PENDING_KEY, resumeId.toString(), cutoff.toEpochMilli());
            if (removed) {
                redisService.delete(messageKey(resumeId));
            }
        } catch (Exception e) {
            log.warn("清理过期的简历分析任务登记失败: resumeId={}, error={}", resumeId, e.getMessage());
        }
    }

    private static String messageKey(Long resumeId) {
        return MESSAGE_KEY_PREFIX + resumeId;
    }

    /** 取出在 cutoff 之前登记、且尚未被清理的简历 ID。 */
    public Collection<Long> claimedBefore(Instant cutoff, int limit) {
        List<Long> resumeIds = new ArrayList<>();
        for (String member : redisService.zRangeByScore(PENDING_KEY, Double.NEGATIVE_INFINITY,
                cutoff.toEpochMilli(), limit)) {
            try {
                resumeIds.add(Long.valueOf(member));
            } catch (NumberFormatException e) {
                // 一条脏数据（旧版本写入格式、人工塞入）不能把整轮回收卡死，直接清掉
                log.warn("清理无法解析的简历分析任务登记项: member={}", member);
                redisService.zRemove(PENDING_KEY, member);
            }
        }
        return resumeIds;
    }
}
