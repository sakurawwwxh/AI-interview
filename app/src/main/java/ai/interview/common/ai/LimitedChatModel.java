package ai.interview.common.ai;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

/** ChatModel decorator that applies shared provider/user backpressure. */
public final class LimitedChatModel implements ChatModel {

    private final ChatModel delegate;
    private final AiRequestLimiter limiter;
    private final String providerKey;
    private final Long userId;

    public LimitedChatModel(ChatModel delegate, AiRequestLimiter limiter, String providerKey, Long userId) {
        this.delegate = delegate;
        this.limiter = limiter;
        this.providerKey = providerKey;
        this.userId = userId;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        return limiter.execute(providerKey, userId, () -> delegate.call(prompt));
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return Flux.defer(() -> {
            AiRequestLimiter.Lease lease = limiter.acquire(providerKey, userId);
            try {
                return delegate.stream(prompt).doFinally(signal -> lease.close());
            } catch (RuntimeException e) {
                lease.close();
                throw e;
            }
        });
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return delegate.getDefaultOptions();
    }
}
