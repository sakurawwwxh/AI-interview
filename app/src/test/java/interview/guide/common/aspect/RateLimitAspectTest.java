package interview.guide.common.aspect;

import interview.guide.common.annotation.RateLimit;
import interview.guide.common.exception.RateLimitExceededException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;
import org.redisson.client.codec.Codec;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 限流切面的故障语义测试。
 * <p>
 * 这里锁定的是两条容易互相破坏的边界：Redis 故障必须放行（否则一次抖动会把
 * 受保护接口整体打成 5xx），而配额耗尽必须照旧拒绝（否则放行改动静默地废掉了限流）。
 */
class RateLimitAspectTest {

    private static final String BUSINESS_RESULT = "业务结果";

    @RateLimit(dimensions = {RateLimit.Dimension.GLOBAL}, count = 5)
    void limitedEndpoint() {
    }

    @Test
    @DisplayName("Redis 故障时放行原方法，不把接口变成 5xx")
    void failsOpenWhenRedisIsUnavailable() throws Throwable {
        // 模拟跨机房链路下的读超时：与线上日志中的 Read timed out 同类
        RateLimitAspect aspect = aspectWhoseRedisThrows(
            new RedisException("Unable to write command into connection! ... Read timed out"));
        ProceedingJoinPoint joinPoint = joinPoint();

        Object result = aspect.around(joinPoint, annotation());

        assertEquals(BUSINESS_RESULT, result);
        verify(joinPoint).proceed();
    }

    @Test
    @DisplayName("预加载失败不阻止启动，首个请求按需加载脚本并正常放行")
    void loadsScriptOnDemandWhenPreloadFailed() throws Throwable {
        // 模拟启动阶段 Redis 响应超时：init() 的 scriptLoad 抛错
        AtomicInteger scriptLoadCalls = new AtomicInteger();
        RScript script = mock(RScript.class, invocation -> switch (invocation.getMethod().getName()) {
            case "scriptLoad" -> {
                if (scriptLoadCalls.getAndIncrement() == 0) {
                    throw new RedisException(
                        "Redis server response timeout (3000 ms) occurred after 4 of 4 retry attempts");
                }
                yield "loaded-on-demand-sha1";
            }
            case "evalSha" -> 1L;
            default -> null;
        });
        RedissonClient client = mock(RedissonClient.class);
        when(client.getScript(any(Codec.class))).thenReturn(script);
        RateLimitAspect aspect = new RateLimitAspect(client);

        assertDoesNotThrow(aspect::init, "预加载失败不应让 Bean 初始化失败（否则应用起不来）");

        assertEquals(BUSINESS_RESULT, aspect.around(joinPoint(), annotation()));
        assertEquals(2, scriptLoadCalls.get(), "预加载失败后首个请求应补齐脚本加载");
    }

    @Test
    @DisplayName("配额耗尽时仍然拒绝，不因放行改动而失效")
    void stillRejectsWhenQuotaIsExhausted() throws Throwable {
        RateLimitAspect aspect = aspectWhoseEvalReturns(0L);
        ProceedingJoinPoint joinPoint = joinPoint();

        assertThrows(RateLimitExceededException.class, () -> aspect.around(joinPoint, annotation()));
        verify(joinPoint, never()).proceed();
    }

    @Test
    @DisplayName("配额充足时正常执行原方法")
    void proceedsWhenQuotaIsAvailable() throws Throwable {
        RateLimitAspect aspect = aspectWhoseEvalReturns(1L);
        ProceedingJoinPoint joinPoint = joinPoint();

        assertEquals(BUSINESS_RESULT, aspect.around(joinPoint, annotation()));
        verify(joinPoint).proceed();
    }

    private RateLimitAspect aspectWhoseRedisThrows(RuntimeException failure) {
        RScript script = mock(RScript.class, invocation -> {
            if ("scriptLoad".equals(invocation.getMethod().getName())) {
                return "fake-sha1";
            }
            throw failure;
        });
        return aspectFor(script);
    }

    private RateLimitAspect aspectWhoseEvalReturns(Object evalResult) {
        RScript script = mock(RScript.class, invocation -> switch (invocation.getMethod().getName()) {
            case "scriptLoad" -> "fake-sha1";
            case "evalSha" -> evalResult;
            default -> null;
        });
        return aspectFor(script);
    }

    private RateLimitAspect aspectFor(RScript script) {
        RedissonClient client = mock(RedissonClient.class);
        when(client.getScript(any(Codec.class))).thenReturn(script);
        RateLimitAspect aspect = new RateLimitAspect(client);
        aspect.init();
        return aspect;
    }

    private ProceedingJoinPoint joinPoint() throws Throwable {
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(getClass().getDeclaredMethod("limitedEndpoint"));
        when(signature.getParameterTypes()).thenReturn(new Class<?>[0]);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getTarget()).thenReturn(this);
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        when(joinPoint.proceed()).thenReturn(BUSINESS_RESULT);
        return joinPoint;
    }

    private RateLimit annotation() throws NoSuchMethodException {
        return getClass().getDeclaredMethod("limitedEndpoint").getAnnotation(RateLimit.class);
    }
}
