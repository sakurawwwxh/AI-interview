package interview.guide.modules.practice.controller;

import interview.guide.common.result.Result;
import interview.guide.modules.practice.model.PracticeDTO;
import interview.guide.modules.practice.model.PracticeTaskStatus;
import interview.guide.modules.practice.service.PracticeTaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/practice")
@RequiredArgsConstructor
public class PracticeController {

    private final PracticeTaskService practiceTaskService;

    @GetMapping("/tasks")
    public Result<PracticeDTO.TaskPage> listTasks(
        @RequestParam(required = false) PracticeTaskStatus status,
        @RequestParam(required = false) String category,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return Result.success(practiceTaskService.listTasks(status, category, page, size));
    }

    @GetMapping("/tasks/{taskId}")
    public Result<PracticeDTO.TaskDetail> getTask(@PathVariable Long taskId) {
        return Result.success(practiceTaskService.getTask(taskId));
    }

    @PostMapping("/tasks/{taskId}/attempts")
    public Result<PracticeDTO.SubmitAttemptResponse> submitAttempt(
        @PathVariable Long taskId,
        @Valid @RequestBody PracticeDTO.SubmitAttemptRequest request
    ) {
        return Result.success(practiceTaskService.submitAttempt(taskId, request));
    }

    @PatchMapping("/tasks/{taskId}")
    public Result<PracticeDTO.TaskItem> updateStatus(
        @PathVariable Long taskId,
        @Valid @RequestBody PracticeDTO.UpdateTaskStatusRequest request
    ) {
        return Result.success(practiceTaskService.updateStatus(taskId, request));
    }

    @GetMapping("/summary")
    public Result<PracticeDTO.Summary> getSummary() {
        return Result.success(practiceTaskService.getSummary());
    }
}
