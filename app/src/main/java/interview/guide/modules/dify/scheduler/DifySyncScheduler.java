package interview.guide.modules.dify.scheduler;

import interview.guide.modules.dify.config.DifyConfig;
import interview.guide.modules.dify.service.DifySyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Dify 同步调度器
 * 定时从 Dify 拉取变更
 */
@Component
@Profile("!legacy-migration")
@Slf4j
@RequiredArgsConstructor
public class DifySyncScheduler {

    private final DifySyncService difySyncService;
    private final DifyConfig config;

    /**
     * 定时从 Dify 拉取变更
     * 默认每 10 分钟执行一次（600000毫秒）
     */
    @Scheduled(fixedDelayString = "${dify.sync.interval:600000}")
    public void syncFromDify() {
        if (!config.getSync().isEnabled()) {
            log.debug("Dify 同步已禁用，跳过定时任务");
            return;
        }

        log.info("开始定时从 Dify 同步变更");
        try {
            difySyncService.syncFromDify();
            log.info("定时从 Dify 同步完成");
        } catch (Exception e) {
            // 网络不可达（代理/DNS 超时）属环境问题，避免每次打印完整堆栈刷屏
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            if (isLikelyNetworkIssue(msg)) {
                log.warn("定时从 Dify 同步失败（网络不可达，可检查代理或将 dify.sync.enabled=false）: {}", msg);
            } else {
                log.error("定时从 Dify 同步失败: {}", msg, e);
            }
        }
    }

    private static boolean isLikelyNetworkIssue(String message) {
        String m = message.toLowerCase();
        return m.contains("timed out")
            || m.contains("timeout")
            || m.contains("connection refused")
            || m.contains("unknown host")
            || m.contains("i/o error")
            || m.contains("connectexception")
            || m.contains("getsockopt");
    }
}
