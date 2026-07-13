package interview.guide.modules.userai.service;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.user.security.UserContext;
import interview.guide.modules.userai.model.*;
import interview.guide.modules.userai.repository.UserAiConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

@Service @RequiredArgsConstructor
public class UserAiConfigService {
    private final UserAiConfigRepository repository; private final AiKeyCipher cipher; private final UserAiChatClientFactory clientFactory;
    public Optional<UserAiConfigDTO> get() { return repository.findByUserId(UserContext.getCurrentUserIdOrThrow()).map(this::dto); }
    @Transactional public UserAiConfigDTO save(SaveUserAiConfigRequest request) { Long userId = UserContext.getCurrentUserIdOrThrow(); clientFactory.validateBaseUrl(request.baseUrl()); UserAiConfigEntity entity = repository.findByUserId(userId).orElseGet(UserAiConfigEntity::new); entity.setUserId(userId); entity.setProvider(request.provider().trim()); entity.setBaseUrl(request.baseUrl().trim()); entity.setModel(request.model().trim()); entity.setEncryptedApiKey(cipher.encrypt(request.apiKey().trim())); entity.setFallbackToPlatform(request.fallbackToPlatform()); return dto(repository.save(entity)); }
    @Transactional public void delete() { repository.findByUserId(UserContext.getCurrentUserIdOrThrow()).ifPresent(repository::delete); }
    public void test(SaveUserAiConfigRequest request) { clientFactory.create(request.baseUrl(), request.model(), request.apiKey()).prompt("Reply with exactly READY").call().content(); }
    private UserAiConfigDTO dto(UserAiConfigEntity item) { String raw = cipher.decrypt(item.getEncryptedApiKey()); String masked = raw.length() <= 6 ? "******" : raw.substring(0, 3) + "****" + raw.substring(raw.length() - 3); return new UserAiConfigDTO(item.getProvider(), item.getBaseUrl(), item.getModel(), masked, item.isFallbackToPlatform(), item.getUpdatedAt()); }
}
