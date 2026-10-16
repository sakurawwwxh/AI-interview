package interview.guide.common.aspect;

import interview.guide.common.annotation.RateLimit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 限流注解单元测试
 */
@DisplayName("限流注解单元测试")
class RateLimitScriptTest {

    @Test
    @DisplayName("验证注解元配置")
    void testAnnotationMetadata() {
        assertEquals(RetentionPolicy.RUNTIME,
                RateLimit.class.getAnnotation(Retention.class).value());
        assertArrayEquals(new ElementType[]{ElementType.METHOD},
                RateLimit.class.getAnnotation(Target.class).value());
        assertEquals(3, RateLimit.Dimension.values().length);
        assertEquals(5, RateLimit.TimeUnit.values().length);
        assertEquals(2, RateLimit.Strategy.values().length);
    }

    @Test
    @DisplayName("验证注解默认值")
    void testDefaultValues() throws NoSuchMethodException {
        RateLimit ann = TestClass.class.getMethod("defaultMethod").getAnnotation(RateLimit.class);

        assertArrayEquals(new RateLimit.Dimension[]{RateLimit.Dimension.GLOBAL}, ann.dimensions());
        assertEquals(0, ann.timeout());
        assertEquals(1, ann.interval());
        assertEquals(RateLimit.TimeUnit.SECONDS, ann.timeUnit());
        assertEquals("", ann.fallback());
        assertEquals(RateLimit.Strategy.SLIDING_WINDOW, ann.strategy());
    }

    @Test
    @DisplayName("验证自定义值")
    void testCustomValues() throws NoSuchMethodException {
        RateLimit ann = TestClass.class.getMethod("customMethod").getAnnotation(RateLimit.class);

        assertEquals(100.0, ann.count(), 0.001);
        assertEquals(2, ann.timeout());
        assertEquals(2, ann.interval());
        assertEquals(RateLimit.TimeUnit.SECONDS, ann.timeUnit());
        assertEquals("fallback", ann.fallback());
        assertEquals(3, ann.dimensions().length);
    }

    @Test
    @DisplayName("验证令牌桶策略配置")
    void testTokenBucketStrategy() throws NoSuchMethodException {
        RateLimit ann = TestClass.class.getMethod("tokenBucketMethod").getAnnotation(RateLimit.class);

        assertEquals(RateLimit.Strategy.TOKEN_BUCKET, ann.strategy());
        assertEquals(10.0, ann.count(), 0.001);
        assertArrayEquals(new RateLimit.Dimension[]{RateLimit.Dimension.GLOBAL, RateLimit.Dimension.IP},
                ann.dimensions());
    }

    static class TestClass {
        @RateLimit(count = 10)
        public void defaultMethod() {}

        @RateLimit(dimensions = {RateLimit.Dimension.GLOBAL, RateLimit.Dimension.IP, RateLimit.Dimension.USER},
                count = 100, interval = 2, timeout = 2, timeUnit = RateLimit.TimeUnit.SECONDS, fallback = "fallback")
        public void customMethod() {}

        @RateLimit(dimensions = {RateLimit.Dimension.GLOBAL, RateLimit.Dimension.IP},
                count = 10, strategy = RateLimit.Strategy.TOKEN_BUCKET)
        public void tokenBucketMethod() {}
    }
}
