package interview.guide.modules.dify.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Dify 文档模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DifyDocument {
    /** Dify 文档 ID */
    private String id;

    /** 文档名称 */
    private String name;

    /** 文档内容预览 */
    private String contentPreview;

    /** 元数据 */
    private Map<String, Object> metadata;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 索引状态 */
    private String indexingStatus;
}
