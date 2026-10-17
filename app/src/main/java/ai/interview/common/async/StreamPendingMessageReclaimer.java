package ai.interview.common.async;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Redis Stream PEL 滞留消息定时回收器。
 * <p>
 * 消费者宕机/重启后，已投递但未 ACK 的消息会永久滞留在消费者组的 PEL 中
 * （正常消费只读 neverDelivered 消息）。本任务周期性遍历所有注册的
 * {@link AbstractStreamConsumer}，通过 XAUTOCLAIM 将 idle 超过
 * {@link AsyncTaskStreamConstants#PENDING_IDLE_THRESHOLD_MS} 的消息认领回当前实例继续处理，
 * 处理后无论成功失败都会 ACK，防止消息永久滞留。
 */
@Slf4j
@Component
@Profile("!legacy-migration")
@RequiredArgsConstructor
public class StreamPendingMessageReclaimer {

    private final List<AbstractStreamConsumer<?>> streamConsumers;

    /**
     * 定时回收各 Stream 的 PEL 滞留消息，默认每 30 秒一次
     */
    @Scheduled(fixedDelayString = "${app.stream.pending-reclaim-interval-ms:30000}")
    public void reclaimPendingMessages() {
        for (AbstractStreamConsumer<?> consumer : streamConsumers) {
            try {
                consumer.reclaimPendingMessages();
            } catch (Exception e) {
                log.error("回收 Stream 滞留消息失败: stream={}, error={}",
                    consumer.streamKey(), e.getMessage(), e);
            }
        }
    }
}
