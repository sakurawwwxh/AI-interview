package interview.guide.infrastructure.redis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.stream.StreamReadGroupArgs;
import org.redisson.client.codec.Codec;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/**
 * Stream 消费读路径的兜底测试。
 *
 * <p>Redisson 4.0.0 的 {@code RedissonStream.readGroup} 把 XREADGROUP 的响应直接强转成 Map，
 * 空响应偶发被解码成 {@code Collections.emptyList} 时就会抛
 * {@code EmptyList cannot be cast to Map}。这里锁定我们的处理方式：
 * 视作「本轮没读到消息」，而不是把异常抛给消费循环（否则每轮打一条 ERROR 噪声）。
 */
class RedisServiceStreamConsumeTest {

    @Test
    @DisplayName("Redisson 空响应强转失败时按「本轮无消息」处理，不上抛给消费循环")
    void treatsRedissonEmptyReplyCastFailureAsNoMessages() {
        RedissonClient client = mock(RedissonClient.class);
        @SuppressWarnings("unchecked")
        RStream<String, String> stream = mock(RStream.class);
        doReturn(stream).when(client).getStream(anyString(), any(Codec.class));
        doThrow(new ClassCastException(
            "class java.util.Collections$EmptyList cannot be cast to class java.util.Map"))
            .when(stream).readGroup(anyString(), anyString(), any(StreamReadGroupArgs.class));

        boolean processed = new RedisService(client).streamConsumeMessages(
            "resume:analyze:stream", "analyze-group", "consumer-1", 10, 1000,
            (messageId, data) -> {
                throw new AssertionError("异常场景下不应处理任何消息");
            });

        assertFalse(processed);
    }
}
