package interview.guide.modules.dify.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Dify 配置类
 */
@Configuration
@ConfigurationProperties(prefix = "dify")
@Data
public class DifyConfig {
    /** Dify API 基础地址 */
    private String apiUrl = "http://your-dify-host:80/v1";

    /** API Key */
    private String apiKey;

    /** 默认知识库 ID */
    private String datasetId;

    /** 默认用户标识，用于 chat 和 workflow 调用中的 user 字段 */
    private String defaultUser = "system";

    /** 同步配置 */
    private SyncConfig sync = new SyncConfig();

    @Data
    public static class SyncConfig {
        /** 是否启用同步 */
        private boolean enabled = true;

        /** 同步间隔（毫秒），默认600000毫秒（10分钟） */
        private long interval = 600000;

        /** 重试次数 */
        private int retryCount = 3;

        /** 重试延迟（毫秒） */
        private long retryDelay = 5000;
    }
}
