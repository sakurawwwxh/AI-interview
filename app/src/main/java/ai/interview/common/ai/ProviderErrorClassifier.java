package ai.interview.common.ai;

import org.springframework.web.client.RestClientResponseException;

/**
 * 判断模型供应商返回的错误是否「重试也不会成功」。
 *
 * <p>认证失败、请求非法（4xx，429 除外）由请求本身决定，重试只会白耗配额并让用户干等；
 * 429 是限流、5xx 与超时通常是瞬时的，都应当重试。
 *
 * <p>优先按 HTTP 状态码判断（{@link RestClientResponseException} 会沿着 cause 链找），
 * 拿不到状态码时退化为消息特征匹配——有些调用链会把状态码吃掉、只剩一句错误文本。
 * 两个调用方（结构化输出重试、Stream 消费重试）共用这一份判断，避免各写一套后口径跑偏。
 */
public final class ProviderErrorClassifier {

    private static final String[] PERMANENT_MESSAGE_MARKERS = {
        "http 400", "http 401", "http 403", "http 404", "invalid_api_key"
    };

    private ProviderErrorClassifier() {
    }

    public static boolean isPermanent(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof RestClientResponseException responseException) {
                int status = responseException.getStatusCode().value();
                return status >= 400 && status < 500 && status != 429;
            }
            current = current.getCause();
        }

        String message = error == null ? null : error.getMessage();
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase();
        for (String marker : PERMANENT_MESSAGE_MARKERS) {
            if (normalized.contains(marker)) {
                return true;
            }
        }
        return false;
    }
}
