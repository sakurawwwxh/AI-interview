package ai.interview.modules.userai.service;

import ai.interview.modules.user.security.UserContext;
import ai.interview.modules.userai.model.UserAiConfigEntity;
import ai.interview.modules.userai.repository.UserAiConfigRepository;
import ai.interview.common.ai.AiRequestLimiter;
import ai.interview.common.ai.LimitedChatModel;
import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.setup.OpenAiSetup;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserAiChatClientFactory {
    /** A stalled upstream must not permanently consume an asynchronous analysis slot. */
    private static final Duration MODEL_RESPONSE_TIMEOUT = Duration.ofSeconds(45);

    private final ChatModel platformChatModel;
    private final UserAiConfigRepository repository;
    private final AiKeyCipher cipher;
    private final AiRequestLimiter requestLimiter;

    public ChatClient forCurrentUser() {
        Long userId = UserContext.getCurrentUserId();
        return userId == null ? platformClient(null) : forUser(userId);
    }

    /** Returns the platform client only when this user explicitly enabled fallback. */
    public ChatClient fallbackForCurrentUser() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return null;
        }
        return repository.findByUserId(userId)
            .filter(UserAiConfigEntity::isFallbackToPlatform)
            .map(ignored -> platformClient(userId))
            .orElse(null);
    }

    public ChatClient forUser(Long userId) {
        return repository.findByUserId(userId).map(config -> build(config, userId))
            .orElseGet(() -> platformClient(userId));
    }

    public ChatClient create(String baseUrl, String model, String apiKey) {
        return create(baseUrl, model, apiKey, UserContext.getCurrentUserId());
    }

    private ChatClient build(UserAiConfigEntity config, Long userId) {
        return create(config.getBaseUrl(), config.getModel(), cipher.decrypt(config.getEncryptedApiKey()), userId);
    }

    private ChatClient create(String baseUrl, String model, String apiKey, Long userId) {
        validateBaseUrl(baseUrl);
        String normalizedBaseUrl = trimBaseUrl(baseUrl);
        String apiBaseUrl = withApiVersion(normalizedBaseUrl);
        // Spring AI 2.0 起底层改成官方 OpenAI SDK：客户端由 OpenAiSetup 构造，
        // 超时与重试直接透传，不再需要手工拼 RestClient / WebClient。
        // maxRetries=0：跨供应商重试由 StructuredOutputInvoker 统一负责，避免重复叠加。
        OpenAIClient syncClient = OpenAiSetup.setupSyncClient(
            apiBaseUrl, apiKey, null, null, null, null,
            false, false, model, MODEL_RESPONSE_TIMEOUT, 0, null, Map.of(),
            ObservationRegistry.NOOP, null, List.of());
        OpenAIClientAsync asyncClient = OpenAiSetup.setupAsyncClient(
            apiBaseUrl, apiKey, null, null, null, null,
            false, false, model, MODEL_RESPONSE_TIMEOUT, 0, null, Map.of(),
            ObservationRegistry.NOOP, null, List.of());
        OpenAiChatOptions options = OpenAiChatOptions.builder().model(model).temperature(0.2).build();
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
            .openAiClient(syncClient)
            .openAiClientAsync(asyncClient)
            .options(options)
            .observationRegistry(ObservationRegistry.NOOP)
            .build();
        return ChatClient.create(new LimitedChatModel(chatModel, requestLimiter, normalizedBaseUrl, userId));
    }

    private ChatClient platformClient(Long userId) {
        return ChatClient.create(new LimitedChatModel(platformChatModel, requestLimiter, "platform", userId));
    }
    public void validateBaseUrl(String baseUrl) {
        try {
            var uri = UriComponentsBuilder.fromUriString(baseUrl).build().toUri();
            if ("https".equalsIgnoreCase(uri.getScheme())) return;
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(java.util.Locale.ROOT);
            if ("http".equalsIgnoreCase(uri.getScheme()) && java.util.Set.of("localhost", "127.0.0.1", "::1", "host.docker.internal").contains(host)) return;
            throw new IllegalArgumentException("Only HTTPS or an explicitly local model endpoint is allowed");
        }
        catch (Exception e) { throw new IllegalArgumentException("Invalid AI service address"); }
    }
    private String trimBaseUrl(String url) { return url.trim().replaceAll("/+$", ""); }

    /**
     * Spring AI 2.0 底层换成官方 OpenAI SDK 后，baseUrl 必须是带版本的根路径
     * （例如 {@code https://api.openai.com/v1}）；旧版会自己拼 {@code /v1}。
     * 用户填的多是不带版本的形式，这里统一补齐，两种写法都能用。
     */
    private String withApiVersion(String url) {
        return url.endsWith("/v1") ? url : url + "/v1";
    }
}
