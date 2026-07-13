package interview.guide.modules.userai.service;

import interview.guide.modules.user.security.UserContext;
import interview.guide.modules.userai.model.UserAiConfigEntity;
import interview.guide.modules.userai.repository.UserAiConfigRepository;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@RequiredArgsConstructor
public class UserAiChatClientFactory {
    private final ChatClient.Builder platformBuilder;
    private final UserAiConfigRepository repository;
    private final AiKeyCipher cipher;

    public ChatClient forCurrentUser() {
        Long userId = UserContext.getCurrentUserId();
        return userId == null ? platformBuilder.build() : forUser(userId);
    }

    /** Returns the platform client only when this user explicitly enabled fallback. */
    public ChatClient fallbackForCurrentUser() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return null;
        }
        return repository.findByUserId(userId)
            .filter(UserAiConfigEntity::isFallbackToPlatform)
            .map(ignored -> platformBuilder.build())
            .orElse(null);
    }

    public ChatClient forUser(Long userId) {
        return repository.findByUserId(userId).map(this::build).orElseGet(platformBuilder::build);
    }

    public ChatClient create(String baseUrl, String model, String apiKey) {
        validateBaseUrl(baseUrl);
        OpenAiApi api = OpenAiApi.builder().baseUrl(trimBaseUrl(baseUrl)).apiKey(apiKey).build();
        OpenAiChatOptions options = OpenAiChatOptions.builder().model(model).temperature(0.2).build();
        OpenAiChatModel chatModel = OpenAiChatModel.builder().openAiApi(api).defaultOptions(options)
            .retryTemplate(new RetryTemplate()).observationRegistry(ObservationRegistry.NOOP).build();
        return ChatClient.create(chatModel);
    }

    private ChatClient build(UserAiConfigEntity config) { return create(config.getBaseUrl(), config.getModel(), cipher.decrypt(config.getEncryptedApiKey())); }
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
