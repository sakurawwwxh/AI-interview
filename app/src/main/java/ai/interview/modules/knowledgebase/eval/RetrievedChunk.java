package ai.interview.modules.knowledgebase.eval;

/**
 * 一次检索返回的单个文档片段（用于评估的观测数据）。
 *
 * <p>与 Spring AI {@code org.springframework.ai.document.Document} 解耦，便于把真实运行的
 * 召回结果序列化成 JSON 留给离线回归使用。
 *
 * @param source 来源标识（通常是文件名或 path，取不到时为 null）
 * @param text   片段正文
 * @param score  相似度分数
 */
public record RetrievedChunk(String source, String text, double score) {
}
