package interview.guide.modules.dify.scheduler;

import interview.guide.modules.dify.config.DifyConfig;
import interview.guide.modules.dify.service.DifySyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Dify 同步调度器
 * 定时从 Dify 拉取变更
 */
@Component
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
            log.error("定时从 Dify 同步失败: {}", e.getMessage(), e);
        }
    }
}
