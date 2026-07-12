package interview.guide.modules.interview.service;

import interview.guide.modules.interview.model.InterviewAnswerEntity;
import interview.guide.modules.interview.model.InterviewQuestionDTO;
import interview.guide.modules.interview.model.InterviewSessionEntity;
import interview.guide.modules.interview.model.InterviewStatisticsDTO;
import interview.guide.modules.interview.repository.InterviewSessionRepository;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InterviewStatisticsServiceTest {

    @Test
    void aggregatesScoresTrendAndWeaknessesFromEvaluatedSessions() throws Exception {
        InterviewSessionRepository repository = mock(InterviewSessionRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();

        InterviewSessionEntity first = session("session-1", 70, LocalDateTime.of(2026, 7, 1, 10, 0), objectMapper,
            question(0, InterviewQuestionDTO.QuestionType.JAVA_BASIC), answer(0, 80));
        InterviewSessionEntity second = session("session-2", 84, LocalDateTime.of(2026, 7, 2, 10, 0), objectMapper,
            question(0, InterviewQuestionDTO.QuestionType.JAVA_BASIC), answer(0, 90),
            question(1, InterviewQuestionDTO.QuestionType.REDIS), answer(1, 50));
        when(repository.findEvaluatedWithAnswers()).thenReturn(List.of(first, second));

        InterviewStatisticsDTO statistics = new InterviewStatisticsService(repository, objectMapper).getStatistics();

        assertEquals(2, statistics.completedInterviewCount());
        assertEquals(77, statistics.averageScore());
        assertEquals(84, statistics.latestScore());
        assertEquals(14, statistics.scoreChange());
        assertEquals(2, statistics.abilityScores().size());
        assertEquals("Redis", statistics.weaknesses().getFirst().category());
        assertEquals(50, statistics.weaknesses().getFirst().averageScore());
    }

    @Test
    void returnsAnEmptyDashboardWhenNoSessionHasBeenEvaluated() {
        InterviewSessionRepository repository = mock(InterviewSessionRepository.class);
        when(repository.findEvaluatedWithAnswers()).thenReturn(List.of());

        InterviewStatisticsDTO statistics = new InterviewStatisticsService(repository, new ObjectMapper()).getStatistics();

        assertEquals(0, statistics.completedInterviewCount());
        assertNull(statistics.latestScore());
        assertNull(statistics.scoreChange());
        assertEquals(List.of(), statistics.abilityScores());
    }

    private InterviewSessionEntity session(String sessionId, int score, LocalDateTime completedAt, ObjectMapper objectMapper,
                                           Object... questionAndAnswers) throws Exception {
        InterviewSessionEntity session = new InterviewSessionEntity();
        session.setSessionId(sessionId);
        session.setOverallScore(score);
        session.setCreatedAt(completedAt.minusMinutes(10));
        session.setCompletedAt(completedAt);

        List<InterviewQuestionDTO> questions = new java.util.ArrayList<>();
        for (int i = 0; i < questionAndAnswers.length; i += 2) {
            questions.add((InterviewQuestionDTO) questionAndAnswers[i]);
            session.addAnswer((InterviewAnswerEntity) questionAndAnswers[i + 1]);
        }
        session.setQuestionsJson(objectMapper.writeValueAsString(questions));
        return session;
    }

    private InterviewQuestionDTO question(int index, InterviewQuestionDTO.QuestionType type) {
        return InterviewQuestionDTO.create(index, "question", type, type.name());
    }

    private InterviewAnswerEntity answer(int index, int score) {
        InterviewAnswerEntity answer = new InterviewAnswerEntity();
        answer.setQuestionIndex(index);
        answer.setScore(score);
        return answer;
    }
}
