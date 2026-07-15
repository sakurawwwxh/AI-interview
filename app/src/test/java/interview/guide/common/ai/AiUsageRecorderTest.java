package interview.guide.common.ai;

import interview.guide.common.exception.BusinessException;
import interview.guide.infrastructure.redis.RedisService;
import interview.guide.modules.user.model.UserEntity;
import interview.guide.modules.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.redisson.api.RAtomicLong;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AI 用量记录器单元测试
 *
 * <p>注意：{@link AiUsageRecorder} 的 {@code checkQuota}/{@code recordUsage}/{@code getUsageStats}
 * 内部调用 {@code UserContext.getCurrentUserId()}，在无 SecurityContext 的单元测试中返回 null。
 * 因此这些测试主要验证 userId 为 null 时的降级行为，以及通过 userId 参数调用的方法。
 */
class AiUsageRecorderTest {

    private final RedisService redisService = mock(RedisService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final RAtomicLong atomicLong = mock(RAtomicLong.class);

    private AiUsageRecorder recorder;

    @BeforeEach
    void setUp() throws Exception {
        when(redisService.getAtomicLong(anyString())).thenReturn(atomicLong);
        when(redisService.increment(anyString())).thenReturn(1L);

        recorder = new AiUsageRecorder(redisService, userRepository);
        setField(recorder, "quotaEnabled", true);
    }

    private void setField(Object target, String field, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Nested
    @DisplayName("checkQuota 配额校验")
    class CheckQuota {

        @Test
        @DisplayName("未登录用户（userId=null）不校验，不抛异常")
        void shouldSkipWhenNoUser() {
            // UserContext.getCurrentUserId() 在无 SecurityContext 时返回 null
            assertDoesNotThrow(() -> recorder.checkQuota());
        }

        @Test
        @DisplayName("配额关闭时不校验")
        void shouldSkipWhenDisabled() throws Exception {
            setField(recorder, "quotaEnabled", false);
            assertDoesNotThrow(() -> recorder.checkQuota());
        }
    }

    @Nested
    @DisplayName("recordUsage 记录用量")
    class RecordUsage {

        @Test
        @DisplayName("未登录用户不记录")
        void shouldNotRecordWhenNoUser() {
            recorder.recordUsage(100, 50, "test");
            verify(redisService, never()).getAtomicLong(anyString());
            verify(redisService, never()).increment(anyString());
        }

        @Test
        @DisplayName("token 为 0 时不记录")
        void shouldNotRecordWhenZeroTokens() {
            recorder.recordUsage(0, 0, "test");
            verify(redisService, never()).getAtomicLong(anyString());
        }
    }

    @Nested
    @DisplayName("按 userId 参数的方法")
    class ByUserIdMethods {

        @Test
        @DisplayName("getDailyUsage 返回正确值")
        void shouldReturnDailyUsage() {
            when(atomicLong.get()).thenReturn(150000L);
            assertEquals(150000L, recorder.getDailyUsage(1L));
        }

        @Test
        @DisplayName("getDailyLimit 从用户实体读取")
        void shouldReturnLimitFromUser() {
            UserEntity user = new UserEntity();
            user.setDailyTokenQuota(300000L);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            assertEquals(300000L, recorder.getDailyLimit(1L));
        }

        @Test
        @DisplayName("getDailyLimit 用户不存在时返回默认值")
        void shouldReturnDefaultLimitWhenUserNotFound() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());
            assertEquals(500000L, recorder.getDailyLimit(99L));
        }

        @Test
        @DisplayName("getMonthlyUsage 返回正确值")
        void shouldReturnMonthlyUsage() {
            when(atomicLong.get()).thenReturn(500000L);
            assertEquals(500000L, recorder.getMonthlyUsage(1L));
        }

        @Test
        @DisplayName("getDailyRequestCount 返回正确值")
        void shouldReturnDailyRequestCount() {
            when(atomicLong.get()).thenReturn(42L);
            assertEquals(42L, recorder.getDailyRequestCount(1L));
        }
    }
}
