package interview.guide.modules.knowledgebase.model;

import java.time.LocalDateTime;

/**
 * 知识库列表项DTO
 */
public record KnowledgeBaseListItemDTO(
    Long id,
    String name,
    String originalFilename,
    Long fileSize,
    String contentType,
    LocalDateTime uploadedAt,
    LocalDateTime lastAccessedAt,
    Integer accessCount,
    Integer questionCount
) {
    public static KnowledgeBaseListItemDTO fromEntity(KnowledgeBaseEntity entity) {
        return new KnowledgeBaseListItemDTO(
            entity.getId(),
            entity.getName(),
            entity.getOriginalFilename(),
            entity.getFileSize(),
            entity.getContentType(),
            entity.getUploadedAt(),
            entity.getLastAccessedAt(),
            entity.getAccessCount(),
            entity.getQuestionCount()
        );
    }
}

