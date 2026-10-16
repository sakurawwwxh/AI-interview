package interview.guide.common.config;

import interview.guide.common.constant.AsyncTaskStreamConstants;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Redis Stream 异步消费者配置。
 *
 * <p>所有消费者默认共享同一份安全上限；部署时可通过
 * {@code ASYNC_TASK_MAX_CONCURRENCY} 按实例容量调整。</p>
 */
@Component
@ConfigurationProperties(prefix = "async.task")
public class AsyncTaskProperties {

    private int maxConcurrency = AsyncTaskStreamConstants.DEFAULT_MAX_CONCURRENCY;

    public int getMaxConcurrency() {
        return maxConcurrency;
    }

    public void setMaxConcurrency(int maxConcurrency) {
        this.maxConcurrency = Math.max(1, maxConcurrency);
    }
}
