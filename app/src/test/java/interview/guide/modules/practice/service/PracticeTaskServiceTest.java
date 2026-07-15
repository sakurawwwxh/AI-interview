package interview.guide.modules.practice.service;

import interview.guide.modules.interview.model.InterviewAnswerEntity;
import interview.guide.modules.interview.model.InterviewSessionEntity;
import interview.guide.modules.interview.service.AnswerEvaluationService;
import interview.guide.common.exception.BusinessException;
import interview.guide.modules.practice.model.PracticeDTO;
import interview.guide.modules.practice.model.PracticeTaskEntity;
import interview.guide.modules.practice.model.PracticeTaskStatus;
import interview.guide.modules.practice.repository.PracticeAttemptRepository;
import interview.guide.modules.practice.repository.PracticeTaskRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PracticeTaskServiceTest {

    private final PracticeTaskRepository taskRepository = mock(PracticeTaskRepository.class);
    private final PracticeAttemptRepository attemptRepository = mock(PracticeAttemptRepository.class);
    private final AnswerEvaluationService evaluationService = mock(AnswerEvaluationService.class);
    private final PracticeTaskService service = new PracticeTaskService(taskRepository, attemptRepository,
        evaluationService, new ObjectMapper());

    @BeforeEach
    void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(1L, "tester", List.of()));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createsOnlyOneTaskForEachLowScoreSourceAnswer() {
        InterviewSessionEntity session = new InterviewSessionEntity();
        session.setSessionId("session-1");
        InterviewAnswerEntity lowScore = answer(11L, session, 45);
        InterviewAnswerEntity passing = answer(12L, session, 80);
        when(taskRepository.existsBySourceAnswerId(11L)).thenReturn(false);

        service.createTasksForEvaluatedAnswers(1L, List.of(lowScore, passing));

        verify(taskRepository).save(any(PracticeTaskEntity.class));
        verify(taskRepository, never()).existsBySourceAnswerId(12L);
        when(taskRepository.existsBySourceAnswerId(11L)).thenReturn(true);
        service.createTasksForEvaluatedAnswers(1L, List.of(lowScore));
        verify(taskRepository, times(1)).save(any(PracticeTaskEntity.class));
    }

    @Test
    void passingRetryCompletesTaskAndRecordsImprovement() {
        PracticeTaskEntity task = task(5L, 40, PracticeTaskStatus.TODO);
        when(taskRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(task));
        when(evaluationService.evaluatePracticeAnswer("5", "question", "Java", "better answer"))
            .thenReturn(new AnswerEvaluationService.PracticeAnswerEvaluation(78, "feedback"));
        when(taskRepository.save(task)).thenReturn(task);

        PracticeDTO.SubmitAttemptResponse response = service.submitAttempt(5L,
            new PracticeDTO.SubmitAttemptRequest("better answer"));

        assertEquals(PracticeTaskStatus.COMPLETED, response.taskStatus());
        assertEquals(38, response.improvement());
        assertEquals(1, task.getAttemptCount());
        assertEquals(78, task.getLastScore());
        assertEquals(1, task.getAttempts().size());
    }

    @Test
    void otherUsersCannotReadTask() {
        when(taskRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> service.getTask(5L));

        verify(attemptRepository, never()).findByTaskIdOrderByCreatedAtDesc(any());
    }

    @Test
    void ignoredTaskCanBeRestoredButCannotBeManuallyCompleted() {
        PracticeTaskEntity task = task(5L, 40, PracticeTaskStatus.TODO);
        when(taskRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(task));
        when(taskRepository.save(task)).thenReturn(task);

        service.updateStatus(5L, new PracticeDTO.UpdateTaskStatusRequest(PracticeTaskStatus.IGNORED));
        assertEquals(PracticeTaskStatus.IGNORED, task.getStatus());
        service.updateStatus(5L, new PracticeDTO.UpdateTaskStatusRequest(PracticeTaskStatus.TODO));
        assertEquals(PracticeTaskStatus.TODO, task.getStatus());
        assertThrows(BusinessException.class, () -> service.updateStatus(5L,
            new PracticeDTO.UpdateTaskStatusRequest(PracticeTaskStatus.COMPLETED)));
    }

    @Test
    void unsuccessfulRetryStaysTodo() {
        PracticeTaskEntity task = task(5L, 40, PracticeTaskStatus.TODO);
        when(taskRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(task));
        when(evaluationService.evaluatePracticeAnswer("5", "question", "Java", "retry"))
            .thenReturn(new AnswerEvaluationService.PracticeAnswerEvaluation(55, "feedback"));
        when(taskRepository.save(task)).thenReturn(task);

        PracticeDTO.SubmitAttemptResponse response = service.submitAttempt(5L,
            new PracticeDTO.SubmitAttemptRequest("retry"));

        assertEquals(PracticeTaskStatus.TODO, response.taskStatus());
        assertEquals(15, response.improvement());
    }

    private InterviewAnswerEntity answer(Long id, InterviewSessionEntity session, int score) {
        InterviewAnswerEntity answer = new InterviewAnswerEntity();
        answer.setId(id); answer.setSession(session); answer.setQuestion("question"); answer.setCategory("Java");
        answer.setUserAnswer("old answer"); answer.setScore(score); answer.setFeedback("old feedback");
        return answer;
    }

    private PracticeTaskEntity task(Long id, int originalScore, PracticeTaskStatus status) {
        PracticeTaskEntity task = new PracticeTaskEntity();
        task.setId(id); task.setUserId(1L); task.setQuestion("question"); task.setCategory("Java");
        task.setOriginalScore(originalScore); task.setStatus(status); task.setAttemptCount(0);
        return task;
    }
}
