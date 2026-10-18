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
        return limiter.execute(providerKey, userId, () -> delegate.call(withCompatibleOptions(prompt)));
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return Flux.defer(() -> {
            AiRequestLimiter.Lease lease = limiter.acquire(providerKey, userId);
            try {
                return delegate.stream(withCompatibleOptions(prompt)).doFinally(signal -> lease.close());
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

    /**
     * Spring AI 2.0 的模型实现会对 prompt 的选项做强转（例如 {@code OpenAiChatModel} 直接
     * {@code checkcast OpenAiChatOptions}），而 {@code ChatClient} 默认塞进去的是通用
     * {@code DefaultChatOptions}——直接传下去会抛 ClassCastException。
     *
     * <p>在调用边界把「与模型默认选项类型不兼容」的选项替换成模型默认选项；
     * 调用方显式传入的同类型选项原样保留。
     */
    private Prompt withCompatibleOptions(Prompt prompt) {
        ChatOptions defaults = delegate.getDefaultOptions();
        ChatOptions provided = prompt.getOptions();
        if (defaults == null || provided == null || defaults.getClass().isInstance(provided)) {
            return prompt;
        }
        return new Prompt(prompt.getInstructions(), defaults);
    }
}
