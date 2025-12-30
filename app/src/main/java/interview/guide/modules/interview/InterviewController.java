package interview.guide.modules.interview;

import interview.guide.common.result.Result;
import interview.guide.modules.interview.model.*;
import interview.guide.modules.interview.service.InterviewHistoryService;
import interview.guide.modules.interview.service.InterviewPersistenceService;
import interview.guide.modules.interview.service.InterviewSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 面试控制器
 * 提供模拟面试相关的API接口
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class InterviewController {
    
    private final InterviewSessionService sessionService;
    private final InterviewHistoryService historyService;
    private final InterviewPersistenceService persistenceService;
    
    /**
     * 创建面试会话
     * POST /api/interview/session
     */
    @PostMapping("/api/interview/session")
    public Result<InterviewSessionDTO> createSession(@RequestBody CreateInterviewRequest request) {
        log.info("创建面试会话，题目数量: {}", request.questionCount());
        InterviewSessionDTO session = sessionService.createSession(request);
        return Result.success(session);
    }
    
    /**
     * 获取会话信息
     * GET /api/interview/session/{sessionId}
     */
    @GetMapping("/api/interview/session/{sessionId}")
    public Result<InterviewSessionDTO> getSession(@PathVariable String sessionId) {
        InterviewSessionDTO session = sessionService.getSession(sessionId);
        return Result.success(session);
    }
    
    /**
     * 获取当前问题
     * GET /api/interview/session/{sessionId}/question
     */
    @GetMapping("/api/interview/session/{sessionId}/question")
    public Result<Map<String, Object>> getCurrentQuestion(@PathVariable String sessionId) {
        return Result.success(sessionService.getCurrentQuestionResponse(sessionId));
    }
    
    /**
     * 提交答案
     * POST /api/interview/answer
     */
    @PostMapping("/api/interview/answer")
    public Result<SubmitAnswerResponse> submitAnswer(@RequestBody SubmitAnswerRequest request) {
        log.info("提交答案: 会话{}, 问题{}", request.sessionId(), request.questionIndex());
        SubmitAnswerResponse response = sessionService.submitAnswer(request);
        return Result.success(response);
    }
    
    /**
     * 生成面试报告
     * GET /api/interview/session/{sessionId}/report
     */
    @GetMapping("/api/interview/session/{sessionId}/report")
    public Result<InterviewReportDTO> getReport(@PathVariable String sessionId) {
        log.info("生成面试报告: {}", sessionId);
        InterviewReportDTO report = sessionService.generateReport(sessionId);
        return Result.success(report);
    }
    
    /**
     * 查找未完成的面试会话
     * GET /api/interview/unfinished/{resumeId}
     */
    @GetMapping("/api/interview/unfinished/{resumeId}")
    public Result<InterviewSessionDTO> findUnfinishedSession(@PathVariable Long resumeId) {
        return Result.success(sessionService.findUnfinishedSessionOrThrow(resumeId));
    }
    
    /**
     * 暂存答案（不进入下一题）
     * POST /api/interview/save-answer
     */
    @PostMapping("/api/interview/save-answer")
    public Result<Void> saveAnswer(@RequestBody SubmitAnswerRequest request) {
        log.info("暂存答案: 会话{}, 问题{}", request.sessionId(), request.questionIndex());
        sessionService.saveAnswer(request);
        return Result.success(null);
    }
    
    /**
     * 提前交卷
     * POST /api/interview/{sessionId}/complete
     */
    @PostMapping("/api/interview/{sessionId}/complete")
    public Result<Void> completeInterview(@PathVariable String sessionId) {
        log.info("提前交卷: {}", sessionId);
        sessionService.completeInterview(sessionId);
        return Result.success(null);
    }
    
    /**
     * 获取面试会话详情
     * GET /api/interview/{sessionId}/detail
     */
    @GetMapping("/api/interview/{sessionId}/detail")
    public Result<InterviewDetailDTO> getInterviewDetail(@PathVariable String sessionId) {
        InterviewDetailDTO detail = historyService.getInterviewDetail(sessionId);
        return Result.success(detail);
    }
    
    /**
     * 导出面试报告为PDF
     * GET /api/interview/{sessionId}/export
     */
    @GetMapping("/api/interview/{sessionId}/export")
    public ResponseEntity<byte[]> exportInterviewPdf(@PathVariable String sessionId) {
        try {
            byte[] pdfBytes = historyService.exportInterviewPdf(sessionId);
            String filename = URLEncoder.encode("模拟面试报告_" + sessionId + ".pdf", 
                StandardCharsets.UTF_8);
            
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
        } catch (Exception e) {
            log.error("导出PDF失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 删除面试会话
     * DELETE /api/interview/{sessionId}
     */
    @DeleteMapping("/api/interview/{sessionId}")
    public Result<Void> deleteInterview(@PathVariable String sessionId) {
        log.info("删除面试会话: {}", sessionId);
        persistenceService.deleteSessionBySessionId(sessionId);
        return Result.success(null);
    }
}
