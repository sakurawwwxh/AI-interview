package interview.guide.modules.dify.model;

import java.util.List;
import java.util.Map;

/**
 * Dify 对话请求
 */
public record DifyChatRequest(
    /** 用户问题 */
    String query,
    /** 会话 ID（可选） */
    String conversationId,
    /** 知识库 ID 列表 */
    List<Long> knowledgeBaseIds,
    /** 输入变量 */
    Map<String, String> inputs
) {}
