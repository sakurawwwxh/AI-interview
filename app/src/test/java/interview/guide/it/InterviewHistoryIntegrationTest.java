package interview.guide.it;

import interview.guide.common.model.AsyncTaskStatus;
import interview.guide.modules.interview.model.InterviewListItemDTO;
import interview.guide.modules.interview.model.InterviewSessionEntity;
import interview.guide.modules.interview.model.InterviewSessionEntity.SessionStatus;
import interview.guide.modules.interview.repository.InterviewAnswerRepository;
import interview.guide.modules.interview.repository.InterviewSessionRepository;
import interview.guide.modules.interview.repository.InterviewSessionRepository.ResumeInterviewRow;
import interview.guide.modules.interview.service.InterviewHistoryService;
import interview.guide.modules.resume.model.ResumeEntity;
import interview.guide.modules.resume.repository.ResumeRepository;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 面试记录列表读路径的集成测试：真实数据库 + 真实 SQL。
 *
 * <p>记录列表原先走「实体 + JOIN FETCH resume」，会把会话与简历上的大文本列一并取回
 * （实测 72 KB，而列表只渲染 13 个标量、约 0.6 KB）。这里钉住改造后的投影查询：
 * 列别名映射、枚举以字符串读取、LEFT JOIN 未命中时的兜底、以及按创建时间倒序。
 *
 * <p>用例数据库是隔离的 {@code interview_guide_it}，数据库不可达时整类跳过。
 */
@SpringBootTest
@Import(StubEmbeddingConfiguration.class)
@EnabledIf("interview.guide.it.IntegrationTestDatabase#available")
@DisplayName("面试记录列表读路径 · 集成测试")
class InterviewHistoryIntegrationTest {

    private static final long USER_ID = 9101L;

    @DynamicPropertySource
    static void useIsolatedDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", IntegrationTestDatabase::jdbcUrl);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.ai.openai.api-key", () -> "it-placeholder-key");
    }

    @Autowired
    private InterviewHistoryService historyService;

    @Autowired
    private InterviewSessionRepository sessionRepository;

    @Autowired
    private InterviewAnswerRepository answerRepository;

    @Autowired
    private ResumeRepository resumeRepository;

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
    @DisplayName("列表按创建时间倒序，且字段取值与实体路径一致（含枚举名与简历文件名）")
    void listsSessionsInReverseChronologicalOrderWithResumeAndEnumNames() {
        ResumeEntity resume = saveResume("我的简历.pdf");
        InterviewSessionEntity older = saveSession(resume, "hist-old", 10, LocalDateTime.now().minusDays(2),
            SessionStatus.CREATED, null, null);
        InterviewSessionEntity newer = saveSession(resume, "hist-new", 12, LocalDateTime.now().minusHours(1),
            SessionStatus.EVALUATED, AsyncTaskStatus.COMPLETED, 88);

        List<InterviewListItemDTO> items = historyService.listCurrentUserInterviews();

        assertEquals(2, items.size());
        assertEquals(List.of("hist-new", "hist-old"),
            items.stream().map(InterviewListItemDTO::sessionId).toList());

        InterviewListItemDTO newest = items.getFirst();
        assertEquals(newer.getId(), newest.id());
        assertEquals(resume.getId(), newest.resumeId());
        assertEquals("我的简历.pdf", newest.resumeFilename());
        // 枚举以字符串读取，取值必须与原先 getStatus().name() 一致
        assertEquals("EVALUATED", newest.status());
        assertEquals("COMPLETED", newest.evaluateStatus());
        assertEquals(88, newest.overallScore());

        InterviewListItemDTO oldest = items.get(1);
        assertEquals(older.getId(), oldest.id());
        assertEquals("CREATED", oldest.status());
        assertEquals(10, oldest.totalQuestions());
        // 未评估时保持为空，不能凭空造出状态或分数
        assertNull(oldest.evaluateStatus());
        assertNull(oldest.overallScore());
    }

    @Test
    @DisplayName("只返回当前用户的记录")
    void onlyReturnsCurrentUsersSessions() {
        ResumeEntity mine = saveResume("my.pdf");
        saveSession(mine, "mine-1", 5, LocalDateTime.now(), SessionStatus.CREATED, null, null);

        List<InterviewListItemDTO> items = historyService.listCurrentUserInterviews();

        assertEquals(List.of("mine-1"), items.stream().map(InterviewListItemDTO::sessionId).toList());
    }

    @Test
    @DisplayName("简历详情里的面试历史：只取标量列，取值与实体路径一致且按创建时间倒序")
    void resumeDetailInterviewRowsCarryTheSameValuesAsEntities() {
        ResumeEntity resume = saveResume("detail.pdf");
        InterviewSessionEntity older = saveSession(resume, "detail-old", 6, LocalDateTime.now().minusDays(2),
            SessionStatus.CREATED, null, null);
        InterviewSessionEntity newer = saveSession(resume, "detail-new", 22, LocalDateTime.now().minusHours(1),
            SessionStatus.EVALUATED, AsyncTaskStatus.COMPLETED, 71);

        List<ResumeInterviewRow> rows = sessionRepository.findResumeInterviewRowsByResumeId(resume.getId());

        assertEquals(List.of("detail-new", "detail-old"),
            rows.stream().map(ResumeInterviewRow::getSessionId).toList());

        ResumeInterviewRow newest = rows.getFirst();
        assertEquals(newer.getId(), newest.getId());
        assertEquals(22, newest.getTotalQuestions());
        // 枚举以字符串读取，取值与实体的 getStatus().toString() 一致
        assertEquals("EVALUATED", newest.getStatus());
        assertEquals("COMPLETED", newest.getEvaluateStatus());
        assertEquals(71, newest.getOverallScore());
        // createdAt 能正确映射（跨库往返会截到微秒精度）；completedAt 本用例没设置，投影应如实返回 null
        assertNotNull(newest.getCreatedAt());
        assertNull(newest.getCompletedAt());

        ResumeInterviewRow oldest = rows.get(1);
        assertEquals(older.getId(), oldest.getId());
        assertEquals("CREATED", oldest.getStatus());
        assertNull(oldest.getEvaluateStatus());
        assertNull(oldest.getOverallScore());
    }

    private ResumeEntity saveResume(String filename) {
        ResumeEntity resume = new ResumeEntity();
        resume.setUserId(USER_ID);
        resume.setFileHash("it-hash-" + UUID.randomUUID());
        resume.setOriginalFilename(filename);
        resume.setUploadedAt(LocalDateTime.now());
        return resumeRepository.save(resume);
    }

    private InterviewSessionEntity saveSession(ResumeEntity resume, String sessionId, int totalQuestions,
                                              LocalDateTime createdAt, SessionStatus status,
                                              AsyncTaskStatus evaluateStatus, Integer overallScore) {
        InterviewSessionEntity session = new InterviewSessionEntity();
        session.setUserId(USER_ID);
        session.setSessionId(sessionId);
        session.setResume(resume);
        session.setTotalQuestions(totalQuestions);
        session.setStatus(status);
        session.setEvaluateStatus(evaluateStatus);
        session.setOverallScore(overallScore);
        session.setCreatedAt(createdAt);
        // 故意写入一份大文本，确认投影查询不会把它带回来
        session.setQuestionsJson("[{\"questionIndex\":0,\"question\":\"" + "填充".repeat(4000) + "\",\"type\":\"REDIS\"}]");
        return sessionRepository.save(session);
    }
}
