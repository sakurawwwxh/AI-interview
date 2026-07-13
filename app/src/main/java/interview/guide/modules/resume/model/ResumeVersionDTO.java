package interview.guide.modules.resume.model;

import java.time.LocalDateTime;

public record ResumeVersionDTO(Long id, int versionNumber, String title, String content,
                               String source, LocalDateTime createdAt, boolean current) {}
