package interview.guide.modules.userai.model;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record SaveUserAiConfigRequest(@NotBlank @Size(max = 40) String provider,
    @NotBlank @Size(max = 500) String baseUrl, @NotBlank @Size(max = 160) String model,
    @NotBlank @Size(max = 1000) String apiKey, boolean fallbackToPlatform) {}
