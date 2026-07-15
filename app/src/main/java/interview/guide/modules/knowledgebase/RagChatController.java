package interview.guide.modules.knowledgebase;

import interview.guide.common.result.Result;
import interview.guide.modules.knowledgebase.model.RagChatDTO.*;
import interview.guide.modules.knowledgebase.service.RagChatSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * RAG 聊天控制器
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class RagChatController {

    private final RagChatSessionService sessionService;

    /**
     * 创建新会话
     */
    @PostMapping("/api/rag-chat/sessions")
    public Result<SessionDTO> createSession(@Valid @RequestBody CreateSessionRequest request) {
        return Result.success(sessionService.createSession(request));
    }

    /**
     * 获取会话列表
     */
    @GetMapping("/api/rag-chat/sessions")
    public Result<List<SessionListItemDTO>> listSessions() {
        return Result.success(sessionService.listSessions());
    }

    /**
     * 获取会话详情（包含消息历史）
     * GET /api/rag-chat/sessions/{sessionId}
     */
    @GetMapping("/api/rag-chat/sessions/{sessionId}")
    public Result<SessionDetailDTO> getSessionDetail(@PathVariable Long sessionId) {
        return Result.success(sessionService.getSessionDetail(sessionId));
    }

    /**
     * 更新会话标题
     */
    @PutMapping("/api/rag-chat/sessions/{sessionId}/title")
    public Result<Void> updateSessionTitle(
            @PathVariable Long sessionId,
            @Valid @RequestBody UpdateTitleRequest request) {
        sessionService.updateSessionTitle(sessionId, request.title());
        return Result.success(null);
    }

    /**
     * 切换会话置顶状态
     * PUT /api/rag-chat/sessions/{sessionId}/pin
     */
    @PutMapping("/api/rag-chat/sessions/{sessionId}/pin")
    public Result<Void> togglePin(@PathVariable Long sessionId) {
        sessionService.togglePin(sessionId);
        return Result.success(null);
    }

    /**
     * 更新会话知识库
     */
    @PutMapping("/api/rag-chat/sessions/{sessionId}/knowledge-bases")
    public Result<Void> updateSessionKnowledgeBases(
            @PathVariable Long sessionId,
            @Valid @RequestBody UpdateKnowledgeBasesRequest request) {
        sessionService.updateSessionKnowledgeBases(sessionId, request.knowledgeBaseIds());
        return Result.success(null);
    }

    /**
     * 删除会话
     * DELETE /api/rag-chat/sessions/{sessionId}
     */
    @DeleteMapping("/api/rag-chat/sessions/{sessionId}")
    public Result<Void> deleteSession(@PathVariable Long sessionId) {
        sessionService.deleteSession(sessionId);
        return Result.success(null);
    }

    /**
     * 发送消息（流式SSE）
     * 流式响应设计：
     * 1. 先同步保存用户消息和创建 AI 消息占位
     * 2. 发送心跳事件防止检索期间超时
     * 3. 返回流式响应
     * 4. 流式完成/中断/错误时通过回调更新消息
     */
    @PostMapping(value = "/api/rag-chat/sessions/{sessionId}/messages/stream",
                 produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> sendMessageStream(
            @PathVariable Long sessionId,
            @Valid @RequestBody SendMessageRequest request) {

        log.info("收到 RAG 聊天流式请求: sessionId={}, question={}", sessionId, request.question());

        // 1. 准备消息（保存用户消息，创建 AI 消息占位）
        Long messageId = sessionService.prepareStreamMessage(sessionId, request.question());

        // 2. 获取流式响应，收集内容用于落库
        StringBuffer fullContent = new StringBuffer();
        boolean[] completed = {false};

        // 3. 心跳事件（防止检索期间客户端超时）
        ServerSentEvent<String> heartbeat = ServerSentEvent.<String>builder()
            .event("ping")
            .data("")
            .build();

        // 4. 主流式内容
        Flux<ServerSentEvent<String>> contentFlux = sessionService.getStreamAnswer(sessionId, request.question())
            .doOnNext(fullContent::append)
            .map(chunk -> ServerSentEvent.<String>builder()
                .data(chunk.replace("\n", "\\n").replace("\r", "\\r"))
                .build())
            .doOnComplete(() -> {
                completed[0] = true;
                sessionService.completeStreamMessage(messageId, fullContent.toString());
                log.info("RAG 聊天流式完成: sessionId={}, messageId={}", sessionId, messageId);
            })
            .doOnCancel(() -> {
                // 用户中断时落库已收内容
                sessionService.completeStreamMessage(messageId, fullContent.toString());
                log.info("RAG 聊天流式被中断: sessionId={}, messageId={}, contentLength={}",
                    sessionId, messageId, fullContent.length());
            })
            .onErrorResume(e -> {
                // 错误时只保存已收的部分内容，不把错误文案存入数据库
                sessionService.completeStreamMessage(messageId, fullContent.toString());
                log.error("RAG 聊天流式错误: sessionId={}", sessionId, e);
                // 向客户端发送 error 事件
                return Flux.just(ServerSentEvent.<String>builder()
                    .event("error")
                    .data("回答生成失败，请稍后重试")
                    .build());
            });

        return Flux.concat(Flux.just(heartbeat), contentFlux);
    }

    /**
     * 发送消息（Dify 聊天助手流式SSE）
     *
     * <p>与 {@link #sendMessageStream} 结构一致，但走 Dify chat-messages API。
     * 同样通过 session 保存用户消息和 AI 回答，支持对话历史展示。
     */
    @PostMapping(value = "/api/rag-chat/sessions/{sessionId}/messages/dify-stream",
                 produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> sendDifyMessageStream(
            @PathVariable Long sessionId,
            @Valid @RequestBody SendMessageRequest request) {

        log.info("收到 Dify 聊天流式请求: sessionId={}, question={}", sessionId, request.question());

// 1. 准备消息（保存用户消息，创建 AI 消息占位）
        Long messageId = sessionService.prepareStreamMessage(sessionId, request.question());

        // 2. 获取 Dify 流式响应，并通过回调捕获 Dify 返回的 conversation_id
        StringBuffer fullContent = new StringBuffer();
        java.util.concurrent.atomic.AtomicReference<String> capturedConvId = new java.util.concurrent.atomic.AtomicReference<>();

        // 3. 心跳事件
        ServerSentEvent<String> heartbeat = ServerSentEvent.<String>builder()
            .event("ping")
            .data("")
            .build();

        // 4. 主流式内容：conversation_id 从会话实体读取（首问为空），
        //    Dify 返回新 conversation_id 时通过回调捕获
        Flux<ServerSentEvent<String>> contentFlux = sessionService
            .getDifyStreamAnswer(sessionId, request.question(), capturedConvId::set)
            .doOnNext(fullContent::append)
            .map(chunk -> ServerSentEvent.<String>builder()
                .data(chunk.replace("\n", "\\n").replace("\r", "\\r"))
                .build())
            .doOnComplete(() -> {
                sessionService.completeStreamMessage(messageId, fullContent.toString());
                // 持久化捕获到的 conversation_id，后续多轮对话可恢复上下文
                String convId = capturedConvId.get();
                if (convId != null && !convId.isBlank()) {
                    sessionService.updateDifyConversationId(sessionId, convId);
                }
                log.info("Dify 聊天流式完成: sessionId={}, messageId={}", sessionId, messageId);
            })
            .doOnCancel(() -> {
                sessionService.completeStreamMessage(messageId, fullContent.toString());
                String convId = capturedConvId.get();
                if (convId != null && !convId.isBlank()) {
                    sessionService.updateDifyConversationId(sessionId, convId);
                }
                log.info("Dify 聊天流式被中断: sessionId={}, messageId={}, contentLength={}",
                    sessionId, messageId, fullContent.length());
            })
            .onErrorResume(e -> {
                sessionService.completeStreamMessage(messageId, fullContent.toString());
                log.error("Dify 聊天流式错误: sessionId={}", sessionId, e);
                return Flux.just(ServerSentEvent.<String>builder()
                    .event("error")
                    .data("Dify 回答生成失败，请稍后重试")
                    .build());
            });

        return Flux.concat(Flux.just(heartbeat), contentFlux);
    }
}
