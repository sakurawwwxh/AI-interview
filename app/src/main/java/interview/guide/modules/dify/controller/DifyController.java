package interview.guide.modules.dify.controller;

import interview.guide.common.annotation.RateLimit;
import interview.guide.common.result.Result;
import interview.guide.modules.dify.client.DifyApiClient;
import interview.guide.modules.dify.model.*;
import interview.guide.modules.dify.service.DifyChatService;
import interview.guide.modules.dify.service.DifyKnowledgeBaseAdminService;
import interview.guide.modules.dify.service.DifySyncService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * Dify 控制器
 * 提供 Dify 相关的 API 接口
 */
@Slf4j
@RestController
@RequestMapping("/api/dify")
@RequiredArgsConstructor
public class DifyController {

    private final DifySyncService syncService;
    private final DifyKnowledgeBaseAdminService knowledgeBaseAdminService;
    private final DifyChatService chatService;
    private final DifyApiClient difyApiClient;

    /**
     * 手动触发同步
     */
    @PostMapping("/sync")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> manualSync() {
        log.info("手动触发 Dify 同步");
        syncService.syncFromDify();
        return Result.success(null);
    }

    /**
     * 获取同步状态
     */
    @GetMapping("/sync/status")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<DifySyncStatusDTO>> getSyncStatus() {
        return Result.success(syncService.getSyncStatus());
    }

    /**
     * 获取同步日志
     */
    @GetMapping("/sync/logs")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<DifySyncLogEntity>> getSyncLogs(
            @RequestParam(required = false) Long knowledgeBaseId,
            @RequestParam(defaultValue = "50") int limit) {
        return Result.success(syncService.getSyncLogs(knowledgeBaseId, limit));
    }

    /** 将单个本地知识库推送到 Dify，可用于手动重试失败项。 */
    @PostMapping("/knowledge-bases/{knowledgeBaseId}/sync-to-dify")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> syncKnowledgeBaseToDify(@PathVariable Long knowledgeBaseId) {
        knowledgeBaseAdminService.syncKnowledgeBaseToDify(knowledgeBaseId);
        return Result.success(null);
    }

    /** 管理本地知识库归属；归属决定本地 RAG 可见性。 */
    @PutMapping("/knowledge-bases/{knowledgeBaseId}/owner")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> transferKnowledgeBaseOwner(
            @PathVariable Long knowledgeBaseId,
            @Valid @RequestBody DifyKnowledgeBaseOwnerRequest request) {
        knowledgeBaseAdminService.transferKnowledgeBaseOwner(knowledgeBaseId, request.userId());
        return Result.success(null);
    }

    /**
     * 基于 Dify 对话
     */
    @PostMapping("/chat")
    @RateLimit(dimensions = {RateLimit.Dimension.GLOBAL, RateLimit.Dimension.IP}, count = 10)
    @PreAuthorize("hasRole('ADMIN')")
    public Result<DifyChatResponse> chat(@Valid @RequestBody DifyChatRequest request) {
        log.info("Dify 对话请求: query={}, kbIds={}", request.query(), request.knowledgeBaseIds());
        return Result.success(chatService.chat(
            request.query(),
            request.conversationId(),
            request.knowledgeBaseIds()
        ));
    }

    /**
     * 流式运行 Dify 聊天助手（SSE）
     *
     * <p>请求体格式：{ "query": "你的问题", "conversationId": "" }
     * 以 streaming 模式调用 Dify chat-messages API，逐块返回 LLM 输出。
     * Dify 聊天助手应用已关联知识库，可在 Dify 控制台查看召回率。
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RateLimit(dimensions = {RateLimit.Dimension.GLOBAL, RateLimit.Dimension.IP}, count = 5)
    @PreAuthorize("hasRole('ADMIN')")
    public Flux<ServerSentEvent<String>> difyChatStream(@Valid @RequestBody DifyChatRequest request) {
        log.info("流式运行 Dify 聊天: query={}",
            request.query().length() > 50 ? request.query().substring(0, 50) + "..." : request.query());

        // 心跳事件
        ServerSentEvent<String> heartbeat = ServerSentEvent.<String>builder()
            .event("ping")
            .data("")
            .build();

        Flux<ServerSentEvent<String>> contentFlux = difyApiClient.chatStream(
                request.query(), request.conversationId())
            .map(chunk -> ServerSentEvent.<String>builder()
                .data(chunk.replace("\n", "\\n").replace("\r", "\\r"))
                .build())
            .onErrorResume(e -> {
                log.error("Dify 聊天流式错误", e);
                return Flux.just(ServerSentEvent.<String>builder()
                    .event("error")
                    .data("Dify 聊天调用失败，请稍后重试")
                    .build());
            });

        return Flux.concat(Flux.just(heartbeat), contentFlux);
    }
}
