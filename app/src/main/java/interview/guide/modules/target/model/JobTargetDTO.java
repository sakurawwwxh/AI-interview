package interview.guide.modules.target.model;
import java.time.LocalDateTime;
public record JobTargetDTO(Long id, String title, String company, String jobDescription, boolean active,
                           LocalDateTime createdAt, LocalDateTime updatedAt) {}
