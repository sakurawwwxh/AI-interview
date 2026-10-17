package ai.interview.it;

import ai.interview.common.annotation.RateLimit;
import ai.interview.common.exception.RateLimitExceededException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 限流的集成测试：真实 Redis + 真实 Lua 脚本。
 *
 * <p>补的是一个明确的空白：既有的 {@code RateLimitScriptTest} 只校验**注解元数据**
 * （默认值、策略配置），从未执行过 Lua 脚本；限流的核心语义——配额扣减、令牌桶容量、
 * 窗口过期后回补——此前没有任何测试覆盖过。
 *
 * <p>通过真实的 AOP 代理调用带 {@link RateLimit} 注解的方法，因此覆盖
 * 「注解 → 切面 → Lua → Redis」整条链路。
 *
 * <p>注意：切面生成的真实键带后缀（{@code :value} / {@code :permits}），
 * 清理必须按前缀删除，否则本用例之间会互相污染。
 */
@SpringBootTest
@Import(RateLimitRedisIntegrationTest.ProbeConfiguration.class)
@EnabledIf("ai.interview.it.IntegrationTestDatabase#available")
@DisplayName("限流 · 集成测试（真 Redis + 真 Lua）")
class RateLimitRedisIntegrationTest {

    static class Probe {
        static final int COUNT = 3;

        @RateLimit(count = COUNT, interval = 60, dimensions = {RateLimit.Dimension.GLOBAL})
        public String slidingWindow() {
            return "ok";
        }

        @RateLimit(strategy = RateLimit.Strategy.TOKEN_BUCKET, count = 5, interval = 60,
            dimensions = {RateLimit.Dimension.GLOBAL})
        public String tokenBucket() {
            return "ok";
        }

        /**
         * 短窗口探针：用于验证窗口过期后的惰性回补。
         * <p>
         * 窗口必须显著大于「完成 3 次远程调用」的耗时：这里打的是远端 Redis（单次往返
         * 约 90~500ms）。窗口若取 1 秒，三次调用一旦超时，第 1 次记录就滑出窗口，
         * 此时第 3 次被放行本是正确行为，用例却会随机变红。取 8 秒留出裕度。
         */
        @RateLimit(count = 2, interval = 8, dimensions = {RateLimit.Dimension.GLOBAL})
        public String shortWindow() {
            return "ok";
        }

        /** 与其它用例隔离，专供「脚本已预加载」用例使用。 */
        @RateLimit(count = 2, interval = 60, dimensions = {RateLimit.Dimension.GLOBAL})
        public String isolated() {
            return "ok";
        }
    }

    @TestConfiguration
    static class ProbeConfiguration {
        @Bean
        Probe rateLimitProbe() {
            return new Probe();
        }
    }

    @DynamicPropertySource
    static void isolatedContext(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", IntegrationTestDatabase::jdbcUrl);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.ai.openai.api-key", () -> "it-placeholder-key");
    }

    @Autowired
    private Probe probe;

    @Autowired
    private RedissonClient redissonClient;

    @AfterEach
    void cleanUpKeys() {
        // 切面生成的键带 :value / :permits 后缀，按前缀清理
        redissonClient.getKeys().deleteByPattern("ratelimit:{Probe:*");
    }

    private Object remainingTokens(String method) {
        // Lua 脚本用 SET 写的是纯字符串，读取必须显式用 StringCodec，否则 Redisson 用默认编解码器解不出来
        return redissonClient.getBucket("ratelimit:{Probe:" + method + "}:global:value",
            org.redisson.client.codec.StringCodec.INSTANCE).get();
    }

    @Test
    @DisplayName("滑动窗口：配额内放行，超出后拒绝")
    void slidingWindowRejectsAfterQuota() {
        for (int i = 1; i <= Probe.COUNT; i++) {
            assertDoesNotThrow(() -> probe.slidingWindow(), "第 " + i + " 次应放行");
        }
        assertThrows(RateLimitExceededException.class, () -> probe.slidingWindow(),
            "超出配额的第 " + (Probe.COUNT + 1) + " 次应被拒绝");
    }

    @Test
    @DisplayName("令牌桶：桶容量内连续放行，取空后拒绝")
    void tokenBucketRejectsWhenDrained() {
        for (int i = 1; i <= 5; i++) {
            assertDoesNotThrow(() -> probe.tokenBucket(), "桶容量内第 " + i + " 次应放行");
        }
        assertThrows(RateLimitExceededException.class, () -> probe.tokenBucket(),
            "令牌取空后应被拒绝");
    }

    @Test
    @DisplayName("窗口过期后配额被回补：过了 interval 的下一个请求重新放行")
    void slidingWindowRecoversAfterInterval() throws InterruptedException {
        assertDoesNotThrow(() -> probe.shortWindow());
        assertDoesNotThrow(() -> probe.shortWindow());
        assertThrows(RateLimitExceededException.class, () -> probe.shortWindow(), "窗口内第 3 次应被拒绝");

        // 关键语义：回补是**惰性**的——过期记录在下一个请求到达时才结算，
        // 静置期间 :value 仍是 0。这里把两种状态都记录下来，避免后人误读成"欠账不还"。
        Object quotaWhileRejected = remainingTokens("shortWindow");
        Thread.sleep(8300); // 窗口 interval=8s，多睡 0.3s 保证窗口确实过期
        Object quotaWhileIdle = remainingTokens("shortWindow");
        System.out.printf("[it] shortWindow 剩余配额: 被拒时=%s, 静置 8.3s 后=%s（回补惰性，请求到达才结算）%n",
            quotaWhileRejected, quotaWhileIdle);

        assertDoesNotThrow(() -> probe.shortWindow(), "窗口过期后应重新放行（过期记录未被清理？）");
        assertEquals("1", String.valueOf(remainingTokens("shortWindow")),
            "回补 2 个配额并消耗 1 个后应剩 1 个");
    }

    @Test
    @DisplayName("脚本已通过 SHA 预加载：切面能拿到脚本句柄并成功执行")
    void scriptIsPreloadedAndExecutable() {
        // 若脚本未加载，切面会抛 IllegalStateException("限流脚本未加载")，而不是限流异常
        assertDoesNotThrow(() -> probe.isolated(), "首次调用即应成功（脚本未预加载会抛 IllegalStateException）");
    }
}
