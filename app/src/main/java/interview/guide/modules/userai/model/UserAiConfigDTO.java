package interview.guide.modules.userai.model;
import java.time.LocalDateTime;
public record UserAiConfigDTO(String provider, String baseUrl, String model, String apiKeyMasked,
                              boolean fallbackToPlatform, LocalDateTime updatedAt) {}
