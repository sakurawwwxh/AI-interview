package interview.guide.modules.dify.model;

import java.time.LocalDateTime;

/**
 * Dify 同步状态 DTO
 */
public record DifySyncStatusDTO(
    Long knowledgeBaseId,
    String name,
    String difyDocumentId,
    DifySyncStatus syncStatus,
    LocalDateTime lastSyncTime,
    String errorMessage
) {}
