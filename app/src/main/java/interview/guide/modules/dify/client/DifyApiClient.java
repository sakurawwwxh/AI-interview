package interview.guide.modules.dify.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import interview.guide.modules.dify.config.DifyConfig;
import interview.guide.modules.dify.exception.DifyApiException;
import interview.guide.modules.dify.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Dify API 客户端
 * 封装所有与 Dify 平台的交互
 *
 * <p>使用 Spring 6.1+ 引入的 {@link RestClient} 作为 HTTP 客户端（Spring Boot 4.0 已移除
 * RestTemplateBuilder 和 RestTemplate 的自动配置支持）。
 */
@Service
@Slf4j
public class DifyApiClient {

    private final RestClient restClient;
    private final DifyConfig config;
    private final ObjectMapper objectMapper;

    public DifyApiClient(DifyConfig config,
                         ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
        // 构建 RestClient，统一配置 baseUrl 和默认 Authorization 头
        this.restClient = RestClient.builder()
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + config.getApiKey())
            .build();
    }

    /**
     * 创建文档到 Dify 知识库
     *
     * <p>调用 Dify 官方端点 {@code POST /datasets/{datasetId}/document/create-by-text}。
     * 注意：Dify 知识库 API 要求 {@code name} 为必填字段，且不识别 {@code metadata} 字段，
     * 因此从 metadata 中提取 name 后丢弃 metadata。
     *
     * @param datasetId 知识库 ID
     * @param text      文档内容
     * @param metadata  元数据（仅从中提取 name，其余字段被 Dify 知识库 API 忽略）
     * @return Dify 文档 ID
     */
    @Retryable(
        value = {DifyApiException.class},
        maxAttemptsExpression = "${dify.sync.retry-count:3}",
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

        // Dify 要求 name 必填，从 metadata 提取；缺失时使用默认值
        String name = "document";
        if (metadata != null && metadata.get("name") instanceof String n && !n.isBlank()) {
            name = n;
        }

        log.info("创建 Dify 文档: datasetId={}, name={}, textLength={}", datasetId, name, text.length());

        String url = config.getApiUrl() + "/datasets/" + datasetId + "/document/create-by-text";

        // Dify 知识库 API 的 create-by-text 请求体，name 和 text 为必填字段
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("text", text);
        body.put("indexing_technique", "high_quality");
        body.put("doc_language", "zh-CN");

        try {
            ResponseEntity<JsonNode> response = restClient.post()
                .uri(url)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .retrieve()
                .toEntity(JsonNode.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                // 官方响应结构: {"document":{"id":"..."},"batch":"..."}
                String documentId = response.getBody().path("document").path("id").asText();
                if (documentId.isBlank()) {
                    throw new DifyApiException("创建文档失败: 响应中缺少 document.id");
                }
                log.info("Dify 文档创建成功: documentId={}", documentId);
                return documentId;
            } else {
                throw new DifyApiException("创建文档失败: " + response.getStatusCode());
            }
        } catch (DifyApiException e) {
            throw e;
        } catch (RestClientException e) {
            log.error("调用 Dify API 失败: {}", e.getMessage());
            throw new DifyApiException("创建文档失败: " + e.getMessage(), e);
        }
    }

    /**
     * 更新 Dify 文档
     *
     * <p>调用 Dify 官方端点 {@code POST /datasets/{datasetId}/documents/{documentId}/update-by-text}。
     * 注意：Dify 知识库 API 要求当提供 text 时 name 必填，且使用 POST 而非 PUT。
     *
     * @param datasetId  知识库 ID
     * @param documentId 文档 ID
     * @param name       文档名称（text 提供时必填）
     * @param text       文档内容
     */
    @Retryable(
        value = {DifyApiException.class},
        maxAttemptsExpression = "${dify.sync.retry-count:3}",
        backoff = @Backoff(delayExpression = "${dify.sync.retry-delay:5000}")
    )
    public void updateDocument(String datasetId, String documentId, String name, String text) {
        if (datasetId == null || datasetId.isBlank()) {
            throw new IllegalArgumentException("datasetId 不能为空");
        }
        if (documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException("documentId 不能为空");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text 不能为空");
        }

        // Dify 要求 text 提供时 name 必填，缺失时使用默认值
        String docName = (name != null && !name.isBlank()) ? name : "document";

        log.info("更新 Dify 文档: datasetId={}, documentId={}, name={}", datasetId, documentId, docName);

        String url = config.getApiUrl() + "/datasets/" + datasetId + "/documents/" + documentId + "/update-by-text";

        Map<String, Object> body = new HashMap<>();
        body.put("name", docName);
        body.put("text", text);
        body.put("doc_language", "zh-CN");

        try {
            ResponseEntity<JsonNode> response = restClient.post()
                .uri(url)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .retrieve()
                .toEntity(JsonNode.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new DifyApiException("更新文档失败: " + response.getStatusCode());
            }

            log.info("Dify 文档更新成功: documentId={}", documentId);
        } catch (DifyApiException e) {
            throw e;
        } catch (RestClientException e) {
            log.error("调用 Dify API 失败: {}", e.getMessage());
            throw new DifyApiException("更新文档失败: " + e.getMessage(), e);
        }
    }

    /**
     * 删除 Dify 文档
     */
    @Retryable(
        value = {DifyApiException.class},
        maxAttemptsExpression = "${dify.sync.retry-count:3}",
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

        try {
            ResponseEntity<Void> response = restClient.delete()
                .uri(url)
                .retrieve()
                .toEntity(Void.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new DifyApiException("删除文档失败: " + response.getStatusCode());
            }

            log.info("Dify 文档删除成功: documentId={}", documentId);
        } catch (DifyApiException e) {
            throw e;
        } catch (RestClientException e) {
            log.error("调用 Dify API 失败: {}", e.getMessage());
            throw new DifyApiException("删除文档失败: " + e.getMessage(), e);
        }
    }

    /**
     * 列出知识库中的文档
     */
    @Retryable(
        value = {DifyApiException.class},
        maxAttemptsExpression = "${dify.sync.retry-count:3}",
        backoff = @Backoff(delayExpression = "${dify.sync.retry-delay:5000}")
    )
    public DifyDocumentList listDocuments(String datasetId, int page, int limit) {
        // 参数校验
        if (datasetId == null || datasetId.isBlank()) {
            throw new IllegalArgumentException("datasetId 不能为空");
        }

        log.info("列出 Dify 文档: datasetId={}, page={}, limit={}", datasetId, page, limit);

        String url = config.getApiUrl() + "/datasets/" + datasetId + "/documents?page=" + page + "&limit=" + limit;

        try {
            ResponseEntity<JsonNode> response = restClient.get()
                .uri(url)
                .retrieve()
                .toEntity(JsonNode.class);

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
        } catch (RestClientException e) {
            log.error("调用 Dify API 失败: {}", e.getMessage());
            throw new DifyApiException("列出文档失败: " + e.getMessage(), e);
        }
    }

    /**
     * 发送对话消息
     */
    @Retryable(
        value = {DifyApiException.class},
        maxAttemptsExpression = "${dify.sync.retry-count:3}",
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

        Map<String, Object> body = new HashMap<>();
        body.put("query", query);
        body.put("inputs", inputs != null ? inputs : Map.of());
        body.put("response_mode", "blocking");
        body.put("user", config.getDefaultUser());

        if (conversationId != null && !conversationId.isBlank()) {
            body.put("conversation_id", conversationId);
        }

        try {
            ResponseEntity<JsonNode> response = restClient.post()
                .uri(url)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .retrieve()
                .toEntity(JsonNode.class);

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
        } catch (RestClientException e) {
            log.error("调用 Dify API 失败: {}", e.getMessage());
            throw new DifyApiException("对话失败: " + e.getMessage(), e);
        }
    }

    /**
     * 运行工作流
     */
    @Retryable(
        value = {DifyApiException.class},
        maxAttemptsExpression = "${dify.sync.retry-count:3}",
        backoff = @Backoff(delayExpression = "${dify.sync.retry-delay:5000}")
    )
    public DifyWorkflowResponse runWorkflow(String workflowId, Map<String, Object> inputs) {
        // 参数校验
        if (workflowId == null || workflowId.isBlank()) {
            throw new IllegalArgumentException("workflowId 不能为空");
        }

        log.info("运行 Dify 工作流: workflowId={}", workflowId);

        String url = config.getApiUrl() + "/workflows/run";

        Map<String, Object> body = new HashMap<>();
        body.put("workflow_id", workflowId);
        body.put("inputs", inputs != null ? inputs : Map.of());
        body.put("response_mode", "blocking");
        body.put("user", config.getDefaultUser());

        try {
            ResponseEntity<JsonNode> response = restClient.post()
                .uri(url)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .retrieve()
                .toEntity(JsonNode.class);

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
        } catch (RestClientException e) {
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
    public void recoverUpdateDocument(DifyApiException e, String datasetId, String documentId,
                                      String name, String text) {
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
     * 解析 Dify 文档 JSON 节点为 DifyDocument 对象
     *
     * <p>Dify 知识库 API 的 list documents 响应字段：id、name、indexing_status、display_status、
     * word_count、hit_count、created_at、updated_at。其中时间字段为 Unix 时间戳（秒）。
     */
    private DifyDocument parseDocument(JsonNode node) {
        return DifyDocument.builder()
            .id(node.path("id").asText())
            .name(node.path("name").asText())
            .indexingStatus(node.path("indexing_status").asText())
            .displayStatus(node.path("display_status").asText())
            .wordCount(node.path("word_count").asInt(0))
            .hitCount(node.path("hit_count").asInt(0))
            .createdAt(parseTimestamp(node.path("created_at")))
            .updatedAt(parseTimestamp(node.path("updated_at")))
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
     * 解析 Dify 返回的 Unix 时间戳（秒）为 LocalDateTime
     *
     * <p>Dify 知识库 API 的 created_at / updated_at 字段为 Unix 时间戳（秒，整数或浮点）。
     * 部分旧版接口可能返回 ISO 字符串，此处做兼容处理。
     */
    private LocalDateTime parseTimestamp(JsonNode node) {
        if (node == null || node.isNull() || node.asText().isBlank()) {
            return null;
        }
        try {
            // 数值型时间戳（Dify 知识库 API 的标准格式）
            if (node.isNumber()) {
                long seconds = (long) node.asDouble();
                return LocalDateTime.ofInstant(java.time.Instant.ofEpochSecond(seconds),
                    java.time.ZoneId.systemDefault());
            }
            // 兼容 ISO 字符串格式
            String text = node.asText();
            if (text.matches("\\d+(\\.\\d+)?")) {
                long seconds = (long) Double.parseDouble(text);
                return LocalDateTime.ofInstant(java.time.Instant.ofEpochSecond(seconds),
                    java.time.ZoneId.systemDefault());
            }
            return LocalDateTime.parse(text, DateTimeFormatter.ISO_DATE_TIME);
        } catch (Exception e) {
            log.warn("解析时间戳失败: {}", node);
            return null;
        }
    }
}
