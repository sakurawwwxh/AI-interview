package interview.guide.modules.dify.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Dify 配置类
 *
 * <p>Dify 有两类 API Key：
 * <ul>
 *   <li>{@code apiKey}（dataset-xxx）：知识库文档同步用，调用 /datasets/* 端点</li>
 *   <li>{@code appApiKey}（app-xxx）：工作流/对话用，调用 /workflows/run、/chat-messages 端点</li>
 * </ul>
 *
 * <p>访问 Dify Cloud（api.dify.ai）若本机使用 Clash 等代理，Java 默认不走系统代理，
 * 需配置 {@code dify.proxy.*}，否则会出现 198.18.x.x Fake-IP 连接超时。
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

    /** 出站 HTTP 代理（可选，用于本机代理访问 Dify Cloud） */
    private ProxyConfig proxy = new ProxyConfig();

    @Data
    public static class SyncConfig {
        /**
         * 是否启用与 Dify 的双向同步。
         * 需要云端知识库同步时设为 true，并确保网络可达（或配置 proxy）。
         */
        private boolean enabled = false;

        /** 同步间隔（毫秒），默认 10 分钟 */
        private long interval = 600000;

        /** 重试次数 */
        private int retryCount = 3;

        /** 重试延迟（毫秒） */
        private long retryDelay = 5000;
    }

    @Data
    public static class ProxyConfig {
        /** 是否启用代理 */
        private boolean enabled = false;

        /** 代理主机，本机 Clash 一般为 127.0.0.1 */
        private String host = "127.0.0.1";

        /**
         * 代理端口。Clash 常见 HTTP 端口为 7890，以客户端设置为准，不要想当然。
         */
        private int port = 7890;

        /** 连接超时（毫秒） */
        private int connectTimeoutMs = 15000;

        /** 是否已配置可用代理 */
        public boolean isConfigured() {
            return enabled && host != null && !host.isBlank() && port > 0;
        }
    }
}
