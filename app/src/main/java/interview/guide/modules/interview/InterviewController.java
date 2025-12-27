package interview.guide.modules.interview;

import interview.guide.common.result.Result;
import interview.guide.modules.interview.model.CreateInterviewRequest;
import interview.guide.modules.interview.model.SubmitAnswerRequest;
import interview.guide.modules.interview.model.InterviewQuestionDTO;
import interview.guide.modules.interview.model.InterviewReportDTO;
import interview.guide.modules.interview.model.InterviewSessionDTO;
import interview.guide.modules.interview.model.SubmitAnswerResponse;
import interview.guide.modules.interview.service.InterviewSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 面试控制器
 * 提供模拟面试相关的API接口
 */
@Slf4j
@RestController
@RequestMapping("/api/interview")
@RequiredArgsConstructor
public class InterviewController {
    
    private final InterviewSessionService sessionService;
    
    /**
     * 创建面试会话
     * POST /api/interview/session
     */
    @PostMapping("/session")
    public Result<InterviewSessionDTO> createSession(@RequestBody CreateInterviewRequest request) {
        log.info("创建面试会话，题目数量: {}", request.questionCount());
        InterviewSessionDTO session = sessionService.createSession(request);
        return Result.success(session);
    }
    
    /**
     * 获取会话信息
     * GET /api/interview/session/{sessionId}
     */
    @GetMapping("/session/{sessionId}")
    public Result<InterviewSessionDTO> getSession(@PathVariable String sessionId) {
        InterviewSessionDTO session = sessionService.getSession(sessionId);
        return Result.success(session);
    }
    
    /**
     * 获取当前问题
     * GET /api/interview/session/{sessionId}/question
     */
    @GetMapping("/session/{sessionId}/question")
    public Result<Map<String, Object>> getCurrentQuestion(@PathVariable String sessionId) {
        InterviewQuestionDTO question = sessionService.getCurrentQuestion(sessionId);
        if (question == null) {
            return Result.success(Map.of(
                "completed", true,
                "message", "所有问题已回答完毕"
            ));
        }
        return Result.success(Map.of(
            "completed", false,
            "question", question
        ));
    }
    
    /**
     * 提交答案
     * POST /api/interview/answer
     */
    @PostMapping("/answer")
    public Result<SubmitAnswerResponse> submitAnswer(@RequestBody SubmitAnswerRequest request) {
        log.info("提交答案: 会话{}, 问题{}", request.sessionId(), request.questionIndex());
        SubmitAnswerResponse response = sessionService.submitAnswer(request);
        return Result.success(response);
    }
    
    /**
     * 生成面试报告
     * GET /api/interview/session/{sessionId}/report
     */
    @GetMapping("/session/{sessionId}/report")
    public Result<InterviewReportDTO> getReport(@PathVariable String sessionId) {
        log.info("生成面试报告: {}", sessionId);
        InterviewReportDTO report = sessionService.generateReport(sessionId);
        return Result.success(report);
    }
}
