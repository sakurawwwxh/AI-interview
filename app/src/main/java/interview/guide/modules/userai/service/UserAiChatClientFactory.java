package interview.guide.modules.userai.service;

import interview.guide.modules.user.security.UserContext;
import interview.guide.modules.userai.model.UserAiConfigEntity;
import interview.guide.modules.userai.repository.UserAiConfigRepository;
import interview.guide.common.ai.AiRequestLimiter;
import interview.guide.common.ai.LimitedChatModel;
import io.micrometer.observation.ObservationRegistry;
import io.netty.channel.ChannelOption;
import lombok.RequiredArgsConstructor;
import reactor.netty.http.client.HttpClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.client.ReactorClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class UserAiChatClientFactory {
    /** A stalled upstream must not permanently consume an asynchronous analysis slot. */
    private static final Duration MODEL_CONNECT_TIMEOUT = Duration.ofSeconds(10);
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
        HttpClient httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.toIntExact(MODEL_CONNECT_TIMEOUT.toMillis()))
            .responseTimeout(MODEL_RESPONSE_TIMEOUT);
        ReactorClientHttpRequestFactory requestFactory = new ReactorClientHttpRequestFactory(httpClient);
        OpenAiApi api = OpenAiApi.builder()
            .baseUrl(normalizedBaseUrl)
            .apiKey(apiKey)
            .restClientBuilder(RestClient.builder().requestFactory(requestFactory))
            .webClientBuilder(WebClient.builder().clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(httpClient)))
            .build();
        OpenAiChatOptions options = OpenAiChatOptions.builder().model(model).temperature(0.2).build();
        OpenAiChatModel chatModel = OpenAiChatModel.builder().openAiApi(api).defaultOptions(options)
            // StructuredOutputInvoker owns cross-provider retry; avoid compounded retries here.
            .retryTemplate(new RetryTemplate(RetryPolicy.withMaxRetries(0)))
            .observationRegistry(ObservationRegistry.NOOP).build();
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
}
