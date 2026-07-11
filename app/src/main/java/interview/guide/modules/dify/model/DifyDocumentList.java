package interview.guide.modules.dify.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Dify 文档列表响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DifyDocumentList {
    /** 文档列表 */
    private List<DifyDocument> data;

    /** 总数 */
    private int total;

    /** 页码 */
    private int page;

    /** 每页数量 */
    private int limit;

    /** 是否有更多 */
    private boolean hasMore;
}
