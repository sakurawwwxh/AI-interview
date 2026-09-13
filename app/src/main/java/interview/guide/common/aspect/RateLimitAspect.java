package interview.guide.common.aspect;

import interview.guide.common.annotation.RateLimit;
import interview.guide.common.exception.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 限流 AOP 切面
 * 基于滑动时间窗口 / 令牌桶实现的多维度原子限流
 * 根据 {@link RateLimit#strategy()} 选择对应算法的 Lua 脚本执行
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final RedissonClient redissonClient;

    /**
     * 滑动窗口 Lua 脚本
     */
    private static String SLIDING_WINDOW_SCRIPT;
    /**
     * 令牌桶 Lua 脚本
     */
    private static String TOKEN_BUCKET_SCRIPT;

    /**
     * 各策略脚本预加载后的 SHA1 缓存
     */
    private final Map<RateLimit.Strategy, String> scriptShaCache = new EnumMap<>(RateLimit.Strategy.class);

    static {
        try {
            SLIDING_WINDOW_SCRIPT = loadScript("scripts/rate_limit.lua");
            TOKEN_BUCKET_SCRIPT = loadScript("scripts/token_bucket.lua");
        } catch (IOException e) {
            throw new RuntimeException("加载限流 Lua 脚本失败", e);
        }
    }

    private static String loadScript(String path) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        return new String(resource.getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    /**
     * 初始化：预加载脚本到 Redis 提高性能
     */
    @jakarta.annotation.PostConstruct
    public void init() {
        RScript script = redissonClient.getScript(StringCodec.INSTANCE);
        scriptShaCache.put(RateLimit.Strategy.SLIDING_WINDOW,
                script.scriptLoad(SLIDING_WINDOW_SCRIPT));
        scriptShaCache.put(RateLimit.Strategy.TOKEN_BUCKET,
                script.scriptLoad(TOKEN_BUCKET_SCRIPT));
        log.info("限流 Lua 脚本加载完成, SHA1: {}", scriptShaCache);
    }

    /**
     * 环绕通知：拦截带 @RateLimit 注解的方法
     */
    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String className = method.getDeclaringClass().getSimpleName();
        String methodName = method.getName();

        // 1. 计算时间窗口（毫秒）
        long intervalMs = calculateIntervalMs(rateLimit.interval(), rateLimit.timeUnit());

        // 2. 根据配置维度动态生成 Redis Keys
        List<String> keys = generateKeys(className, methodName, rateLimit.dimensions());

        // 3. 根据限流策略选择对应的 Lua 脚本执行原子限流
        // 使用 StringCodec 确保参数正确传递为字符串
        RScript script = redissonClient.getScript(StringCodec.INSTANCE);
        String scriptSha = scriptShaCache.get(rateLimit.strategy());
        if (scriptSha == null) {
            throw new IllegalStateException("限流脚本未加载: " + rateLimit.strategy());
        }

        // 准备参数（两种策略脚本共用同一签名）
        List<Object> keysList = new ArrayList<>(keys);
        Object[] args = {
                String.valueOf(System.currentTimeMillis()), // ARGV[1]: 当前时间戳
                String.valueOf(1),                          // ARGV[2]: 申请令牌数（默认1个）
                String.valueOf(intervalMs),                 // ARGV[3]: 时间窗口
                String.valueOf(rateLimit.count()),          // ARGV[4]: 最大令牌数/桶容量
                UUID.randomUUID().toString()               // ARGV[5]: 请求唯一标识
        };

        Object resultObj = script.evalSha(
                RScript.Mode.READ_WRITE,
                scriptSha,
                RScript.ReturnType.VALUE,
                keysList,
                args
        );

        // 将结果转换为 Long
        Long result = convertToLong(resultObj);

        // 4. 处理限流结果
        if (result == null || result == 0) {
            return handleRateLimitExceeded(joinPoint, rateLimit, keys);
        }

        // 5. 执行原方法
        return joinPoint.proceed();
    }

    /**
     * 计算时间窗口毫秒数
     */
    private long calculateIntervalMs(long interval, RateLimit.TimeUnit unit) {
        return switch (unit) {
            case MILLISECONDS -> interval;
            case SECONDS -> interval * 1000;
            case MINUTES -> interval * 60 * 1000;
            case HOURS -> interval * 3600 * 1000;
            case DAYS -> interval * 86400 * 1000;
        };
    }

    /**
     * 将结果对象安全转换为 Long
     */
    private Long convertToLong(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof Long) {
            return (Long) obj;
        } else if (obj instanceof Integer) {
            return ((Integer) obj).longValue();
        } else if (obj instanceof Short) {
            return ((Short) obj).longValue();
        } else if (obj instanceof Byte) {
            return ((Byte) obj).longValue();
        } else if (obj instanceof String) {
            try {
                return Long.parseLong((String) obj);
            } catch (NumberFormatException e) {
                log.warn("无法将字符串转换为Long: {}", obj);
                return null;
            }
        }
        log.warn("不支持的对象类型转换为Long: {}", obj.getClass().getName());
        return null;
    }

    /**
     * 生成限流键列表
     */
    private List<String> generateKeys(String className, String methodName, RateLimit.Dimension[] dimensions) {
        List<String> keys = new ArrayList<>();
        // 使用 {} 包含类名和方法名作为 Hash Tag，确保该方法的所有限流 Key 落在同一个 Redis Slot
        // 从而适配 Redis Cluster 模式
        String hashTag = "{" + className + ":" + methodName + "}";
        String keyPrefix = "ratelimit:" + hashTag;

        for (RateLimit.Dimension dimension : dimensions) {
            switch (dimension) {
                case GLOBAL -> keys.add(keyPrefix + ":global");
                case IP -> keys.add(keyPrefix + ":ip:" + getClientIp());
                case USER -> keys.add(keyPrefix + ":user:" + getCurrentUserId());
            }
        }

        return keys;
    }

    /**
     * 处理限流超出情况
     */
    private Object handleRateLimitExceeded(ProceedingJoinPoint joinPoint, RateLimit rateLimit, List<String> keys)
            throws Throwable {
        String methodName = joinPoint.getSignature().getName();

        // 如果配置了降级方法，则调用降级方法
        if (rateLimit.fallback() != null && !rateLimit.fallback().isEmpty()) {
            try {
                Method fallbackMethod = findFallbackMethod(joinPoint, rateLimit.fallback());
                if (fallbackMethod != null) {
                    log.debug("限流触发，执行降级方法: {}.{} -> {}",
                            joinPoint.getTarget().getClass().getSimpleName(),
                            methodName,
                            rateLimit.fallback());
                    // 如果降级方法有参数，传入原方法的参数
                    if (fallbackMethod.getParameterCount() > 0) {
                        return fallbackMethod.invoke(joinPoint.getTarget(), joinPoint.getArgs());
                    } else {
                        return fallbackMethod.invoke(joinPoint.getTarget());
                    }
                }
            } catch (Exception e) {
                log.error("降级方法执行失败: {}", rateLimit.fallback(), e);
            }
        }

        // 没有降级方法或降级失败，抛出限流异常
        log.debug("限流触发，拒绝请求: keys={}, count={} per {} {}",
                keys, rateLimit.count(), rateLimit.interval(), rateLimit.timeUnit());
        throw new RateLimitExceededException("请求过于频繁，请稍后再试");
    }

    /**
     * 查找降级方法
     * 优先查找与原方法参数列表完全一致的方法，找不到则查找无参方法
     */
    private Method findFallbackMethod(ProceedingJoinPoint joinPoint, String fallbackName) {
        Class<?> targetClass = joinPoint.getTarget().getClass();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Class<?>[] parameterTypes = signature.getParameterTypes();

        try {
            // 1. 尝试查找同参数列表的方法
            Method method = targetClass.getDeclaredMethod(fallbackName, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            // 2. 尝试查找无参方法
            try {
                Method method = targetClass.getDeclaredMethod(fallbackName);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ex) {
                log.warn("未找到降级方法: {}.{} (需无参或参数列表一致)",
                        targetClass.getSimpleName(), fallbackName);
                return null;
            }
        }
    }

    /**
     * 获取客户端真实 IP
     * 处理 X-Forwarded-For 头，支持代理服务器场景
     */
    private String getClientIp() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "unknown";
        }

        HttpServletRequest request = attributes.getRequest();
        String ip = request.getHeader("X-Forwarded-For");

        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }

        // 处理多个 IP 的情况（X-Forwarded-For 可能包含多个 IP）
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }

        return ip != null ? ip : "unknown";
    }

    /**
     * 获取当前用户 ID
     * 从 SecurityContext（JWT 认证后写入）获取，不信任外部 X-User-Id 头
     */
    private String getCurrentUserId() {
        Long userId = interview.guide.modules.user.security.UserContext.getCurrentUserId();
        return userId != null ? userId.toString() : "anonymous";
    }
}
