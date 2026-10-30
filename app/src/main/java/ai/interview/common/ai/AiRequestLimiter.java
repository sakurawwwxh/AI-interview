package ai.interview.common.ai;

import ai.interview.common.exception.BusinessException;
import ai.interview.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * JVM 内共享的模型请求限流器。
 *
 * <p>供应商许可防止同一上游被所有用户合计打满；用户许可防止单个用户独占供应商容量。
 * 许可覆盖完整的同步/流式调用生命周期，流式响应结束后才释放。</p>
 */
@Component
public class AiRequestLimiter {

    private final int providerMaxConcurrency;
    private final int userMaxConcurrency;
    private final Duration acquireTimeout;
    private final ConcurrentHashMap<String, Semaphore> providerPermits = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Semaphore> userPermits = new ConcurrentHashMap<>();

    public AiRequestLimiter(
        @Value("${app.ai.limiter.provider-max-concurrency:8}") int providerMaxConcurrency,
        @Value("${app.ai.limiter.user-max-concurrency:4}") int userMaxConcurrency,
        @Value("${app.ai.limiter.acquire-timeout:60s}") Duration acquireTimeout
    ) {
        this.providerMaxConcurrency = Math.max(1, providerMaxConcurrency);
        this.userMaxConcurrency = Math.max(1, userMaxConcurrency);
        this.acquireTimeout = acquireTimeout.isNegative() || acquireTimeout.isZero()
            ? Duration.ofSeconds(10) : acquireTimeout;
    }

    /** Execute a non-streaming model request under provider and user limits. */
    public <T> T execute(String providerKey, Long userId, Supplier<T> action) {
        Lease lease = acquire(providerKey, userId);
        try {
            return action.get();
        } finally {
            lease.close();
        }
    }

    /** Acquire limits for a streaming request; caller must close the returned lease. */
    public Lease acquire(String providerKey, Long userId) {
        String normalizedProvider = providerKey == null || providerKey.isBlank()
            ? "default" : providerKey.trim().toLowerCase(java.util.Locale.ROOT);
        Semaphore provider = providerPermits.computeIfAbsent(
            normalizedProvider, ignored -> new Semaphore(providerMaxConcurrency));
        boolean providerAcquired = tryAcquire(provider, "模型供应商" + normalizedProvider);
        if (!providerAcquired) {
            throw limited("模型供应商请求并发已满，请稍后重试");
        }

        Semaphore user = null;
        boolean userAcquired = false;
        if (userId != null) {
            user = userPermits.computeIfAbsent(userId, ignored -> new Semaphore(userMaxConcurrency));
            userAcquired = tryAcquire(user, "用户" + userId);
            if (!userAcquired) {
                provider.release();
                throw limited("当前用户模型请求并发已满，请稍后重试");
            }
        }
        return new Lease(provider, user, userAcquired);
    }

    private boolean tryAcquire(Semaphore semaphore, String target) {
        try {
            return semaphore.tryAcquire(acquireTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw limited(target + "限流等待被中断");
        }
    }

    private BusinessException limited(String message) {
        return new BusinessException(ErrorCode.AI_RATE_LIMIT_EXCEEDED, message);
    }

    public final class Lease implements AutoCloseable {
        private final Semaphore provider;
        private final Semaphore user;
        private final boolean userAcquired;
        private boolean closed;

        private Lease(Semaphore provider, Semaphore user, boolean userAcquired) {
            this.provider = provider;
            this.user = user;
            this.userAcquired = userAcquired;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (userAcquired && user != null) {
                user.release();
            }
            provider.release();
        }
    }
}
