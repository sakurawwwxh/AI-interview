package interview.guide.controller;

import interview.guide.dto.*;
import interview.guide.service.InterviewSessionService;
import interview.guide.service.InterviewSessionService.SubmitAnswerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 面试控制器
 * 提供模拟面试相关的API接口
 */
@RestController
@RequestMapping("/api/interview")
@CrossOrigin(origins = "*")
public class InterviewController {
    
    private static final Logger log = LoggerFactory.getLogger(InterviewController.class);
    
    private final InterviewSessionService sessionService;
    
    public InterviewController(InterviewSessionService sessionService) {
        this.sessionService = sessionService;
    }
    
    /**
     * 创建面试会话
     * POST /api/interview/session
     */
    @PostMapping("/session")
    public ResponseEntity<?> createSession(@RequestBody CreateInterviewRequest request) {
        try {
            log.info("创建面试会话，题目数量: {}", request.questionCount());
            InterviewSessionDTO session = sessionService.createSession(request);
            return ResponseEntity.ok(session);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("创建面试会话失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "创建面试会话失败: " + e.getMessage()));
        }
    }
    
    /**
     * 获取会话信息
     * GET /api/interview/session/{sessionId}
     */
    @GetMapping("/session/{sessionId}")
    public ResponseEntity<?> getSession(@PathVariable String sessionId) {
        try {
            InterviewSessionDTO session = sessionService.getSession(sessionId);
            return ResponseEntity.ok(session);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * 获取当前问题
     * GET /api/interview/session/{sessionId}/question
     */
    @GetMapping("/session/{sessionId}/question")
    public ResponseEntity<?> getCurrentQuestion(@PathVariable String sessionId) {
        try {
            InterviewQuestionDTO question = sessionService.getCurrentQuestion(sessionId);
            if (question == null) {
                return ResponseEntity.ok(Map.of(
                    "completed", true,
                    "message", "所有问题已回答完毕"
                ));
            }
            return ResponseEntity.ok(Map.of(
                "completed", false,
                "question", question
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * 提交答案
     * POST /api/interview/answer
     */
    @PostMapping("/answer")
    public ResponseEntity<?> submitAnswer(@RequestBody SubmitAnswerRequest request) {
        try {
            log.info("提交答案: 会话{}, 问题{}", request.sessionId(), request.questionIndex());
            SubmitAnswerResponse response = sessionService.submitAnswer(request);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.error("提交答案失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * 生成面试报告
     * GET /api/interview/session/{sessionId}/report
     */
    @GetMapping("/session/{sessionId}/report")
    public ResponseEntity<?> getReport(@PathVariable String sessionId) {
        try {
            log.info("生成面试报告: {}", sessionId);
            InterviewReportDTO report = sessionService.generateReport(sessionId);
            return ResponseEntity.ok(report);
        } catch (RuntimeException e) {
            log.error("生成报告失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
