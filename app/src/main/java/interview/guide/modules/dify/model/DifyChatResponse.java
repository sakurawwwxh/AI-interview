package interview.guide.modules.dify.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Dify 对话响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DifyChatResponse {
    /** 回答内容 */
    private String answer;

    /** 会话 ID */
    private String conversationId;

    /** 检索来源 */
    private List<DifyRetrievalSource> retrievalSources;

    /**
     * 检索来源
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DifyRetrievalSource {
        /** 知识库名称 */
        private String datasetName;

        /** 文档名称 */
        private String documentName;

        /** 内容片段 */
        private String content;

        /** 相似度分数 */
        private Double score;
    }
}
