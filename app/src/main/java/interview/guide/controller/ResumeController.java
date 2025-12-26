package interview.guide.controller;

import interview.guide.config.AppConfigProperties;
import interview.guide.dto.ResumeAnalysisResponse;
import interview.guide.service.ResumeGradingService;
import interview.guide.service.ResumeParseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 简历控制器
 * Resume Controller for upload and analysis
 */
@RestController
@RequestMapping("/api/resume")
@CrossOrigin(origins = "*")
public class ResumeController {
    
    private static final Logger log = LoggerFactory.getLogger(ResumeController.class);
    
    private final ResumeParseService parseService;
    private final ResumeGradingService gradingService;
    private final AppConfigProperties appConfig;
    
    public ResumeController(ResumeParseService parseService, 
                           ResumeGradingService gradingService,
                           AppConfigProperties appConfig) {
        this.parseService = parseService;
        this.gradingService = gradingService;
        this.appConfig = appConfig;
    }
    
    /**
     * 上传简历并获取分析结果
     * POST /api/resume/upload
     * 
     * @param file 简历文件（支持PDF、DOCX、DOC、TXT）
     * @return 简历分析结果，包含评分和建议
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadAndAnalyze(@RequestParam("file") MultipartFile file) {
        
        // 1. 验证文件
        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "请选择要上传的简历文件"));
        }
        
        String fileName = file.getOriginalFilename();
        log.info("收到简历上传请求: {}, 大小: {} bytes", fileName, file.getSize());
        
        // 2. 验证文件类型
        String contentType = parseService.detectContentType(file);
        if (!isAllowedType(contentType)) {
            return ResponseEntity.badRequest()
                .body(Map.of(
                    "error", "不支持的文件类型",
                    "detectedType", contentType,
                    "allowedTypes", appConfig.getAllowedTypes()
                ));
        }
        
        try {
            // 3. 解析简历文本
            String resumeText = parseService.parseResume(file);
            
            if (resumeText == null || resumeText.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "无法从文件中提取文本内容，请确保文件不是扫描版PDF"));
            }
            
            // 4. AI分析简历
            ResumeAnalysisResponse analysis = gradingService.analyzeResume(resumeText);
            
            log.info("简历分析完成: {}, 得分: {}", fileName, analysis.overallScore());
            
            return ResponseEntity.ok(analysis);
            
        } catch (Exception e) {
            log.error("简历处理失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "简历处理失败: " + e.getMessage()));
        }
    }
    
    /**
     * 健康检查接口
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
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
