package ai.interview.common.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClientResponseException;

import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 重试判定的口径测试。
 *
 * <p>这套判断同时被结构化输出重试与 Stream 消费重试使用：判定过松会白耗 token 并拖住用户，
 * 过紧则把本来能恢复的任务直接判死。这里把两端都钉住。
 */
class ProviderErrorClassifierTest {

    @Test
    @DisplayName("认证失败/请求非法：重试也不会成功")
    void treatsUnauthorizedAsPermanent() {
        assertTrue(ProviderErrorClassifier.isPermanent(httpError(HttpStatus.UNAUTHORIZED)));
        assertTrue(ProviderErrorClassifier.isPermanent(httpError(HttpStatus.BAD_REQUEST)));
        assertTrue(ProviderErrorClassifier.isPermanent(httpError(HttpStatus.FORBIDDEN)));
    }

    @Test
    @DisplayName("429 与 5xx 仍可重试")
    void keepsRateLimitAndServerErrorsRetryable() {
        assertFalse(ProviderErrorClassifier.isPermanent(httpError(HttpStatus.TOO_MANY_REQUESTS)));
        assertFalse(ProviderErrorClassifier.isPermanent(httpError(HttpStatus.INTERNAL_SERVER_ERROR)));
        assertFalse(ProviderErrorClassifier.isPermanent(httpError(HttpStatus.BAD_GATEWAY)));
    }

    @Test
    @DisplayName("状态码藏在 cause 链里也能认出来")
    void looksThroughTheCauseChain() {
        assertTrue(ProviderErrorClassifier.isPermanent(new RuntimeException("调用失败", httpError(HttpStatus.FORBIDDEN))));
        assertFalse(ProviderErrorClassifier.isPermanent(new RuntimeException("调用失败", new TimeoutException("read timeout"))));
    }

    @Test
    @DisplayName("拿不到状态码时退回消息特征匹配")
    void fallsBackToMessageMarkers() {
        assertTrue(ProviderErrorClassifier.isPermanent(new IllegalStateException("HTTP 401 Unauthorized")));
        assertTrue(ProviderErrorClassifier.isPermanent(new IllegalStateException("invalid_api_key")));
        assertFalse(ProviderErrorClassifier.isPermanent(new IllegalStateException("read timeout")));
        assertFalse(ProviderErrorClassifier.isPermanent(new IllegalStateException("connection reset")));
    }

    @Test
    @DisplayName("没有消息、甚至传 null 都不算永久失败")
    void defaultsToRetryableWhenNothingIsKnown() {
        assertFalse(ProviderErrorClassifier.isPermanent(new IllegalStateException()));
        assertFalse(ProviderErrorClassifier.isPermanent(null));
    }

    private static RestClientResponseException httpError(HttpStatusCode status) {
        RestClientResponseException error = mock(RestClientResponseException.class);
        when(error.getStatusCode()).thenReturn(status);
        when(error.getMessage()).thenReturn("HTTP " + status.value());
        return error;
    }
}
