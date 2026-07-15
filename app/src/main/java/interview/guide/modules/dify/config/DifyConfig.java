package interview.guide.modules.dify.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Dify 配置类
 *
 * <p>Dify 有两类 API Key：
 * <ul>
 *   <li>{@code datasetApiKey}（dataset-xxx）：知识库文档同步用，调用 /datasets/* 端点</li>
 *   <li>{@code appApiKey}（app-xxx）：工作流/对话用，调用 /workflows/run、/chat-messages 端点</li>
 * </ul>
 */
@Configuration
@ConfigurationProperties(prefix = "dify")
@Data
public class DifyConfig {
    /** Dify API 基础地址 */
    private String apiUrl = "https://api.dify.ai/v1";

    /** 知识库 API Key（格式: dataset-xxx），用于文档同步 */
    private String apiKey;

    /** 应用 API Key（格式: app-xxx），用于工作流/对话 */
    private String appApiKey;

    /** 默认知识库 ID */
    private String datasetId;

    /** 默认用户标识，用于 chat 和 workflow 调用中的 user 字段 */
    private String defaultUser = "system";

    /** 同步配置 */
    private SyncConfig sync = new SyncConfig();

    @Data
    public static class SyncConfig {
        /**
         * 是否启用与 Dify 的双向同步。
         * 默认关闭：本地若访问不了 api.dify.ai（代理/DNS 伪 IP 等）会周期性超时刷屏。
         * 需要云端知识库同步时，在 application.yml 设置 dify.sync.enabled=true。
         */
        private boolean enabled = false;

        /** 同步间隔（毫秒），默认600000毫秒（10分钟） */
        private long interval = 600000;

        /** 重试次数 */
        private int retryCount = 3;

        /** 重试延迟（毫秒） */
        private long retryDelay = 5000;
    }
}
