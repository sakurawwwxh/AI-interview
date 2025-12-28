package interview.guide.modules.resume;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.common.result.Result;
import interview.guide.common.config.AppConfigProperties;
import interview.guide.modules.interview.model.ResumeAnalysisResponse;
import interview.guide.infrastructure.storage.FileStorageService;
import interview.guide.modules.resume.model.ResumeEntity;
import interview.guide.modules.interview.service.InterviewPersistenceService;
import interview.guide.modules.resume.service.ResumeGradingService;
import interview.guide.modules.resume.service.ResumeParseService;
import interview.guide.modules.resume.service.ResumePersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.Optional;

/**
 * 简历控制器
 * Resume Controller for upload and analysis
 */
@Slf4j
@RestController
@RequestMapping("/api/resume")
@RequiredArgsConstructor
public class ResumeController {
    
    private final ResumeParseService parseService;
    private final ResumeGradingService gradingService;
    private final FileStorageService storageService;
    private final ResumePersistenceService persistenceService;
    private final InterviewPersistenceService interviewPersistenceService;
    private final AppConfigProperties appConfig;
    
    /**
     * 上传简历并获取分析结果
     * POST /api/resume/upload
     * 
     * @param file 简历文件（支持PDF、DOCX、DOC、TXT）
     * @return 简历分析结果，包含评分和建议
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Map<String, Object>> uploadAndAnalyze(@RequestParam("file") MultipartFile file) {
        
        // 1. 验证文件
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择要上传的简历文件");
        }
        
        String fileName = file.getOriginalFilename();
        log.info("收到简历上传请求: {}, 大小: {} bytes", fileName, file.getSize());
        
        // 2. 验证文件类型
        String contentType = parseService.detectContentType(file);
        if (!isAllowedType(contentType)) {
            throw new BusinessException(ErrorCode.RESUME_UPLOAD_FAILED, "不支持的文件类型: " + contentType);
        }
        
        // 3. 检查简历是否已存在（去重）
        Optional<ResumeEntity> existingResume = persistenceService.findExistingResume(file);
        if (existingResume.isPresent()) {
            ResumeEntity resume = existingResume.get();
            log.info("检测到重复简历，返回历史分析结果: resumeId={}", resume.getId());
            
            // 获取历史分析结果，不重新分析
            ResumeAnalysisResponse analysis = persistenceService.getLatestAnalysisAsDTO(resume.getId())
                .orElseGet(() -> gradingService.analyzeResume(resume.getResumeText()));
            
            return Result.success("检测到相同简历，已返回历史分析结果", Map.of(
                "analysis", analysis,
                "storage", Map.of(
                    "fileKey", resume.getStorageKey() != null ? resume.getStorageKey() : "",
                    "fileUrl", resume.getStorageUrl() != null ? resume.getStorageUrl() : "",
                    "resumeId", resume.getId()
                ),
                "duplicate", true
            ));
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
        
        // 6. 保存简历到数据库
        ResumeEntity savedResume = persistenceService.saveResume(file, resumeText, fileKey, fileUrl);
        
        // 7. AI分析简历
        ResumeAnalysisResponse analysis = gradingService.analyzeResume(resumeText);
        
        // 8. 保存评测结果
        persistenceService.saveAnalysis(savedResume, analysis);
        
        log.info("简历分析完成: {}, 得分: {}, resumeId={}", fileName, analysis.overallScore(), savedResume.getId());
        
        // 9. 返回结果，包含存储信息
        return Result.success(Map.of(
            "analysis", analysis,
            "storage", Map.of(
                "fileKey", fileKey,
                "fileUrl", fileUrl,
                "resumeId", savedResume.getId()
            ),
            "duplicate", false
        ));
    }
    
    /**
     * 删除简历
     * DELETE /api/resume/{id}
     * 
     * @param id 简历ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteResume(@PathVariable Long id) {
        log.info("收到删除简历请求: id={}", id);
        
        // 获取简历信息（用于删除存储文件）
        var resumeOpt = persistenceService.findById(id);
        if (resumeOpt.isEmpty()) {
            throw new BusinessException(ErrorCode.RESUME_NOT_FOUND);
        }
        
        ResumeEntity resume = resumeOpt.get();
        
        // 1. 删除存储的文件（如果存在）
        if (resume.getStorageKey() != null && !resume.getStorageKey().isEmpty()) {
            try {
                storageService.deleteResume(resume.getStorageKey());
                log.info("已删除存储文件: {}", resume.getStorageKey());
            } catch (Exception e) {
                log.warn("删除存储文件失败，继续删除数据库记录: {}", e.getMessage());
            }
        }
        
        // 2. 删除面试会话（会自动删除面试答案）
        interviewPersistenceService.deleteSessionsByResumeId(id);
        
        // 3. 删除数据库记录（包括分析记录）
        persistenceService.deleteResume(id);
        
        log.info("简历删除完成: id={}", id);
        return Result.success(null);
    }
    
    /**
     * 健康检查接口
     */
    @GetMapping("/health")
    public Result<Map<String, String>> health() {
        return Result.success(Map.of(
            "status", "UP",
            "service", "AI Interview Platform - Resume Service"
        ));
    }
    
    /**
     * 检查文件类型是否允许
     */
    private boolean isAllowedType(String contentType) {
        if (contentType == null || appConfig.getAllowedTypes() == null) {
            return false;
        }
        return appConfig.getAllowedTypes().stream()
            .anyMatch(allowed -> contentType.toLowerCase().contains(allowed.toLowerCase()) 
                || allowed.toLowerCase().contains(contentType.toLowerCase()));
    }
}
