package interview.guide.modules.knowledgebase.eval;

import java.util.List;

/**
 * RAG 检索评估的单个用例（ground truth）。
 *
 * <p>{@code expectedMarkers} 是一组可替代的标记串：只要召回的任一文档片段正文里出现其中
 * 任意一条，就认为该用例命中。标记串取自知识库文档中该小节标题/正文的独特文本，
 * 因此可以直接用「片段正文包含标记」判断，无需额外标注 chunk id。
 *
 * @param name           用例名（唯一，也是观测文件名）
 * @param question       模拟的用户提问（措辞与文档标题刻意不同，避免字面回声）
 * @param expectedDoc    期望命中的文档（用于数据完整性校验与报告）
 * @param expectedMarkers 可替代的标记串，命中任一即算命中
 */
public record RetrievalEvalCase(
    String name,
    String question,
    String expectedDoc,
    List<String> expectedMarkers
) {

    public RetrievalEvalCase {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("用例名不能为空");
        }
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("问题不能为空: " + name);
        }
        if (expectedMarkers == null || expectedMarkers.isEmpty()) {
            throw new IllegalArgumentException("expectedMarkers 不能为空: " + name);
        }
        expectedMarkers = List.copyOf(expectedMarkers);
    }

    /** 片段正文是否命中本用例的任一标记（忽略大小写与首尾空白）。 */
    public boolean matches(String chunkText) {
        if (chunkText == null || chunkText.isBlank()) {
            return false;
        }
        String haystack = chunkText.toLowerCase();
        for (String marker : expectedMarkers) {
            if (marker == null || marker.isBlank()) {
                continue;
            }
            if (haystack.contains(marker.toLowerCase().trim())) {
                return true;
            }
        }
        return false;
    }
}
