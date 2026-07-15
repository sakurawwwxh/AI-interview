package interview.guide.modules.practice.service;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.model.InterviewAnswerEntity;
import interview.guide.modules.interview.service.AnswerEvaluationService;
import interview.guide.modules.practice.model.PracticeAttemptEntity;
import interview.guide.modules.practice.model.PracticeDTO;
import interview.guide.modules.practice.model.PracticeTaskEntity;
import interview.guide.modules.practice.model.PracticeTaskStatus;
import interview.guide.modules.practice.repository.PracticeAttemptRepository;
import interview.guide.modules.practice.repository.PracticeTaskRepository;
import interview.guide.modules.user.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PracticeTaskService {

    private static final int PRACTICE_THRESHOLD = 60;

    private final PracticeTaskRepository taskRepository;
    private final PracticeAttemptRepository attemptRepository;
    private final AnswerEvaluationService evaluationService;
    private final ObjectMapper objectMapper;

    /** Called from the report persistence transaction; source-answer uniqueness makes retries idempotent. */
    public void createTasksForEvaluatedAnswers(Long userId, List<InterviewAnswerEntity> answers) {
        for (InterviewAnswerEntity answer : answers) {
            if (answer.getId() == null || answer.getScore() == null || answer.getScore() >= PRACTICE_THRESHOLD
                || taskRepository.existsBySourceAnswerId(answer.getId())) {
                continue;
            }
            PracticeTaskEntity task = new PracticeTaskEntity();
            task.setUserId(userId);
            task.setSourceAnswerId(answer.getId());
            task.setSourceSessionId(answer.getSession().getSessionId());
            task.setQuestion(answer.getQuestion());
            task.setCategory(answer.getCategory());
            task.setOriginalAnswer(answer.getUserAnswer());
            task.setOriginalScore(answer.getScore());
            task.setOriginalFeedback(answer.getFeedback());
            task.setReferenceAnswer(answer.getReferenceAnswer());
            task.setKeyPointsJson(answer.getKeyPointsJson());
            taskRepository.save(task);
        }
    }

    @Transactional
    public PracticeDTO.TaskPage listTasks(PracticeTaskStatus status, String category, int page, int size) {
        Long userId = UserContext.getCurrentUserIdOrThrow();
        releaseDueReviews(userId);
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 50),
            Sort.by(Sort.Direction.DESC, "createdAt"));
        String normalizedCategory = category == null || category.isBlank() ? null : category.trim();
        Page<PracticeTaskEntity> result;
        if (status != null && normalizedCategory != null) {
            result = taskRepository.findByUserIdAndStatusAndCategoryIgnoreCase(userId, status, normalizedCategory, pageable);
        } else if (status != null) {
            result = taskRepository.findByUserIdAndStatus(userId, status, pageable);
        } else if (normalizedCategory != null) {
            result = taskRepository.findByUserIdAndCategoryIgnoreCase(userId, normalizedCategory, pageable);
        } else {
            result = taskRepository.findByUserId(userId, pageable);
        }
        return new PracticeDTO.TaskPage(result.getContent().stream().map(this::toTaskItem).toList(),
            result.getTotalElements(), result.getNumber(), result.getSize());
    }

    @Transactional(readOnly = true)
    public PracticeDTO.TaskDetail getTask(Long taskId) {
        PracticeTaskEntity task = findOwnedTask(taskId);
        List<PracticeDTO.AttemptItem> attempts = attemptRepository.findByTaskIdOrderByCreatedAtDesc(taskId).stream()
            .map(this::toAttemptItem)
            .toList();
        return new PracticeDTO.TaskDetail(task.getId(), task.getStatus(), task.getQuestion(), task.getCategory(),
            task.getOriginalAnswer(), valueOrZero(task.getOriginalScore()), task.getOriginalFeedback(),
            task.getReferenceAnswer(), parseKeyPoints(task.getKeyPointsJson()), task.getLastScore(), improvement(task),
            valueOrZero(task.getAttemptCount()), task.getNextReviewAt(), task.getReviewIntervalDays(), attempts);
    }

    @Transactional
    public PracticeDTO.SubmitAttemptResponse submitAttempt(Long taskId, PracticeDTO.SubmitAttemptRequest request) {
        PracticeTaskEntity task = findOwnedTask(taskId);
        if (task.getStatus() == PracticeTaskStatus.IGNORED) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请先恢复该复练任务");
        }
        AnswerEvaluationService.PracticeAnswerEvaluation evaluation = evaluationService.evaluatePracticeAnswer(
            String.valueOf(task.getId()), task.getQuestion(), task.getCategory(), request.answer().trim());

        PracticeAttemptEntity attempt = new PracticeAttemptEntity();
        attempt.setAnswer(request.answer().trim());
        attempt.setScore(evaluation.score());
        attempt.setFeedback(evaluation.feedback());
        task.addAttempt(attempt);
        task.setAttemptCount(valueOrZero(task.getAttemptCount()) + 1);
        task.setLastScore(evaluation.score());
        task.setLastPracticedAt(LocalDateTime.now());
        if (evaluation.score() >= PRACTICE_THRESHOLD) {
            task.setStatus(PracticeTaskStatus.COMPLETED);
            task.setCompletedAt(LocalDateTime.now());
            int interval = reviewIntervalFor(evaluation.score());
            task.setReviewIntervalDays(interval);
            task.setNextReviewAt(LocalDateTime.now().plusDays(interval));
        } else {
            task.setStatus(PracticeTaskStatus.TODO);
            task.setCompletedAt(null);
            task.setReviewIntervalDays(0);
            task.setNextReviewAt(LocalDateTime.now());
        }
        taskRepository.save(task);
        return new PracticeDTO.SubmitAttemptResponse(toAttemptItem(attempt), task.getStatus(), improvement(task));
    }

    @Transactional
    public PracticeDTO.TaskItem updateStatus(Long taskId, PracticeDTO.UpdateTaskStatusRequest request) {
        PracticeTaskEntity task = findOwnedTask(taskId);
        if (request.status() == PracticeTaskStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "复练任务只能通过达标作答完成");
        }
        task.setStatus(request.status());
        task.setIgnoredAt(request.status() == PracticeTaskStatus.IGNORED ? LocalDateTime.now() : null);
        if (request.status() == PracticeTaskStatus.TODO) {
            task.setCompletedAt(null);
        }
        return toTaskItem(taskRepository.save(task));
    }

    @Transactional
    public PracticeDTO.Summary getSummary() {
        Long userId = UserContext.getCurrentUserIdOrThrow();
        releaseDueReviews(userId);
        LocalDateTime weekStart = LocalDateTime.now().with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
            .toLocalDate().atStartOfDay();
        Double average = taskRepository.averageImprovementByUserId(userId, PracticeTaskStatus.COMPLETED);
        return new PracticeDTO.Summary(
            taskRepository.countByUserIdAndStatus(userId, PracticeTaskStatus.TODO),
            taskRepository.countByUserIdAndStatus(userId, PracticeTaskStatus.COMPLETED),
            taskRepository.countByUserIdAndStatus(userId, PracticeTaskStatus.IGNORED),
            taskRepository.countByUserIdAndCompletedAtGreaterThanEqual(userId, weekStart),
            average == null ? null : (int) Math.round(average)
        );
    }

    private PracticeTaskEntity findOwnedTask(Long taskId) {
        Long userId = UserContext.getCurrentUserIdOrThrow();
        return taskRepository.findByIdAndUserId(taskId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PRACTICE_TASK_NOT_FOUND));
    }

    private PracticeDTO.TaskItem toTaskItem(PracticeTaskEntity task) {
        return new PracticeDTO.TaskItem(task.getId(), task.getStatus(), task.getQuestion(), task.getCategory(),
            valueOrZero(task.getOriginalScore()), task.getLastScore(), improvement(task), valueOrZero(task.getAttemptCount()),
            task.getCreatedAt(), task.getLastPracticedAt(), task.getNextReviewAt(), task.getReviewIntervalDays());
    }

    private PracticeDTO.AttemptItem toAttemptItem(PracticeAttemptEntity attempt) {
        return new PracticeDTO.AttemptItem(attempt.getId(), attempt.getAnswer(), valueOrZero(attempt.getScore()),
            attempt.getFeedback(), attempt.getCreatedAt());
    }

    private Integer improvement(PracticeTaskEntity task) {
        return task.getLastScore() == null || task.getOriginalScore() == null ? null
            : task.getLastScore() - task.getOriginalScore();
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private int reviewIntervalFor(int score) {
        if (score < 75) return 1;
        if (score < 90) return 3;
        return 7;
    }

    private void releaseDueReviews(Long userId) {
        List<PracticeTaskEntity> due = taskRepository.findByUserIdAndStatusAndNextReviewAtLessThanEqual(
            userId, PracticeTaskStatus.COMPLETED, LocalDateTime.now());
        for (PracticeTaskEntity task : due) {
            task.setStatus(PracticeTaskStatus.TODO);
            task.setCompletedAt(null);
        }
        if (!due.isEmpty()) taskRepository.saveAll(due);
    }

    private List<String> parseKeyPoints(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception ignored) {
            return List.of();
        }
    }
}
