package interview.guide.modules.dify.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Dify 同步日志实体
 */
@Entity
@Table(name = "dify_sync_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DifySyncLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "knowledge_base_id", nullable = false)
    private Long knowledgeBaseId;

    @Column(name = "dify_document_id", length = 100)
    private String difyDocumentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_direction", nullable = false, length = 20)
    private DifySyncDirection syncDirection;

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_action", nullable = false, length = 20)
    private DifySyncAction syncAction;

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status", nullable = false, length = 20)
    private DifySyncStatus syncStatus;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
