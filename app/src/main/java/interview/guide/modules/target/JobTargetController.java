package interview.guide.modules.target;

import interview.guide.common.annotation.RateLimit;
import interview.guide.common.result.Result;
import interview.guide.modules.target.model.*;
import interview.guide.modules.target.service.JobMatchService;
import interview.guide.modules.target.service.JobTargetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequiredArgsConstructor @RequestMapping("/api/job-targets")
public class JobTargetController {
    private final JobTargetService targetService; private final JobMatchService matchService;
    @GetMapping public Result<List<JobTargetDTO>> list() { return Result.success(targetService.list()); }
    @PostMapping public Result<JobTargetDTO> create(@Valid @RequestBody JobTargetRequest request) { return Result.success(targetService.create(request)); }
    @PutMapping("/{id}") public Result<JobTargetDTO> update(@PathVariable Long id, @Valid @RequestBody JobTargetRequest request) { return Result.success(targetService.update(id, request)); }
    @PatchMapping("/{id}/activate") public Result<JobTargetDTO> activate(@PathVariable Long id) { return Result.success(targetService.activate(id)); }
    @DeleteMapping("/{id}") public Result<Void> delete(@PathVariable Long id) { targetService.delete(id); return Result.success(null); }
    @PostMapping("/{id}/resumes/{resumeId}/match") @RateLimit(dimensions = {RateLimit.Dimension.GLOBAL, RateLimit.Dimension.IP}, count = 3)
    public Result<JobMatchDTO> match(@PathVariable Long id, @PathVariable Long resumeId) { return Result.success(matchService.match(id, resumeId)); }
}
