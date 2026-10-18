package ai.interview.modules.resume.service;

import ai.interview.common.config.AppConfigProperties;
import ai.interview.common.exception.BusinessException;
import ai.interview.common.model.AsyncTaskStatus;
import ai.interview.infrastructure.file.FileStorageService;
import ai.interview.infrastructure.file.FileValidationService;
import ai.interview.modules.resume.listener.AnalyzeStreamProducer;
import ai.interview.modules.resume.listener.AnalyzeTaskTracker;
import ai.interview.modules.resume.model.ResumeEntity;
import ai.interview.modules.resume.repository.ResumeRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 重新分析简历时的读路径。
 *
 * <p>重点不是「能不能重新分析」，而是两条边界：从对象存储下载解析必须发生在任何事务之外，
 * 以及正文回填只能写 {@code resume_text} 一列——整实体 save 会把读到的旧状态写回去，
 * 覆盖掉并发 claim 出来的 PENDING。
 */
@ExtendWith(MockitoExtension.class)
class ResumeUploadServiceTest {

    private static final long RESUME_ID = 5L;
    private static final long USER_ID = 7L;
    private static final String STORAGE_KEY = "resumes/5.pdf";
    private static final String FILENAME = "resume.pdf";

    @Mock private ResumeParseService parseService;
    @Mock private FileStorageService storageService;
    @Mock private ResumePersistenceService persistenceService;
    @Mock private AppConfigProperties appConfig;
    @Mock private FileValidationService fileValidationService;
    @Mock private AnalyzeStreamProducer analyzeStreamProducer;
    @Mock private AnalyzeTaskTracker analyzeTaskTracker;
    @Mock private ResumeRepository resumeRepository;

    private ResumeUploadService service;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(USER_ID, "owner", List.of()));
        service = new ResumeUploadService(parseService, storageService, persistenceService, appConfig,
            fileValidationService, analyzeStreamProducer, resumeRepository, analyzeTaskTracker);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("正文缺失：解析结果随 claim 一并落库，状态与正文同一次提交")
    void persistsTextRecoveredFromStorageWithTheClaim() {
        ResumeEntity resume = resume(AsyncTaskStatus.FAILED, null);
        when(persistenceService.findById(RESUME_ID)).thenReturn(Optional.of(resume));
        when(parseService.downloadAndParseContent(STORAGE_KEY, FILENAME)).thenReturn("解析出的正文");
        when(resumeRepository.claimForReanalysis(eq(RESUME_ID), eq(USER_ID), eq(AsyncTaskStatus.PENDING),
            anyCollection(), any())).thenReturn(1);

        service.reanalyze(RESUME_ID);

        verify(resumeRepository).claimForReanalysis(RESUME_ID, USER_ID, AsyncTaskStatus.PENDING,
            List.of(AsyncTaskStatus.FAILED, AsyncTaskStatus.COMPLETED), "解析出的正文");
        verify(resumeRepository, never()).save(any());
        verify(analyzeStreamProducer).sendAnalyzeTask(RESUME_ID, USER_ID, "解析出的正文");
    }

    @Test
    @DisplayName("下载解析发生在 claim 之前，不占着事务等网络")
    void downloadsAndParsesBeforeClaiming() {
        ResumeEntity resume = resume(AsyncTaskStatus.FAILED, null);
        when(persistenceService.findById(RESUME_ID)).thenReturn(Optional.of(resume));
        when(parseService.downloadAndParseContent(STORAGE_KEY, FILENAME)).thenReturn("解析出的正文");
        when(resumeRepository.claimForReanalysis(eq(RESUME_ID), eq(USER_ID), eq(AsyncTaskStatus.PENDING),
            anyCollection(), any())).thenReturn(1);

        service.reanalyze(RESUME_ID);

        InOrder inOrder = inOrder(parseService, analyzeTaskTracker, resumeRepository);
        inOrder.verify(parseService).downloadAndParseContent(STORAGE_KEY, FILENAME);
        // 登记必须在认领之前：认领提交后进程若退出，回收器要靠这条登记找到它
        inOrder.verify(analyzeTaskTracker).markQueued(RESUME_ID);
        inOrder.verify(resumeRepository).claimForReanalysis(eq(RESUME_ID), eq(USER_ID),
            eq(AsyncTaskStatus.PENDING), anyCollection(), any());
    }

    @Test
    @DisplayName("正文已缓存：不再下载，也不写库，直接用缓存文本入队")
    void reusesCachedTextWithoutTouchingStorageOrDatabase() {
        ResumeEntity resume = resume(AsyncTaskStatus.COMPLETED, "已有正文");
        when(persistenceService.findById(RESUME_ID)).thenReturn(Optional.of(resume));
        when(resumeRepository.claimForReanalysis(eq(RESUME_ID), eq(USER_ID), eq(AsyncTaskStatus.PENDING),
            anyCollection(), any())).thenReturn(1);

        service.reanalyze(RESUME_ID);

        verify(parseService, never()).downloadAndParseContent(any(), any());
        // 已缓存正文时不带文本参数，库里那一列保持原样
        verify(resumeRepository).claimForReanalysis(eq(RESUME_ID), eq(USER_ID),
            eq(AsyncTaskStatus.PENDING), anyCollection(), isNull());
        verify(analyzeStreamProducer).sendAnalyzeTask(RESUME_ID, USER_ID, "已有正文");
    }

    @Test
    @DisplayName("claim 输给并发请求：不写库、不入队")
    void doesNotPersistOrEnqueueWhenClaimLoses() {
        ResumeEntity resume = resume(AsyncTaskStatus.FAILED, null);
        when(persistenceService.findById(RESUME_ID)).thenReturn(Optional.of(resume));
        when(parseService.downloadAndParseContent(STORAGE_KEY, FILENAME)).thenReturn("解析出的正文");
        when(resumeRepository.claimForReanalysis(eq(RESUME_ID), eq(USER_ID), eq(AsyncTaskStatus.PENDING),
            anyCollection(), any())).thenReturn(0);

        assertThrows(BusinessException.class, () -> service.reanalyze(RESUME_ID));

        verify(analyzeStreamProducer, never()).sendAnalyzeTask(any(), any(), any());
    }

    @Test
    @DisplayName("解析结果为空：报错且不进入 claim")
    void rejectsWhenParsingYieldsNothing() {
        ResumeEntity resume = resume(AsyncTaskStatus.FAILED, null);
        when(persistenceService.findById(RESUME_ID)).thenReturn(Optional.of(resume));
        when(parseService.downloadAndParseContent(STORAGE_KEY, FILENAME)).thenReturn("   ");

        assertThrows(BusinessException.class, () -> service.reanalyze(RESUME_ID));

        verify(resumeRepository, never()).claimForReanalysis(any(), any(), any(), any(), any());
        verify(analyzeStreamProducer, never()).sendAnalyzeTask(any(), any(), any());
    }

    @Test
    @DisplayName("任务进行中：直接拒绝，不下载不解析")
    void rejectsWhileAnotherAnalysisIsRunning() {
        ResumeEntity resume = resume(AsyncTaskStatus.PROCESSING, "已有正文");
        when(persistenceService.findById(RESUME_ID)).thenReturn(Optional.of(resume));

        assertThrows(BusinessException.class, () -> service.reanalyze(RESUME_ID));

        verify(parseService, never()).downloadAndParseContent(any(), any());
        verify(resumeRepository, never()).claimForReanalysis(any(), any(), any(), any(), any());
    }

    private ResumeEntity resume(AsyncTaskStatus status, String text) {
        ResumeEntity resume = new ResumeEntity();
        resume.setId(RESUME_ID);
        resume.setUserId(USER_ID);
        resume.setOriginalFilename(FILENAME);
        resume.setStorageKey(STORAGE_KEY);
        resume.setAnalyzeStatus(status);
        resume.setResumeText(text);
        return resume;
    }
}
