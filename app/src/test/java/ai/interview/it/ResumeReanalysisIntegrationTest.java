package ai.interview.it;

import ai.interview.common.model.AsyncTaskStatus;
import ai.interview.modules.resume.model.ResumeEntity;
import ai.interview.modules.resume.repository.ResumeRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 「重新分析」认领语句的集成测试。
 *
 * <p>这条 UPDATE 同时承担两件事：把终态改成 PENDING、把刚解析出的正文写回。
 * 关键在于 {@code coalesce(cast(:resumeText as string), r.resumeText)} —— 传 null 时必须
 * 保留库里已有的正文，传文本时必须覆盖；这两点只有真库（真实 JPQL 翻译 + 参数绑定）
 * 能验证，mock 掉 Repository 的单元测试看不到。
 */
@SpringBootTest
@Import(StubEmbeddingConfiguration.class)
@EnabledIf("ai.interview.it.IntegrationTestDatabase#available")
@DisplayName("简历重新分析认领 · 集成测试")
class ResumeReanalysisIntegrationTest {

    private static final long USER_ID = 9101L;

    @DynamicPropertySource
    static void useIsolatedDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", IntegrationTestDatabase::jdbcUrl);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.ai.openai.api-key", () -> "it-placeholder-key");
    }

    @Autowired
    private ResumeRepository resumeRepository;

    @AfterEach
    void cleanUp() {
        resumeRepository.deleteAll();
    }

    @Test
    @DisplayName("认领同时写入正文：状态转 PENDING、错误清空、正文覆盖")
    void claimWritesStatusAndRecoveredTextTogether() {
        Long id = saveResume(AsyncTaskStatus.FAILED, "旧正文", "解析失败");

        int claimed = resumeRepository.claimForReanalysis(id, USER_ID, AsyncTaskStatus.PENDING,
            List.of(AsyncTaskStatus.FAILED, AsyncTaskStatus.COMPLETED), "新正文");

        assertEquals(1, claimed);
        ResumeEntity reloaded = reload(id);
        assertEquals(AsyncTaskStatus.PENDING, reloaded.getAnalyzeStatus());
        assertEquals("新正文", reloaded.getResumeText());
        assertNull(reloaded.getAnalyzeError());
    }

    @Test
    @DisplayName("不传正文（null）时保留库里已有的正文")
    void claimWithoutTextKeepsCachedText() {
        Long id = saveResume(AsyncTaskStatus.COMPLETED, "缓存的正文", null);

        int claimed = resumeRepository.claimForReanalysis(id, USER_ID, AsyncTaskStatus.PENDING,
            List.of(AsyncTaskStatus.FAILED, AsyncTaskStatus.COMPLETED), null);

        assertEquals(1, claimed);
        ResumeEntity reloaded = reload(id);
        assertEquals(AsyncTaskStatus.PENDING, reloaded.getAnalyzeStatus());
        assertEquals("缓存的正文", reloaded.getResumeText());
    }

    @Test
    @DisplayName("进行中的简历不接受认领：0 行受影响，正文也不被覆盖")
    void claimRejectsWhenAnalysisIsAlreadyRunning() {
        Long id = saveResume(AsyncTaskStatus.PROCESSING, "原正文", null);

        int claimed = resumeRepository.claimForReanalysis(id, USER_ID, AsyncTaskStatus.PENDING,
            List.of(AsyncTaskStatus.FAILED, AsyncTaskStatus.COMPLETED), "不该写入的正文");

        assertEquals(0, claimed);
        ResumeEntity reloaded = reload(id);
        assertEquals(AsyncTaskStatus.PROCESSING, reloaded.getAnalyzeStatus());
        assertEquals("原正文", reloaded.getResumeText());
    }

    @Test
    @DisplayName("回收器只压回仍为 PENDING 的记录，不误伤已推进的任务")
    void failIfStillPendingOnlyTouchesPending() {
        Long pendingId = saveResume(AsyncTaskStatus.PENDING, "正文", null);
        Long completedId = saveResume(AsyncTaskStatus.COMPLETED, "正文", null);

        assertEquals(1, resumeRepository.failIfStillPending(pendingId, AsyncTaskStatus.FAILED,
            "任务超时未入队", AsyncTaskStatus.PENDING));
        assertEquals(0, resumeRepository.failIfStillPending(completedId, AsyncTaskStatus.FAILED,
            "任务超时未入队", AsyncTaskStatus.PENDING));

        ResumeEntity reaped = reload(pendingId);
        assertEquals(AsyncTaskStatus.FAILED, reaped.getAnalyzeStatus());
        assertEquals("任务超时未入队", reaped.getAnalyzeError());
        assertEquals(AsyncTaskStatus.COMPLETED, reload(completedId).getAnalyzeStatus());
    }

    @Test
    @DisplayName("别人的简历认领不到")
    void claimIsScopedToTheOwner() {
        Long id = saveResume(AsyncTaskStatus.FAILED, "原正文", null);

        int claimed = resumeRepository.claimForReanalysis(id, USER_ID + 1, AsyncTaskStatus.PENDING,
            List.of(AsyncTaskStatus.FAILED, AsyncTaskStatus.COMPLETED), "越权正文");

        assertEquals(0, claimed);
        assertEquals("原正文", reload(id).getResumeText());
    }

    private Long saveResume(AsyncTaskStatus status, String text, String error) {
        ResumeEntity resume = new ResumeEntity();
        resume.setUserId(USER_ID);
        resume.setFileHash("it-hash-" + java.util.UUID.randomUUID());
        resume.setOriginalFilename("it-resume.pdf");
        resume.setStorageKey("resumes/it.pdf");
        resume.setResumeText(text);
        resume.setAnalyzeStatus(status);
        resume.setAnalyzeError(error);
        return resumeRepository.save(resume).getId();
    }

    private ResumeEntity reload(Long id) {
        Optional<ResumeEntity> entity = resumeRepository.findById(id);
        return entity.orElseThrow(() -> new AssertionError("简历不存在: " + id));
    }
}
