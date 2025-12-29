package interview.guide.modules.resume;

import interview.guide.common.result.Result;
import interview.guide.modules.resume.service.ResumeUploadService;
import interview.guide.modules.resume.service.ResumeDeleteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 简历控制器
 * Resume Controller for upload and analysis
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class ResumeController {
    
    private final ResumeUploadService uploadService;
    private final ResumeDeleteService deleteService;
    
    /**
     * 上传简历并获取分析结果
     * POST /api/resume/upload
     * 
     * @param file 简历文件（支持PDF、DOCX、DOC、TXT）
     * @return 简历分析结果，包含评分和建议
     */
    @PostMapping(value = "/api/resume/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Map<String, Object>> uploadAndAnalyze(@RequestParam("file") MultipartFile file) {
        Map<String, Object> result = uploadService.uploadAndAnalyze(file);
        boolean isDuplicate = (Boolean) result.get("duplicate");
        if (isDuplicate) {
            return Result.success("检测到相同简历，已返回历史分析结果", result);
        }
        return Result.success(result);
    }
    
    /**
     * 删除简历
     * DELETE /api/resume/{id}
     * 
     * @param id 简历ID
     * @return 删除结果
     */
    @DeleteMapping("/api/resume/{id}")
    public Result<Void> deleteResume(@PathVariable Long id) {
        deleteService.deleteResume(id);
        return Result.success(null);
    }
    
    /**
     * 健康检查接口
     */
    @GetMapping("/api/resume/health")
    public Result<Map<String, String>> health() {
        return Result.success(Map.of(
            "status", "UP",
            "service", "AI Interview Platform - Resume Service"
        ));
    }
    
}
