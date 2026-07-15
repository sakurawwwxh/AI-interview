package interview.guide.modules.dify.client;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import interview.guide.modules.dify.config.DifyConfig;
import interview.guide.modules.dify.exception.DifyApiException;
import interview.guide.modules.dify.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.ProxyProvider;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Dify API 客户端
 * 封装所有与 Dify 平台的交互
 *
 * <p>使用 Spring 6.1+ 引入的 {@link RestClient} 作为 HTTP 客户端（Spring Boot 4.0 已移除
 * RestTemplateBuilder 和 RestTemplate 的自动配置支持）。
 * 可选通过 {@code dify.proxy.*} 走本机 HTTP 代理访问 Dify Cloud。
 */
@Service
@Slf4j
public class DifyApiClient {

    /** 知识库文档同步用 RestClient（Dataset API Key） */
    private final RestClient datasetClient;
    /** 工作流/对话用 RestClient（App API Key） */
    private final RestClient appClient;
    /** 工作流流式用 WebClient（App API Key） */
    private final WebClient appWebClient;
    private final DifyConfig config;
    private final ObjectMapper objectMapper;

    public DifyApiClient(DifyConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;

        JdkClientHttpRequestFactory requestFactory = buildRequestFactory(config);
        // 知识库 API 用的 RestClient（dataset-xxx key）
        this.datasetClient = RestClient.builder()
            .requestFactory(requestFactory)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + config.getApiKey())
            .build();
        // 工作流/对话 API 用的 RestClient（app-xxx key）
        this.appClient = RestClient.builder()
            .requestFactory(requestFactory)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + config.getAppApiKey())
            .build();
        // 流式工作流用的 WebClient（app-xxx key）
        this.appWebClient = WebClient.builder()
            .baseUrl(config.getApiUrl())
            .clientConnector(new ReactorClientHttpConnector(buildReactorHttpClient(config)))
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + config.getAppApiKey())
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();

        if (config.getProxy() != null && config.getProxy().isConfigured()) {
            log.info("Dify HTTP 客户端已启用代理: {}:{}", config.getProxy().getHost(), config.getProxy().getPort());
        } else {
            log.info("Dify HTTP 客户端未配置代理（直连 {}）", config.getApiUrl());
        }
    }

    /** 构建带超时/可选代理的 JDK RestClient 请求工厂 */
    private static JdkClientHttpRequestFactory buildRequestFactory(DifyConfig config) {
        DifyConfig.ProxyConfig proxy = config.getProxy() != null ? config.getProxy() : new DifyConfig.ProxyConfig();
        int connectMs = Math.max(3000, proxy.getConnectTimeoutMs());

        java.net.http.HttpClient.Builder builder = java.net.http.HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(connectMs));

        if (proxy.isConfigured()) {
            builder.proxy(ProxySelector.of(new InetSocketAddress(proxy.getHost().trim(), proxy.getPort())));
        }

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(builder.build());
        // 读写超时略长于连接超时，避免大文档同步中途断开
        factory.setReadTimeout(Duration.ofMillis(Math.max(connectMs * 4L, 60000L)));
        return factory;
    }

    /** 构建 WebClient 用的 Reactor Netty HttpClient（同样支持代理） */
    private static HttpClient buildReactorHttpClient(DifyConfig config) {
        DifyConfig.ProxyConfig proxy = config.getProxy() != null ? config.getProxy() : new DifyConfig.ProxyConfig();
        int connectMs = Math.max(3000, proxy.getConnectTimeoutMs());

        HttpClient httpClient = HttpClient.create()
            .responseTimeout(Duration.ofMillis(Math.max(connectMs * 4L, 60000L)));

        if (proxy.isConfigured()) {
            String host = proxy.getHost().trim();
            int port = proxy.getPort();
            httpClient = httpClient.proxy(typeSpec ->
                typeSpec.type(ProxyProvider.Proxy.HTTP).host(host).port(port));
        }
        return httpClient;
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
        noRetryFor = {IllegalArgumentException.class},
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
            ResponseEntity<JsonNode> response = datasetClient.post()
                .uri(url)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .retrieve()
                .toEntity(JsonNode.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                // 官方响应结构: {"document":{"id":"..."},"batch":"..."}
                String documentId = safeAsText(response.getBody().path("document"), "id");
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
        noRetryFor = {IllegalArgumentException.class},
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
            ResponseEntity<JsonNode> response = datasetClient.post()
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
        noRetryFor = {IllegalArgumentException.class},
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
            ResponseEntity<Void> response = datasetClient.delete()
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
        noRetryFor = {IllegalArgumentException.class},
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
            ResponseEntity<JsonNode> response = datasetClient.get()
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
        noRetryFor = {IllegalArgumentException.class},
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
            ResponseEntity<JsonNode> response = appClient.post()
                .uri(url)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .retrieve()
                .toEntity(JsonNode.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode responseBody = response.getBody();
                return DifyChatResponse.builder()
                    .answer(safeAsText(responseBody, "answer"))
                    .conversationId(safeAsText(responseBody, "conversation_id"))
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
        noRetryFor = {IllegalArgumentException.class},
        maxAttemptsExpression = "${dify.sync.retry-count:3}",
        backoff = @Backoff(delayExpression = "${dify.sync.retry-delay:5000}")
    )
    public DifyWorkflowResponse runWorkflow(String workflowId, Map<String, Object> inputs) {
        // Dify Cloud 的 App Key 已绑定具体应用，workflowId 可为空
        log.info("运行 Dify 工作流: workflowId={}", workflowId != null ? workflowId : "(app-bound)");

        String url = config.getApiUrl() + "/workflows/run";

        Map<String, Object> body = new HashMap<>();
        // workflow_id 仅自建 Dify 需要，Dify Cloud 用 App Key 绑定，不传
        if (workflowId != null && !workflowId.isBlank()) {
            body.put("workflow_id", workflowId);
        }
        body.put("inputs", inputs != null ? inputs : Map.of());
        body.put("response_mode", "blocking");
        body.put("user", config.getDefaultUser());

        try {
            ResponseEntity<JsonNode> response = appClient.post()
                .uri(url)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .retrieve()
                .toEntity(JsonNode.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode responseBody = response.getBody();
                return DifyWorkflowResponse.builder()
                    .runId(safeAsText(responseBody, "run_id"))
                    .status(safeAsText(responseBody, "status"))
                    .outputs(parseOutputs(responseBody.path("outputs")))
                    .elapsed(responseBody.path("elapsed").asLong(0))
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

    /**
     * 流式运行 Dify 聊天助手（SSE）
     *
     * <p>调用 Dify 聊天 API（{@code POST /chat-messages}），以 streaming 模式运行，
     * 逐块返回 LLM 输出。适用于在 Dify Cloud 上编排了知识检索的聊天助手应用。
     *
     * @param query           用户问题
     * @param conversationId  会话 ID（可选，为空则新建会话）
     * @return 流式输出 Flux，每个 chunk 是一段回答文本
     */
    public Flux<String> chatStream(String query, String conversationId) {
        return chatStream(query, conversationId, null);
    }

    /**
     * 流式运行 Dify 聊天助手（SSE），并捕获 conversation_id
     *
     * <p>与 {@link #chatStream(String, String)} 一致，额外通过回调暴露 Dify 返回的
     * {@code conversation_id}（出现在 {@code message_end} 事件中）。调用方可在回调中
     * 持久化该 ID，后续请求传入即可恢复多轮对话上下文。
     *
     * @param query                  用户问题
     * @param conversationId         会话 ID（可选，为空则新建）
     * @param conversationIdSink     回调，收到 conversation_id 时调用；为 null 则忽略
     * @return 流式输出 Flux
     */
    public Flux<String> chatStream(String query, String conversationId,
                                   java.util.function.Consumer<String> conversationIdSink) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query 不能为空");
        }

        String truncatedQuery = query.length() > 50 ? query.substring(0, 50) + "..." : query;
        log.info("流式运行 Dify 聊天: query={}, conversationId={}", truncatedQuery, conversationId);

        Map<String, Object> body = new HashMap<>();
        body.put("query", query);
        body.put("inputs", Map.of());
        body.put("response_mode", "streaming");
        body.put("user", config.getDefaultUser());
        if (conversationId != null && !conversationId.isBlank()) {
            body.put("conversation_id", conversationId);
        }

        return appWebClient.post()
            .uri("/chat-messages")
            .bodyValue(body)
            .retrieve()
            .bodyToFlux(String.class)
            .filter(chunk -> chunk != null && !chunk.isBlank())
            .mapNotNull(chunk -> {
                // Dify SSE 每条数据是 JSON，解析提取 answer 字段
                // 事件类型: message（含 answer）、workflow_started、node_started、
                // node_finished、message_end（含 conversation_id）、error 等
                try {
                    JsonNode node = objectMapper.readTree(chunk);
                    String event = safeAsText(node, "event");
                    // 处理 message 事件，提取 answer
                    if ("message".equals(event)) {
                        String answer = safeAsText(node, "answer");
                        return answer.isBlank() ? null : answer;
                    }
                    // message_end 事件：捕获 conversation_id 供多轮对话使用
                    if ("message_end".equals(event)) {
                        if (conversationIdSink != null) {
                            String convId = safeAsText(node, "conversation_id");
                            if (!convId.isBlank()) {
                                conversationIdSink.accept(convId);
                                log.info("Dify chatStream 捕获 conversation_id: {}", convId);
                            }
                        }
                        return null;
                    }
                    // error 事件
                    if ("error".equals(event)) {
                        String errorMsg = safeAsText(node, "message");
                        return "【错误】" + (errorMsg.isBlank() ? "Dify 调用失败" : errorMsg);
                    }
                    // 其他事件（workflow_started/node_finished 等）忽略
                    return null;
                } catch (Exception e) {
                    log.debug("解析 Dify SSE chunk 失败，跳过: {}", chunk.substring(0, Math.min(chunk.length(), 80)));
                    return null;
                }
            })
            .filter(answer -> answer != null && !answer.isBlank())
            .doOnNext(answer -> log.debug("Dify 聊天流式 answer: {}",
                answer.substring(0, Math.min(answer.length(), 80))))
            .onErrorResume(e -> {
                log.error("Dify 聊天流式失败: {}", e.getMessage());
                return Flux.just("【错误】Dify 聊天调用失败，请稍后重试。");
            });
    }

    /**
     * 列出文档分段
     *
     * <p>调用 Dify 官方端点 {@code GET /datasets/{datasetId}/documents/{documentId}/segments}。
     * 分段的 content 字段包含文档正文片段，按 position 排序拼接可重建完整文档内容。
     *
     * @param datasetId  知识库 ID
     * @param documentId 文档 ID
     * @param page       页码（从 1 开始）
     * @param limit      每页数量
     * @return 分段列表
     */
    @Retryable(
        value = {DifyApiException.class},
        noRetryFor = {IllegalArgumentException.class},
        maxAttemptsExpression = "${dify.sync.retry-count:3}",
        backoff = @Backoff(delayExpression = "${dify.sync.retry-delay:5000}")
    )
    public DifySegmentList listSegments(String datasetId, String documentId, int page, int limit) {
        if (datasetId == null || datasetId.isBlank()) {
            throw new IllegalArgumentException("datasetId 不能为空");
        }
        if (documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException("documentId 不能为空");
        }

        log.info("列出 Dify 文档分段: datasetId={}, documentId={}, page={}, limit={}",
            datasetId, documentId, page, limit);

        String url = config.getApiUrl() + "/datasets/" + datasetId + "/documents/" + documentId
            + "/segments?page=" + page + "&limit=" + limit;

        try {
            ResponseEntity<JsonNode> response = datasetClient.get()
                .uri(url)
                .retrieve()
                .toEntity(JsonNode.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode body = response.getBody();
                List<DifySegment> segments = new ArrayList<>();

                JsonNode dataNode = body.path("data");
                if (dataNode.isArray()) {
                    for (JsonNode segNode : dataNode) {
                        segments.add(DifySegment.builder()
                            .id(safeAsText(segNode, "id"))
                            .position(segNode.path("position").asInt(0))
                            .content(safeAsText(segNode, "content"))
                            .wordCount(segNode.path("word_count").asInt(0))
                            .build());
                    }
                }

                return DifySegmentList.builder()
                    .data(segments)
                    .total(body.path("total").asInt(0))
                    .page(page)
                    .limit(limit)
                    .hasMore(body.path("has_more").asBoolean(false))
                    .build();
            } else {
                throw new DifyApiException("列出分段失败: " + response.getStatusCode());
            }
        } catch (DifyApiException e) {
            throw e;
        } catch (RestClientException e) {
            log.error("调用 Dify API 失败: {}", e.getMessage());
            throw new DifyApiException("列出分段失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取文档完整内容
     *
     * <p>通过分页调用 listSegments，按 position 升序拼接所有分段的 content，
     * 重建文档完整内容。适用于从 Dify 拉取文档到本地的场景。
     *
     * @param datasetId  知识库 ID
     * @param documentId 文档 ID
     * @return 文档完整内容
     */
    public String fetchDocumentContent(String datasetId, String documentId) {
        if (datasetId == null || datasetId.isBlank()) {
            throw new IllegalArgumentException("datasetId 不能为空");
        }
        if (documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException("documentId 不能为空");
        }

        log.info("获取 Dify 文档完整内容: datasetId={}, documentId={}", datasetId, documentId);

        List<DifySegment> allSegments = new ArrayList<>();
        int page = 1;
        int limit = 100;
        boolean hasMore = true;

        while (hasMore) {
            DifySegmentList segmentList = listSegments(datasetId, documentId, page, limit);
            if (segmentList.getData() == null || segmentList.getData().isEmpty()) {
                break;
            }
            allSegments.addAll(segmentList.getData());
            hasMore = segmentList.isHasMore();
            page++;
        }

        // 按 position 升序排序后拼接 content
        allSegments.sort(Comparator.comparing(DifySegment::getPosition));

        StringBuilder content = new StringBuilder();
        for (DifySegment segment : allSegments) {
            if (segment.getContent() != null && !segment.getContent().isBlank()) {
                if (content.length() > 0) {
                    content.append("\n");
                }
                content.append(segment.getContent());
            }
        }

        log.info("获取 Dify 文档内容完成: documentId={}, segments={}, contentLength={}",
            documentId, allSegments.size(), content.length());

        return content.toString();
    }

    /**
     * 获取文档所有分段（按 position 排序）
     *
     * <p>用于复用 Dify 的分块结构进行向量化，避免本地重新分块。
     *
     * @param datasetId  知识库 ID
     * @param documentId 文档 ID
     * @return 按 position 升序排列的分段列表
     */
    public List<DifySegment> fetchAllSegments(String datasetId, String documentId) {
        if (datasetId == null || datasetId.isBlank()) {
            throw new IllegalArgumentException("datasetId 不能为空");
        }
        if (documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException("documentId 不能为空");
        }

        log.info("获取 Dify 文档所有分段: datasetId={}, documentId={}", datasetId, documentId);

        List<DifySegment> allSegments = new ArrayList<>();
        int page = 1;
        int limit = 100;
        boolean hasMore = true;

        while (hasMore) {
            DifySegmentList segmentList = listSegments(datasetId, documentId, page, limit);
            if (segmentList.getData() == null || segmentList.getData().isEmpty()) {
                break;
            }
            allSegments.addAll(segmentList.getData());
            hasMore = segmentList.isHasMore();
            page++;
        }

        allSegments.sort(Comparator.comparing(DifySegment::getPosition));

        log.info("获取 Dify 分段完成: documentId={}, segments={}", documentId, allSegments.size());
        return allSegments;
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
    public DifySegmentList recoverListSegments(DifyApiException e, String datasetId, String documentId,
                                                int page, int limit) {
        log.error("Dify API 列出分段失败，已重试 {} 次: {}", config.getSync().getRetryCount(), e.getMessage());
        throw new DifyApiException("列出分段失败: " + e.getMessage(), e);
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
            .id(safeAsText(node, "id"))
            .name(safeAsText(node, "name"))
            .indexingStatus(safeAsText(node, "indexing_status"))
            .displayStatus(safeAsText(node, "display_status"))
            .wordCount(node.path("word_count").asInt(0))
            .hitCount(node.path("hit_count").asInt(0))
            .createdAt(parseTimestamp(node.path("created_at")))
            .updatedAt(parseTimestamp(node.path("updated_at")))
            .build();
    }

    /**
     * 安全读取 JSON 字段为字符串
     *
     * <p>Jackson 3.0 中 MissingNode.asText() 会抛异常，需先检查 isMissingNode。
     */
    private String safeAsText(JsonNode parent, String field) {
        JsonNode child = parent.path(field);
        if (child == null || child.isMissingNode() || child.isNull()) {
            return "";
        }
        return child.asText();
    }

    /**
     * 解析检索来源 JSON 数组
     */
    private List<DifyChatResponse.DifyRetrievalSource> parseRetrievalSources(JsonNode node) {
        List<DifyChatResponse.DifyRetrievalSource> sources = new ArrayList<>();
        if (node != null && !node.isMissingNode() && node.isArray()) {
            for (JsonNode sourceNode : node) {
                sources.add(DifyChatResponse.DifyRetrievalSource.builder()
                    .datasetName(safeAsText(sourceNode, "dataset_name"))
                    .documentName(safeAsText(sourceNode, "document_name"))
                    .content(safeAsText(sourceNode, "content"))
                    .score(sourceNode.path("score").asDouble(0.0))
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
        if (node != null && !node.isMissingNode() && node.isObject()
                && node instanceof tools.jackson.databind.node.ObjectNode objNode) {
            objNode.properties().forEach(entry -> {
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
        // Jackson 3.0 中 MissingNode.asText() 会抛异常，需先检查 isMissingNode
        if (node == null || node.isNull() || node.isMissingNode()) {
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
            if (text == null || text.isBlank()) {
                return null;
            }
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
