package ai.interview.modules.resume.service;

import ai.interview.common.config.AppConfigProperties;
import ai.interview.common.exception.BusinessException;
import ai.interview.common.exception.ErrorCode;
import ai.interview.common.model.AsyncTaskStatus;
import ai.interview.infrastructure.file.FileStorageService;
import ai.interview.infrastructure.file.FileValidationService;
import ai.interview.modules.interview.model.ResumeAnalysisResponse;
import ai.interview.modules.resume.listener.AnalyzeStreamProducer;
import ai.interview.modules.resume.listener.AnalyzeTaskTracker;
import ai.interview.modules.resume.model.ResumeEntity;
import ai.interview.modules.resume.repository.ResumeRepository;
import ai.interview.modules.user.security.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * 简历上传服务
 * 处理简历上传、解析的业务逻辑
 * AI 分析改为异步处理，通过 Redis Stream 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeUploadService {

    private final ResumeParseService parseService;
    private final FileStorageService storageService;
    private final ResumePersistenceService persistenceService;
    private final AppConfigProperties appConfig;
    private final FileValidationService fileValidationService;
    private final AnalyzeStreamProducer analyzeStreamProducer;
    private final ResumeRepository resumeRepository;
    private final AnalyzeTaskTracker analyzeTaskTracker;

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    /**
     * 上传并分析简历（异步）
     *
     * @param file 简历文件
     * @return 上传结果（分析将异步进行）
     */
    public Map<String, Object> uploadAndAnalyze(org.springframework.web.multipart.MultipartFile file) {
        // 1. 验证文件
        fileValidationService.validateFile(file, MAX_FILE_SIZE, "简历");

        String fileName = file.getOriginalFilename();
        log.info("收到简历上传请求: {}, 大小: {} bytes", fileName, file.getSize());

        // 2. 验证文件类型
        String contentType = parseService.detectContentType(file);
        validateContentType(contentType);

        // 3. 检查简历是否已存在（去重）
        Optional<ResumeEntity> existingResume = persistenceService.findExistingResume(file);
        if (existingResume.isPresent()) {
            return handleDuplicateResume(existingResume.get());
        }

        // 4. 解析简历文本
        String resumeText = parseService.parseResume(file);
        if (resumeText == null || resumeText.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.RESUME_PARSE_FAILED, "无法从文件中提取文本内容，请确保文件不是扫描版PDF");
        }

        // 5. 保存简历到RustFS
        String fileKey = storageService.uploadResume(file);
        String fileUrl = storageService.getFileUrl(fileKey);
        log.info("简历已存储到RustFS: {}", fileKey);

        // 6. 保存简历到数据库（状态为 PENDING）
        ResumeEntity savedResume = persistenceService.saveResume(file, resumeText, fileKey, fileUrl);

        // 7. 发送分析任务到 Redis Stream（异步处理）
        analyzeStreamProducer.sendAnalyzeTask(savedResume.getId(), savedResume.getUserId(), resumeText);

        log.info("简历上传完成，分析任务已入队: {}, resumeId={}", fileName, savedResume.getId());

        // 8. 返回结果（状态为 PENDING，前端可轮询获取最新状态）
        return Map.of(
            "resume", Map.of(
                "id", savedResume.getId(),
                "filename", savedResume.getOriginalFilename(),
                "analyzeStatus", AsyncTaskStatus.PENDING.name()
            ),
            "storage", Map.of(
                "fileKey", fileKey,
                "fileUrl", fileUrl,
                "resumeId", savedResume.getId()
            ),
            "duplicate", false
        );
    }

    /**
     * 验证文件类型
     */
    private void validateContentType(String contentType) {
        fileValidationService.validateContentTypeByList(
            contentType,
            appConfig.getAllowedTypes(),
            "不支持的文件类型: " + contentType
        );
    }

    /**
     * 处理重复简历
     */
    private Map<String, Object> handleDuplicateResume(ResumeEntity resume) {
        log.info("检测到重复简历，返回历史分析结果: resumeId={}", resume.getId());

        // 获取历史分析结果
        Optional<ResumeAnalysisResponse> analysisOpt = persistenceService.getLatestAnalysisAsDTO(resume.getId());

        // 已有分析结果，直接返回
        // 没有分析结果（可能之前分析失败），返回当前状态
        return analysisOpt.map(resumeAnalysisResponse -> Map.of(
                "analysis", resumeAnalysisResponse,
                "storage", Map.of(
                        "fileKey", resume.getStorageKey() != null ? resume.getStorageKey() : "",
                        "fileUrl", resume.getStorageUrl() != null ? resume.getStorageUrl() : "",
                        "resumeId", resume.getId()
                ),
                "duplicate", true
        )).orElseGet(() -> Map.of(
                "resume", Map.of(
                        "id", resume.getId(),
                        "filename", resume.getOriginalFilename(),
                        "analyzeStatus", resume.getAnalyzeStatus() != null ? resume.getAnalyzeStatus().name() : AsyncTaskStatus.PENDING.name()
                ),
                "storage", Map.of(
                        "fileKey", resume.getStorageKey() != null ? resume.getStorageKey() : "",
                        "fileUrl", resume.getStorageUrl() != null ? resume.getStorageUrl() : "",
                        "resumeId", resume.getId()
                ),
                "duplicate", true
        ));
    }

    /**
     * 重新分析简历（手动重试）
     * 从数据库获取简历文本并发送分析任务
     *
     * <p>刻意不加 {@code @Transactional}：正文缺失时会从对象存储下载并解析文件，
     * 这是数秒级的网络 + CPU 动作，包进事务会让数据库连接一直被占用。
     * 需要原子性的只有 {@code claimForReanalysis} 这一条条件更新，它自带事务。
     *
     * @param resumeId 简历ID
     */
    public void reanalyze(Long resumeId) {
        Long userId = UserContext.getCurrentUserIdOrThrow();
        ResumeEntity resume = persistenceService.findById(resumeId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESUME_NOT_FOUND, "简历不存在"));

        AsyncTaskStatus currentStatus = resume.getAnalyzeStatus();
        if (currentStatus == AsyncTaskStatus.PENDING || currentStatus == AsyncTaskStatus.PROCESSING) {
            throw new BusinessException(ErrorCode.RESUME_ANALYSIS_IN_PROGRESS,
                "简历分析任务正在进行中，请等待当前任务完成");
        }

        log.info("开始重新分析简历: resumeId={}, filename={}", resumeId, resume.getOriginalFilename());

        String resumeText = resume.getResumeText();
        boolean recoveredFromStorage = resumeText == null || resumeText.trim().isEmpty();
        if (recoveredFromStorage) {
            // 没有缓存的文本，重新从存储下载解析
            resumeText = parseService.downloadAndParseContent(resume.getStorageKey(), resume.getOriginalFilename());
            if (resumeText == null || resumeText.trim().isEmpty()) {
                throw new BusinessException(ErrorCode.RESUME_PARSE_FAILED, "无法获取简历文本内容");
            }
        }

        // 先登记再认领：认领提交与入队之间若进程退出，回收器仍能按登记把 PENDING 压回 FAILED
        analyzeTaskTracker.markQueued(resumeId);

        // 条件更新同时完成「认领」和「正文回填」：拆成两条语句的话，claim 提交后、回填前失败
        // 会让简历永久停在 PENDING（重试入口对 PENDING 一律拒绝，也没有任何任务会来完成它）。
        int claimed = resumeRepository.claimForReanalysis(
            resumeId,
            userId,
            AsyncTaskStatus.PENDING,
            java.util.List.of(AsyncTaskStatus.FAILED, AsyncTaskStatus.COMPLETED),
            recoveredFromStorage ? resumeText : null
        );
        if (claimed != 1) {
            throw new BusinessException(ErrorCode.RESUME_ANALYSIS_IN_PROGRESS,
                "简历分析任务已被其他请求提交，请勿重复操作");
        }

        // 发送分析任务到 Stream
        analyzeStreamProducer.sendAnalyzeTask(resumeId, resume.getUserId(), resumeText);

        log.info("重新分析任务已发送: resumeId={}", resumeId);
    }
}
