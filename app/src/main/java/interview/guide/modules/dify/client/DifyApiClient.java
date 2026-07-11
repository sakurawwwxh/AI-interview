package interview.guide.modules.dify.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import interview.guide.modules.dify.config.DifyConfig;
import interview.guide.modules.dify.exception.DifyApiException;
import interview.guide.modules.dify.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Dify API 客户端
 * 封装所有与 Dify 平台的交互
 */
@Service
@Slf4j
public class DifyApiClient {

    private final RestTemplate restTemplate;
    private final DifyConfig config;
    private final ObjectMapper objectMapper;

    public DifyApiClient(RestTemplateBuilder restTemplateBuilder,
                         DifyConfig config,
                         ObjectMapper objectMapper) {
        this.restTemplate = restTemplateBuilder.build();
        this.config = config;
        this.objectMapper = objectMapper;
    }

    /**
     * 创建文档到 Dify 知识库
     *
     * @param datasetId 知识库 ID
     * @param text      文档内容
     * @param metadata  元数据
     * @return Dify 文档 ID
     */
    @Retryable(
        value = {DifyApiException.class},
        maxExpression = "${dify.sync.retry-count:3}",
        backoff = @Backoff(delayExpression = "${dify.sync.retry-delay:5000}")
    )
    public String createDocument(String datasetId, String text, Map<String, Object> metadata) {
        // 参数校验
        if (datasetId == null || datasetId.isBlank()) {
            throw new IllegalArgumentException("datasetId 不能为空");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text 不能为空");
        }

        log.info("创建 Dify 文档: datasetId={}, textLength={}", datasetId, text.length());

        String url = config.getApiUrl() + "/datasets/" + datasetId + "/documents";

        HttpHeaders headers = createHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("text", text);
        body.put("metadata", metadata != null ? metadata : Map.of());

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                url, HttpMethod.POST, request, JsonNode.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String documentId = response.getBody().path("document").path("id").asText();
                log.info("Dify 文档创建成功: documentId={}", documentId);
                return documentId;
            } else {
                throw new DifyApiException("创建文档失败: " + response.getStatusCode());
            }
        } catch (DifyApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用 Dify API 失败: {}", e.getMessage());
            throw new DifyApiException("创建文档失败: " + e.getMessage(), e);
        }
    }

    /**
     * 更新 Dify 文档
     */
    @Retryable(
        value = {DifyApiException.class},
        maxExpression = "${dify.sync.retry-count:3}",
        backoff = @Backoff(delayExpression = "${dify.sync.retry-delay:5000}")
    )
    public void updateDocument(String datasetId, String documentId, String text) {
        if (datasetId == null || datasetId.isBlank()) {
            throw new IllegalArgumentException("datasetId 不能为空");
        }
        if (documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException("documentId 不能为空");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text 不能为空");
        }

        log.info("更新 Dify 文档: datasetId={}, documentId={}", datasetId, documentId);

        String url = config.getApiUrl() + "/datasets/" + datasetId + "/documents/" + documentId;

        HttpHeaders headers = createHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of("text", text);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Void> response = restTemplate.exchange(
                url, HttpMethod.PUT, request, Void.class
            );

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new DifyApiException("更新文档失败: " + response.getStatusCode());
            }

            log.info("Dify 文档更新成功: documentId={}", documentId);
        } catch (DifyApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用 Dify API 失败: {}", e.getMessage());
            throw new DifyApiException("更新文档失败: " + e.getMessage(), e);
        }
    }

    /**
     * 删除 Dify 文档
     */
    @Retryable(
        value = {DifyApiException.class},
        maxExpression = "${dify.sync.retry-count:3}",
        backoff = @Backoff(delayExpression = "${dify.sync.retry-delay:5000}")
    )
    public void deleteDocument(String datasetId, String documentId) {
        if (datasetId == null || datasetId.isBlank()) {
            throw new IllegalArgumentException("datasetId 不能为空");
        }
        if (documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException("documentId 不能为空");
        }

        log.info("删除 Dify 文档: datasetId={}, documentId={}", datasetId, documentId);

        String url = config.getApiUrl() + "/datasets/" + datasetId + "/documents/" + documentId;

        HttpHeaders headers = createHeaders();
        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<Void> response = restTemplate.exchange(
                url, HttpMethod.DELETE, request, Void.class
            );

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new DifyApiException("删除文档失败: " + response.getStatusCode());
            }

            log.info("Dify 文档删除成功: documentId={}", documentId);
        } catch (DifyApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用 Dify API 失败: {}", e.getMessage());
            throw new DifyApiException("删除文档失败: " + e.getMessage(), e);
        }
    }

    /**
     * 列出知识库中的文档
     */
    @Retryable(
        value = {DifyApiException.class},
        maxExpression = "${dify.sync.retry-count:3}",
        backoff = @Backoff(delayExpression = "${dify.sync.retry-delay:5000}")
    )
    public DifyDocumentList listDocuments(String datasetId, int page, int limit) {
        // 参数校验
        if (datasetId == null || datasetId.isBlank()) {
            throw new IllegalArgumentException("datasetId 不能为空");
        }

        log.info("列出 Dify 文档: datasetId={}, page={}, limit={}", datasetId, page, limit);

        String url = config.getApiUrl() + "/datasets/" + datasetId + "/documents?page=" + page + "&limit=" + limit;

        HttpHeaders headers = createHeaders();
        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                url, HttpMethod.GET, request, JsonNode.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode body = response.getBody();
                List<DifyDocument> documents = new ArrayList<>();

                JsonNode dataNode = body.path("data");
                if (dataNode.isArray()) {
                    for (JsonNode docNode : dataNode) {
                        documents.add(parseDocument(docNode));
                    }
                }

                return DifyDocumentList.builder()
                    .data(documents)
                    .total(body.path("total").asInt(0))
                    .page(page)
                    .limit(limit)
                    .hasMore(body.path("has_more").asBoolean(false))
                    .build();
            } else {
                throw new DifyApiException("列出文档失败: " + response.getStatusCode());
            }
        } catch (DifyApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用 Dify API 失败: {}", e.getMessage());
            throw new DifyApiException("列出文档失败: " + e.getMessage(), e);
        }
    }

    /**
     * 发送对话消息
     */
    @Retryable(
        value = {DifyApiException.class},
        maxExpression = "${dify.sync.retry-count:3}",
        backoff = @Backoff(delayExpression = "${dify.sync.retry-delay:5000}")
    )
    public DifyChatResponse chat(String query, String conversationId, Map<String, String> inputs) {
        // 参数校验
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query 不能为空");
        }

        // 日志脱敏：截断 query 内容，只记录前 50 个字符
        String truncatedQuery = query.length() > 50 ? query.substring(0, 50) + "..." : query;
        log.info("发送 Dify 对话: query={}, conversationId={}", truncatedQuery, conversationId);

        String url = config.getApiUrl() + "/chat-messages";

        HttpHeaders headers = createHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("query", query);
        body.put("inputs", inputs != null ? inputs : Map.of());
        body.put("response_mode", "blocking");
        body.put("user", config.getDefaultUser());

        if (conversationId != null && !conversationId.isBlank()) {
            body.put("conversation_id", conversationId);
        }

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                url, HttpMethod.POST, request, JsonNode.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode responseBody = response.getBody();
                return DifyChatResponse.builder()
                    .answer(responseBody.path("answer").asText())
                    .conversationId(responseBody.path("conversation_id").asText())
                    .retrievalSources(parseRetrievalSources(responseBody.path("retrieval_sources")))
                    .build();
            } else {
                throw new DifyApiException("对话失败: " + response.getStatusCode());
            }
        } catch (DifyApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用 Dify API 失败: {}", e.getMessage());
            throw new DifyApiException("对话失败: " + e.getMessage(), e);
        }
    }

    /**
     * 运行工作流
     */
    @Retryable(
        value = {DifyApiException.class},
        maxExpression = "${dify.sync.retry-count:3}",
        backoff = @Backoff(delayExpression = "${dify.sync.retry-delay:5000}")
    )
    public DifyWorkflowResponse runWorkflow(String workflowId, Map<String, Object> inputs) {
        // 参数校验
        if (workflowId == null || workflowId.isBlank()) {
            throw new IllegalArgumentException("workflowId 不能为空");
        }

        log.info("运行 Dify 工作流: workflowId={}", workflowId);

        String url = config.getApiUrl() + "/workflows/run";

        HttpHeaders headers = createHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("workflow_id", workflowId);
        body.put("inputs", inputs != null ? inputs : Map.of());
        body.put("response_mode", "blocking");
        body.put("user", config.getDefaultUser());

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                url, HttpMethod.POST, request, JsonNode.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode responseBody = response.getBody();
                return DifyWorkflowResponse.builder()
                    .runId(responseBody.path("run_id").asText())
                    .status(responseBody.path("status").asText())
                    .outputs(parseOutputs(responseBody.path("outputs")))
                    .elapsed(responseBody.path("elapsed").asLong())
                    .build();
            } else {
                throw new DifyApiException("运行工作流失败: " + response.getStatusCode());
            }
        } catch (DifyApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用 Dify API 失败: {}", e.getMessage());
            throw new DifyApiException("运行工作流失败: " + e.getMessage(), e);
        }
    }

    // ========== Recovery 方法 ==========

    @Recover
    public String recoverCreateDocument(DifyApiException e, String datasetId, String text,
                                        Map<String, Object> metadata) {
        log.error("Dify API 创建文档失败，已重试 {} 次: {}", config.getSync().getRetryCount(), e.getMessage());
        throw new DifyApiException("创建文档失败: " + e.getMessage(), e);
    }

    @Recover
    public void recoverUpdateDocument(DifyApiException e, String datasetId, String documentId, String text) {
        log.error("Dify API 更新文档失败，已重试 {} 次: {}", config.getSync().getRetryCount(), e.getMessage());
        throw new DifyApiException("更新文档失败: " + e.getMessage(), e);
    }

    @Recover
    public void recoverDeleteDocument(DifyApiException e, String datasetId, String documentId) {
        log.error("Dify API 删除文档失败，已重试 {} 次: {}", config.getSync().getRetryCount(), e.getMessage());
        throw new DifyApiException("删除文档失败: " + e.getMessage(), e);
    }

    @Recover
    public DifyDocumentList recoverListDocuments(DifyApiException e, String datasetId, int page, int limit) {
        log.error("Dify API 列出文档失败，已重试 {} 次: {}", config.getSync().getRetryCount(), e.getMessage());
        throw new DifyApiException("列出文档失败: " + e.getMessage(), e);
    }

    @Recover
    public DifyChatResponse recoverChat(DifyApiException e, String query, String conversationId,
                                        Map<String, String> inputs) {
        log.error("Dify API 对话失败，已重试 {} 次: {}", config.getSync().getRetryCount(), e.getMessage());
        throw new DifyApiException("对话失败: " + e.getMessage(), e);
    }

    @Recover
    public DifyWorkflowResponse recoverRunWorkflow(DifyApiException e, String workflowId,
                                                   Map<String, Object> inputs) {
        log.error("Dify API 运行工作流失败，已重试 {} 次: {}", config.getSync().getRetryCount(), e.getMessage());
        throw new DifyApiException("运行工作流失败: " + e.getMessage(), e);
    }

    // ========== 辅助方法 ==========

    /**
     * 创建包含认证信息的 HTTP 请求头
     */
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + config.getApiKey());
        return headers;
    }

    /**
     * 解析 Dify 文档 JSON 节点为 DifyDocument 对象
     */
    private DifyDocument parseDocument(JsonNode node) {
        return DifyDocument.builder()
            .id(node.path("id").asText())
            .name(node.path("name").asText())
            .contentPreview(node.path("content_preview").asText())
            .createdAt(parseDateTime(node.path("created_at").asText()))
            .updatedAt(parseDateTime(node.path("updated_at").asText()))
            .indexingStatus(node.path("indexing_status").asText())
            .build();
    }

    /**
     * 解析检索来源 JSON 数组
     */
    private List<DifyChatResponse.DifyRetrievalSource> parseRetrievalSources(JsonNode node) {
        List<DifyChatResponse.DifyRetrievalSource> sources = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode sourceNode : node) {
                sources.add(DifyChatResponse.DifyRetrievalSource.builder()
                    .datasetName(sourceNode.path("dataset_name").asText())
                    .documentName(sourceNode.path("document_name").asText())
                    .content(sourceNode.path("content").asText())
                    .score(sourceNode.path("score").asDouble())
                    .build());
            }
        }
        return sources;
    }

    /**
     * 解析工作流输出 JSON 为 Map
     */
    private Map<String, Object> parseOutputs(JsonNode node) {
        Map<String, Object> outputs = new HashMap<>();
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                outputs.put(entry.getKey(), entry.getValue().asText());
            });
        }
        return outputs;
    }

    /**
     * 解析 ISO 格式的日期时间字符串
     */
    private LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(dateTimeStr, DateTimeFormatter.ISO_DATE_TIME);
        } catch (Exception e) {
            log.warn("解析日期时间失败: {}", dateTimeStr);
            return null;
        }
    }
}
