package interview.guide.modules.dify.controller;

import interview.guide.common.result.Result;
import interview.guide.modules.dify.model.*;
import interview.guide.modules.dify.service.DifyChatService;
import interview.guide.modules.dify.service.DifySyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
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
    private final DifyChatService chatService;

    /**
     * 手动触发同步
     */
    @PostMapping("/sync")
    public Result<Void> manualSync() {
        log.info("手动触发 Dify 同步");
        syncService.syncFromDify();
        return Result.success(null);
    }

    /**
     * 获取同步状态
     */
    @GetMapping("/sync/status")
    public Result<List<DifySyncStatusDTO>> getSyncStatus() {
        return Result.success(syncService.getSyncStatus());
    }

    /**
     * 获取同步日志
     */
    @GetMapping("/sync/logs")
    public Result<List<DifySyncLogEntity>> getSyncLogs(
            @RequestParam(required = false) Long knowledgeBaseId,
            @RequestParam(defaultValue = "50") int limit) {
        return Result.success(syncService.getSyncLogs(knowledgeBaseId, limit));
    }

    /**
     * 基于 Dify 对话
     */
    @PostMapping("/chat")
    public Result<DifyChatResponse> chat(@RequestBody DifyChatRequest request) {
        log.info("Dify 对话请求: query={}, kbIds={}", request.query(), request.knowledgeBaseIds());
        return Result.success(chatService.chat(
            request.query(),
            request.conversationId(),
            request.knowledgeBaseIds()
        ));
    }

    /**
     * 流式对话
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestBody DifyChatRequest request) {
        log.info("Dify 流式对话请求: query={}", request.query());
        return chatService.chatStream(request.query(), request.conversationId());
    }

    /**
     * 运行工作流
     */
    @PostMapping("/workflow/{workflowId}")
    public Result<DifyWorkflowResponse> runWorkflow(
            @PathVariable String workflowId,
            @RequestBody Map<String, Object> inputs) {
        log.info("运行 Dify 工作流: workflowId={}", workflowId);
        // TODO: 实现工作流运行
        return Result.error("工作流功能暂未实现");
    }
}
