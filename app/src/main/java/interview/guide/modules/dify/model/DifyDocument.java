package interview.guide.modules.dify.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Dify 文档模型
 *
 * <p>字段与 Dify 知识库 API 的 list documents 响应对齐。
 * 注意：Dify 返回的 created_at / updated_at 为 Unix 时间戳（秒），由客户端解析为 LocalDateTime。
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

    /** 索引状态（waiting / indexing / completed / error 等） */
    private String indexingStatus;

    /** 展示状态（queuing / indexing / paused / error / available / disabled / archived） */
    private String displayStatus;

    /** 字数 */
    private Integer wordCount;

    /** 命中次数 */
    private Integer hitCount;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
