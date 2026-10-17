package ai.interview.common.ai;

import ai.interview.common.exception.BusinessException;
import ai.interview.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiRequestLimiterTest {

    @Test
    void providerPermitIsReleasedAfterActionFails() {
        AiRequestLimiter limiter = new AiRequestLimiter(1, 1, Duration.ofMillis(5));

        assertThrows(RuntimeException.class,
            () -> limiter.execute("dashscope", 7L, () -> { throw new RuntimeException("upstream"); }));

        assertEquals("ok", limiter.execute("dashscope", 7L, () -> "ok"));
    }

    @Test
    void providerCapacityReturnsRateLimitErrorWhenBusy() {
        AiRequestLimiter limiter = new AiRequestLimiter(1, 1, Duration.ofMillis(5));
        AiRequestLimiter.Lease lease = limiter.acquire("dashscope", null);
        try {
            BusinessException error = assertThrows(BusinessException.class,
                () -> limiter.acquire("dashscope", null));
            assertEquals(ErrorCode.AI_RATE_LIMIT_EXCEEDED.getCode(), error.getCode());
        } finally {
            lease.close();
        }
    }
}
