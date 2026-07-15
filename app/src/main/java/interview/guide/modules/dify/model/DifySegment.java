package interview.guide.modules.dify.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Dify 文档分段模型
 *
 * <p>对应 Dify 知识库 API 的 segments 端点响应中的单个分段。
 * 通过分段拼接可重建文档完整内容。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DifySegment {
    /** 分段 ID */
    private String id;

    /** 分段位置（用于排序，从 1 开始） */
    private Integer position;

    /** 分段正文内容 */
    private String content;

    /** 字数 */
    private Integer wordCount;
}
