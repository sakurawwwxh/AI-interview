package interview.guide.common.ai;

import interview.guide.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 用量查询控制器
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiUsageController {

    private final AiUsageRecorder usageRecorder;

    /**
     * 获取当前 AI 用量统计
     */
    @GetMapping("/usage")
    public Result<AiUsageDTO> getUsage() {
        return Result.success(usageRecorder.getUsageStats());
    }
}
