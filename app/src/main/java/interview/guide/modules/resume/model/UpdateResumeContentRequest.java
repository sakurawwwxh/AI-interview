package interview.guide.modules.resume.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateResumeContentRequest(
    @NotBlank @Size(max = 120) String title,
    @NotBlank @Size(max = 100000) String content
) {}
