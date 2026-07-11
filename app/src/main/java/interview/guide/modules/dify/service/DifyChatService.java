package interview.guide.modules.dify.service;

import interview.guide.modules.dify.client.DifyApiClient;
import interview.guide.modules.dify.config.DifyConfig;
import interview.guide.modules.dify.model.DifyChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dify 对话服务
 * 基于 Dify 平台进行知识库对话
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DifyChatService {

    private final DifyApiClient difyApiClient;
    private final DifyConfig config;

    /**
     * 基于 Dify 进行对话
     *
     * @param query            用户问题
     * @param conversationId   会话 ID（可选）
     * @param knowledgeBaseIds 知识库 ID 列表
     * @return 对话响应
     */
    public DifyChatResponse chat(String query, String conversationId, List<Long> knowledgeBaseIds) {
        // 参数校验
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query 不能为空");
        }

        log.info("Dify 对话: query={}, conversationId={}, kbIds={}", query, conversationId, knowledgeBaseIds);

        // 构建输入参数
        Map<String, String> inputs = new HashMap<>();
        if (knowledgeBaseIds != null && !knowledgeBaseIds.isEmpty()) {
            // 将知识库 ID 列表转换为逗号分隔的字符串
            String kbIdsStr = knowledgeBaseIds.stream()
                .map(String::valueOf)
                .reduce((a, b) -> a + "," + b)
                .orElse("");
            inputs.put("knowledge_base_ids", kbIdsStr);
        }

        // 调用 Dify API
        DifyChatResponse response = difyApiClient.chat(query, conversationId, inputs);

        log.info("Dify 对话完成: conversationId={}, answerLength={}",
            response.getConversationId(),
            response.getAnswer() != null ? response.getAnswer().length() : 0);

        return response;
    }

    /**
     * 流式对话（简化实现，实际需要使用 SSE）
     *
     * @param query          用户问题
     * @param conversationId 会话 ID
     * @return SSE 流
     */
    public Flux<String> chatStream(String query, String conversationId) {
        // 简化实现：使用阻塞式对话，返回单个结果
        // 实际生产环境应使用 Dify 的 streaming API
        return Flux.defer(() -> {
            try {
                DifyChatResponse response = chat(query, conversationId, null);
                return Flux.just(response.getAnswer());
            } catch (Exception e) {
                log.error("流式对话失败: {}", e.getMessage());
                return Flux.error(e);
            }
        });
    }
}
