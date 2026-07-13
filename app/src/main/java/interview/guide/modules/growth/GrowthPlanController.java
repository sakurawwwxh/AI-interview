package interview.guide.modules.growth;

import interview.guide.common.result.Result;
import interview.guide.modules.growth.model.GrowthPlanDTO;
import interview.guide.modules.growth.service.GrowthPlanService;
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
