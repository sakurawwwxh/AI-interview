package interview.guide.modules.interview.service;

import interview.guide.modules.interview.model.InterviewStatisticsDTO;
import interview.guide.modules.interview.repository.InterviewAnswerRepository;
import interview.guide.modules.interview.repository.InterviewAnswerRepository.AnswerScoreRow;
import interview.guide.modules.interview.repository.InterviewSessionRepository;
import interview.guide.modules.interview.repository.InterviewSessionRepository.StatisticsSessionRow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InterviewStatisticsServiceTest {

    @BeforeEach
    void setUpSecurityContext() {
        // 模拟已登录用户（userId=1L）
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(1L, "testuser", List.of()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("聚合趋势、能力分与薄弱项（仓储按完成时间倒序返回，服务翻回正序展示）")
    void aggregatesScoresTrendAndWeaknessesFromEvaluatedSessions() {
        InterviewSessionRepository repository = mock(InterviewSessionRepository.class);
        InterviewAnswerRepository answerRepository = mock(InterviewAnswerRepository.class);

        // 仓储约定：按 completedAt 倒序返回最近的场次
        when(repository.findStatisticsSessions(1L, 100)).thenReturn(List.of(
            row(2L, "session-2", 84, LocalDateTime.of(2026, 7, 2, 10, 0), "0:JAVA_BASIC,1:REDIS"),
            row(1L, "session-1", 70, LocalDateTime.of(2026, 7, 1, 10, 0), "0:JAVA_BASIC")
        ));
        when(answerRepository.findAnswerScoresBySessionIds(List.of(1L, 2L))).thenReturn(List.of(
            answer(1L, 0, null, 80),
            answer(2L, 0, null, 90),
            answer(2L, 1, null, 50)
        ));

        InterviewStatisticsDTO statistics =
            new InterviewStatisticsService(repository, answerRepository).getStatistics();

        assertEquals(2, statistics.completedInterviewCount());
        assertEquals(77, statistics.averageScore());
        assertEquals(84, statistics.latestScore());
        assertEquals(14, statistics.scoreChange());
        assertEquals(2, statistics.abilityScores().size());
        assertEquals("Redis", statistics.weaknesses().getFirst().category());
        assertEquals(50, statistics.weaknesses().getFirst().averageScore());
        // 趋势必须是时间正序：最新一场在最后
        assertEquals(List.of("session-1", "session-2"),
            statistics.scoreTrend().stream().map(InterviewStatisticsDTO.TrendPoint::sessionId).toList());
        verify(repository).findStatisticsSessions(eq(1L), eq(100));
    }

    @Test
    @DisplayName("题目类型串缺失时回退到答案自身的 category（按 - 截断）")
    void fallsBackToAnswerCategoryWhenQuestionTypesAreMissing() {
        InterviewSessionRepository repository = mock(InterviewSessionRepository.class);
        InterviewAnswerRepository answerRepository = mock(InterviewAnswerRepository.class);

        // questions_json 缺失 / 损坏时仓储返回空串
        when(repository.findStatisticsSessions(1L, 100))
            .thenReturn(List.of(row(1L, "session-1", 80, LocalDateTime.of(2026, 7, 1, 10, 0), "")));
        when(answerRepository.findAnswerScoresBySessionIds(List.of(1L)))
            .thenReturn(List.of(answer(1L, 0, "Java Concurrency - 线程池与锁", 60)));

        InterviewStatisticsDTO statistics =
            new InterviewStatisticsService(repository, answerRepository).getStatistics();

        assertEquals(1, statistics.abilityScores().size());
        assertEquals("Java Concurrency", statistics.abilityScores().getFirst().category());
    }

    @Test
    @DisplayName("题目类型串不可解析时整场回退且不抛异常")
    void degradesToAnswerCategoryWhenQuestionTypesAreUnparseable() {
        InterviewSessionRepository repository = mock(InterviewSessionRepository.class);
        InterviewAnswerRepository answerRepository = mock(InterviewAnswerRepository.class);

        // 例如该题类型是新版本枚举，旧服务端不认识
        when(repository.findStatisticsSessions(1L, 100))
            .thenReturn(List.of(row(1L, "session-1", 80, LocalDateTime.of(2026, 7, 1, 10, 0), "0:NOT_A_REAL_TYPE")));
        when(answerRepository.findAnswerScoresBySessionIds(List.of(1L)))
            .thenReturn(List.of(answer(1L, 0, "Redis - 限流", 60)));

        InterviewStatisticsDTO statistics =
            new InterviewStatisticsService(repository, answerRepository).getStatistics();

        assertEquals("Redis", statistics.abilityScores().getFirst().category());
    }

    @Test
    @DisplayName("题号缺失的题目按 null 键归档，不会串到其它题号上")
    void keepsMissingQuestionIndexFromLeakingToOtherQuestions() {
        InterviewSessionRepository repository = mock(InterviewSessionRepository.class);
        InterviewAnswerRepository answerRepository = mock(InterviewAnswerRepository.class);

        // 第 0 题没有 questionIndex（类型串里题号为空），第 1 题是 REDIS
        when(repository.findStatisticsSessions(1L, 100))
            .thenReturn(List.of(row(1L, "session-1", 80, LocalDateTime.of(2026, 7, 1, 10, 0), ":PROJECT,1:REDIS")));
        when(answerRepository.findAnswerScoresBySessionIds(List.of(1L)))
            .thenReturn(List.of(answer(1L, 1, "Redis - 限流", 60)));

        InterviewStatisticsDTO statistics =
            new InterviewStatisticsService(repository, answerRepository).getStatistics();

        // 第 1 题取到 REDIS 标签，而不是被空题号那条串走
        assertEquals("Redis", statistics.abilityScores().getFirst().category());
    }

    @Test
    @DisplayName("没有任何已评分会话时返回空看板，且不再查答案表")
    void returnsAnEmptyDashboardWhenNoSessionHasBeenEvaluated() {
        InterviewSessionRepository repository = mock(InterviewSessionRepository.class);
        InterviewAnswerRepository answerRepository = mock(InterviewAnswerRepository.class);
        when(repository.findStatisticsSessions(eq(1L), eq(100))).thenReturn(List.of());

        InterviewStatisticsDTO statistics =
            new InterviewStatisticsService(repository, answerRepository).getStatistics();

        assertEquals(0, statistics.completedInterviewCount());
        assertEquals(0, statistics.averageScore());
        assertNull(statistics.latestScore());
        assertNull(statistics.scoreChange());
        assertEquals(List.of(), statistics.abilityScores());
        assertEquals(List.of(), statistics.weaknesses());
        assertEquals(List.of(), statistics.scoreTrend());
        verify(answerRepository, never()).findAnswerScoresBySessionIds(any());
    }

    private StatisticsSessionRow row(Long id, String sessionId, int score, LocalDateTime completedAt, String questionTypes) {
        return new StatisticsSessionRow() {
            @Override
            public Long getId() {
                return id;
            }

            @Override
            public String getSessionId() {
                return sessionId;
            }

            @Override
            public LocalDateTime getCompletedAt() {
                return completedAt;
            }

            @Override
            public LocalDateTime getCreatedAt() {
                return completedAt.minusMinutes(10);
            }

            @Override
            public Integer getOverallScore() {
                return score;
            }

            @Override
            public String getQuestionTypes() {
                return questionTypes;
            }
        };
    }

    private AnswerScoreRow answer(Long sessionId, Integer questionIndex, String category, Integer score) {
        return new AnswerScoreRow() {
            @Override
            public Long getSessionId() {
                return sessionId;
            }

            @Override
            public Integer getQuestionIndex() {
                return questionIndex;
            }

            @Override
            public String getCategory() {
                return category;
            }

            @Override
            public Integer getScore() {
                return score;
            }
        };
    }
}
