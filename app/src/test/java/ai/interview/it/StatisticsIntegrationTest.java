package ai.interview.it;

import ai.interview.modules.interview.model.InterviewAnswerEntity;
import ai.interview.modules.interview.model.InterviewQuestionDTO;
import ai.interview.modules.interview.model.InterviewSessionEntity;
import ai.interview.modules.interview.model.InterviewStatisticsDTO;
import ai.interview.modules.interview.repository.InterviewAnswerRepository;
import ai.interview.modules.interview.repository.InterviewAnswerRepository.AnswerScoreRow;
import ai.interview.modules.interview.repository.InterviewSessionRepository;
import ai.interview.modules.interview.repository.InterviewSessionRepository.StatisticsSessionRow;
import ai.interview.modules.interview.service.InterviewStatisticsService;
import ai.interview.modules.resume.model.ResumeEntity;
import ai.interview.modules.resume.repository.ResumeRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 统计看板读路径的集成测试：真实数据库 + 真实 SQL。
 *
 * <p>这里要挡住的正是单测挡不住的一类问题——原生查询的列别名映射、在库侧用
 * PostgreSQL JSON 函数把 questions_json 裁成「题号:类型」、limit 参数绑定、
 * 以及损坏 JSON 的降级。单测里这些全被 mock 掉了，只有打真库才能验证接线是通的。
 *
 * <p>用例数据库是隔离的 {@code interview_guide_it}（见 {@link IntegrationTestDatabase}），
 * 以 {@code create-drop} 自建自销，业务库不受影响。数据库不可达时整类跳过。
 */
@SpringBootTest
@Import(StubEmbeddingConfiguration.class)
@EnabledIf("ai.interview.it.IntegrationTestDatabase#available")
@DisplayName("统计看板读路径 · 集成测试")
class StatisticsIntegrationTest {

    private static final long USER_ID = 9001L;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @DynamicPropertySource
    static void useIsolatedDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", IntegrationTestDatabase::jdbcUrl);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.ai.openai.api-key", () -> "it-placeholder-key");
    }

    @Autowired
    private InterviewSessionRepository sessionRepository;

    @Autowired
    private InterviewAnswerRepository answerRepository;

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private InterviewStatisticsService statisticsService;

    @BeforeEach
    void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(USER_ID, "it", List.of()));
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        answerRepository.deleteAll();
        sessionRepository.deleteAll();
        resumeRepository.deleteAll();
    }

    @Test
    @DisplayName("库侧把 questions_json 裁成「题号:类型」，不再整段搬到应用层")
    void extractsQuestionTypesInsideTheDatabase() throws Exception {
        ResumeEntity resume = saveResume();
        saveSession(resume, "it-session-1", 70, LocalDateTime.now().minusDays(2), List.of(
            questionRow(0, InterviewQuestionDTO.QuestionType.PROJECT, 80),
            questionRow(1, InterviewQuestionDTO.QuestionType.JAVA_CONCURRENT, 50)
        ));

        List<StatisticsSessionRow> rows = sessionRepository.findStatisticsSessions(USER_ID, 100);

        assertEquals(1, rows.size());
        assertEquals("it-session-1", rows.getFirst().getSessionId());
        assertEquals(70, rows.getFirst().getOverallScore());
        // 这里同时锁定两件事：SQL 提取出的编码格式，以及 CATEGORY_LABELS 用的枚举名
        assertEquals("0:PROJECT,1:JAVA_CONCURRENT", rows.getFirst().getQuestionTypes());
    }

    @Test
    @DisplayName("只投影聚合需要的窄列，大文本不出库")
    void projectsOnlyTheColumnsAggregationNeeds() throws Exception {
        ResumeEntity resume = saveResume();
        InterviewSessionEntity session = saveSession(resume, "it-session-1", 70, LocalDateTime.now(), List.of(
            questionRow(0, InterviewQuestionDTO.QuestionType.REDIS, 60)
        ));

        List<AnswerScoreRow> rows = answerRepository.findAnswerScoresBySessionIds(List.of(session.getId()));

        assertEquals(1, rows.size());
        assertEquals(session.getId(), rows.getFirst().getSessionId());
        assertEquals(0, rows.getFirst().getQuestionIndex());
        assertEquals(60, rows.getFirst().getScore());
    }

    @Test
    @DisplayName("已评分会话里 score 为空的行不参与聚合，也不被取回")
    void skipsAnswersWithoutScore() throws Exception {
        ResumeEntity resume = saveResume();
        InterviewSessionEntity session = saveSession(resume, "it-session-1", 70, LocalDateTime.now(), List.of(
            questionRow(0, InterviewQuestionDTO.QuestionType.REDIS, 88)
        ));
        saveAnswer(session, 1, InterviewQuestionDTO.QuestionType.REDIS, null);

        List<AnswerScoreRow> rows = answerRepository.findAnswerScoresBySessionIds(List.of(session.getId()));

        assertEquals(1, rows.size());
        assertEquals(88, rows.getFirst().getScore());
    }

    @Test
    @DisplayName("limit 生效：只取最近的场次，并按完成时间倒序返回")
    void takesTheMostRecentSessionsUpToTheLimit() throws Exception {
        ResumeEntity resume = saveResume();
        LocalDateTime now = LocalDateTime.now();
        saveSession(resume, "it-old", 60, now.minusDays(3), List.of(questionRow(0, InterviewQuestionDTO.QuestionType.REDIS, 60)));
        saveSession(resume, "it-mid", 70, now.minusDays(2), List.of(questionRow(0, InterviewQuestionDTO.QuestionType.REDIS, 70)));
        saveSession(resume, "it-new", 80, now.minusDays(1), List.of(questionRow(0, InterviewQuestionDTO.QuestionType.REDIS, 80)));

        List<StatisticsSessionRow> rows = sessionRepository.findStatisticsSessions(USER_ID, 2);

        assertEquals(List.of("it-new", "it-mid"),
            rows.stream().map(StatisticsSessionRow::getSessionId).toList());
    }

    @Test
    @DisplayName("统计聚合：趋势时间正序、最新分与变化量取自最近两场")
    void aggregatesTrendInChronologicalOrder() throws Exception {
        ResumeEntity resume = saveResume();
        LocalDateTime now = LocalDateTime.now();
        saveSession(resume, "it-old", 60, now.minusDays(3), List.of(questionRow(0, InterviewQuestionDTO.QuestionType.REDIS, 60)));
        saveSession(resume, "it-mid", 70, now.minusDays(2), List.of(questionRow(0, InterviewQuestionDTO.QuestionType.JAVA_BASIC, 70)));
        saveSession(resume, "it-new", 80, now.minusDays(1), List.of(questionRow(0, InterviewQuestionDTO.QuestionType.REDIS, 80)));

        InterviewStatisticsDTO statistics = statisticsService.getStatistics();

        assertEquals(3, statistics.completedInterviewCount());
        assertEquals(70, statistics.averageScore());
        assertEquals(80, statistics.latestScore());
        assertEquals(10, statistics.scoreChange());
        assertEquals(List.of("it-old", "it-mid", "it-new"),
            statistics.scoreTrend().stream().map(InterviewStatisticsDTO.TrendPoint::sessionId).toList());
        // 能力分按分类名排序，所以按分类取值而不是按位置取
        assertEquals(2, statistics.abilityScores().size());
        assertEquals(70, categoryOf(statistics, "Java 基础").averageScore());
        assertEquals(1, categoryOf(statistics, "Java 基础").questionCount());
        // 两场 Redis 题分别是 60 与 80，跨会话聚合到同一个分类下
        assertEquals(70, categoryOf(statistics, "Redis").averageScore());
        assertEquals(2, categoryOf(statistics, "Redis").questionCount());
    }

    @Test
    @DisplayName("questions_json 缺失、非法或不是数组时都不报错，退化为按答案自身 category 归类")
    void degradesGracefullyOnUnusableQuestionsJson() throws Exception {
        ResumeEntity resume = saveResume();
        LocalDateTime now = LocalDateTime.now();
        saveSessionWithRawQuestionsJson(resume, "it-null", 60, now.minusDays(3), null,
            List.of(questionRow(0, InterviewQuestionDTO.QuestionType.REDIS, 60)));
        // 合法 JSON，但不是数组
        saveSessionWithRawQuestionsJson(resume, "it-object", 70, now.minusDays(2), "{\"not\":\"an array\"}",
            List.of(questionRow(0, InterviewQuestionDTO.QuestionType.REDIS, 70)));
        // 根本不是 JSON
        saveSessionWithRawQuestionsJson(resume, "it-garbage", 80, now.minusDays(1), "not json at all",
            List.of(questionRow(0, InterviewQuestionDTO.QuestionType.REDIS, 80)));

        List<StatisticsSessionRow> rows = sessionRepository.findStatisticsSessions(USER_ID, 100);

        assertEquals(3, rows.size());
        assertTrue(rows.stream().allMatch(row -> row.getQuestionTypes().isEmpty()));

        InterviewStatisticsDTO statistics = statisticsService.getStatistics();
        assertEquals(3, statistics.completedInterviewCount());
        assertEquals(1, statistics.abilityScores().size());
        // 类型串拿不到时用题目自身带的 category，并去掉 " - 别名" 后缀；
        // 此时得到的是原样前缀（REDIS），不是 questions_json 路径下的中文标签（Redis）——
        // 这是改造前就有的行为，这里把它钉住，避免后续误以为是回归。
        assertEquals("REDIS", statistics.abilityScores().getFirst().category());
    }

    private InterviewStatisticsDTO.CategoryScore categoryOf(InterviewStatisticsDTO statistics, String category) {
        return statistics.abilityScores().stream()
            .filter(item -> item.category().equals(category))
            .findFirst()
            .orElseThrow(() -> new AssertionError("未找到分类 " + category));
    }

    @Test
    @DisplayName("已评分的空会话只出现在趋势里，不产生能力分")
    void sessionWithoutScoredAnswersContributesOnlyToTrend() throws Exception {
        ResumeEntity resume = saveResume();
        saveSession(resume, "it-empty", 66, LocalDateTime.now().minusDays(1), List.of());

        InterviewStatisticsDTO statistics = statisticsService.getStatistics();

        assertEquals(1, statistics.completedInterviewCount());
        assertEquals(1, statistics.scoreTrend().size());
        assertTrue(statistics.abilityScores().isEmpty());
        assertEquals(66, statistics.latestScore());
    }

    @Test
    @DisplayName("未评分的会话（overall_score 为空）完全不进入统计")
    void ignoresSessionsWithoutOverallScore() throws Exception {
        ResumeEntity resume = saveResume();
        saveSession(resume, "it-evaluated", 75, LocalDateTime.now().minusDays(1),
            List.of(questionRow(0, InterviewQuestionDTO.QuestionType.REDIS, 75)));
        InterviewSessionEntity inProgress = saveSession(resume, "it-in-progress", null, LocalDateTime.now(),
            List.of(questionRow(0, InterviewQuestionDTO.QuestionType.REDIS, 90)));

        List<StatisticsSessionRow> rows = sessionRepository.findStatisticsSessions(USER_ID, 100);

        assertEquals(1, rows.size());
        assertEquals("it-evaluated", rows.getFirst().getSessionId());
        assertFalse(rows.stream().anyMatch(row -> row.getId().equals(inProgress.getId())));
    }

    // ---------- 造数 ----------

    private ResumeEntity saveResume() {
        ResumeEntity resume = new ResumeEntity();
        resume.setUserId(USER_ID);
        resume.setFileHash("it-hash-" + java.util.UUID.randomUUID());
        resume.setOriginalFilename("it-resume.pdf");
        resume.setUploadedAt(LocalDateTime.now());
        return resumeRepository.save(resume);
    }

    /** 一条题目 + 对应答案：题目决定库侧提取出的 type，答案携带 score 与自身的 category。 */
    private QuestionRow questionRow(int index, InterviewQuestionDTO.QuestionType type, Integer score) {
        return new QuestionRow(index, type, score);
    }

    private record QuestionRow(int index, InterviewQuestionDTO.QuestionType type, Integer score) {
    }

    private InterviewSessionEntity saveSession(ResumeEntity resume, String sessionId, Integer overallScore,
                                               LocalDateTime completedAt, List<QuestionRow> rows) {
        List<InterviewQuestionDTO> questions = rows.stream()
            .map(row -> InterviewQuestionDTO.create(row.index(), "题目 " + row.index() + " 的正文", row.type(), row.type().name()))
            .toList();
        try {
            return saveSessionWithRawQuestionsJson(resume, sessionId, overallScore, completedAt,
                MAPPER.writeValueAsString(questions), rows);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private InterviewSessionEntity saveSessionWithRawQuestionsJson(ResumeEntity resume, String sessionId,
                                                                   Integer overallScore, LocalDateTime completedAt,
                                                                   String questionsJson, List<QuestionRow> rows) {
        InterviewSessionEntity session = new InterviewSessionEntity();
        session.setUserId(USER_ID);
        session.setSessionId(sessionId);
        session.setResume(resume);
        session.setTotalQuestions(rows.size());
        session.setOverallScore(overallScore);
        session.setCreatedAt(completedAt.minusMinutes(10));
        session.setCompletedAt(completedAt);
        session.setQuestionsJson(questionsJson);
        InterviewSessionEntity saved = sessionRepository.save(session);
        for (QuestionRow row : rows) {
            saveAnswer(saved, row.index(), row.type(), row.score());
        }
        return saved;
    }

    private void saveAnswer(InterviewSessionEntity session, int index, InterviewQuestionDTO.QuestionType type, Integer score) {
        InterviewAnswerEntity answer = new InterviewAnswerEntity();
        answer.setQuestionIndex(index);
        answer.setQuestion("题目 " + index + " 的正文");
        // 与生产写入一致：category 形如 "REDIS - 别名"
        answer.setCategory(type.name() + " - " + type.name().toLowerCase());
        answer.setUserAnswer("答案正文");
        answer.setScore(score);
        answer.setAnsweredAt(LocalDateTime.now());
        answer.setSession(session);
        answerRepository.save(answer);
    }
}
