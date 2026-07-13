package interview.guide.modules.target.model;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record JobTargetRequest(
    @NotBlank @Size(max = 120) String title,
    @Size(max = 120) String company,
    @NotBlank @Size(max = 30000) String jobDescription
) {}
