package ai.interview.modules.growth;

import ai.interview.common.result.Result;
import ai.interview.modules.growth.model.GrowthPlanDTO;
import ai.interview.modules.growth.service.GrowthPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/growth")
@RequiredArgsConstructor
public class GrowthPlanController {
    private final GrowthPlanService growthPlanService;
    @GetMapping("/plan")
    public Result<GrowthPlanDTO> getPlan() { return Result.success(growthPlanService.getPlan()); }
}
