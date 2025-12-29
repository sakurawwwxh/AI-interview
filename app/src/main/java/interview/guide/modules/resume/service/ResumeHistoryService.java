package interview.guide.modules.resume.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.infrastructure.export.PdfExportService;
import interview.guide.modules.interview.model.ResumeAnalysisResponse;
import interview.guide.modules.interview.service.InterviewPersistenceService;
import interview.guide.modules.resume.model.ResumeAnalysisEntity;
import interview.guide.modules.resume.model.ResumeDetailDTO;
import interview.guide.modules.resume.model.ResumeEntity;
import interview.guide.modules.resume.model.ResumeListItemDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 简历历史服务
 * Resume History Service
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeHistoryService {
    
    private final ResumePersistenceService resumePersistenceService;
    private final InterviewPersistenceService interviewPersistenceService;
    private final PdfExportService pdfExportService;
    private final ObjectMapper objectMapper;
    
    /**
     * 获取所有简历列表
     */
    public List<ResumeListItemDTO> getAllResumes() {
        List<ResumeEntity> resumes = resumePersistenceService.findAllResumes();
        
        return resumes.stream().map(resume -> {
            // 获取最新分析结果的分数
            Integer latestScore = null;
            java.time.LocalDateTime lastAnalyzedAt = null;
            Optional<ResumeAnalysisEntity> analysisOpt = resumePersistenceService.getLatestAnalysis(resume.getId());
            if (analysisOpt.isPresent()) {
                ResumeAnalysisEntity analysis = analysisOpt.get();
                latestScore = analysis.getOverallScore();
                lastAnalyzedAt = analysis.getAnalyzedAt();
            }
            
            // 获取面试次数
            int interviewCount = interviewPersistenceService.findByResumeId(resume.getId()).size();
            
            return new ResumeListItemDTO(
                resume.getId(),
                resume.getOriginalFilename(),
                resume.getFileSize(),
                resume.getUploadedAt(),
                resume.getAccessCount(),
                latestScore,
                lastAnalyzedAt,
                interviewCount
            );
        }).toList();
    }
    
    /**
     * 获取简历详情（包含分析历史）
     */
    public ResumeDetailDTO getResumeDetail(Long id) {
        Optional<ResumeEntity> resumeOpt = resumePersistenceService.findById(id);
        if (resumeOpt.isEmpty()) {
            throw new BusinessException(ErrorCode.RESUME_NOT_FOUND);
        }
        
        ResumeEntity resume = resumeOpt.get();
        
        // 获取所有分析记录
        List<ResumeAnalysisEntity> analyses = resumePersistenceService.findAnalysesByResumeId(id);
        List<ResumeDetailDTO.AnalysisHistoryDTO> analysisHistory = analyses.stream().map(a -> {
            List<String> strengths = List.of();
            List<Object> suggestions = List.of();
            
            try {
                if (a.getStrengthsJson() != null) {
                    strengths = objectMapper.readValue(
                        a.getStrengthsJson(),
                        new TypeReference<List<String>>() {}
                    );
                }
                if (a.getSuggestionsJson() != null) {
                    suggestions = objectMapper.readValue(
                        a.getSuggestionsJson(),
                        new TypeReference<List<Object>>() {}
                    );
                }
            } catch (JsonProcessingException e) {
                log.error("解析分析JSON失败", e);
            }
            
            return new ResumeDetailDTO.AnalysisHistoryDTO(
                a.getId(),
                a.getOverallScore(),
                a.getContentScore(),
                a.getStructureScore(),
                a.getSkillMatchScore(),
                a.getExpressionScore(),
                a.getProjectScore(),
                a.getSummary(),
                a.getAnalyzedAt(),
                strengths,
                suggestions
            );
        }).toList();
        
        // 获取所有面试记录（只返回基本信息，详细内容由InterviewHistoryService提供）
        List<Object> interviewHistory = interviewPersistenceService.findByResumeId(id).stream()
            .map(session -> {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", session.getId());
                map.put("sessionId", session.getSessionId());
                map.put("totalQuestions", session.getTotalQuestions());
                map.put("status", session.getStatus().toString());
                map.put("overallScore", session.getOverallScore());
                map.put("createdAt", session.getCreatedAt());
                map.put("completedAt", session.getCompletedAt());
                return (Object) map;
            })
            .toList();
        
        return new ResumeDetailDTO(
            resume.getId(),
            resume.getOriginalFilename(),
            resume.getFileSize(),
            resume.getContentType(),
            resume.getStorageUrl(),
            resume.getUploadedAt(),
            resume.getAccessCount(),
            resume.getResumeText(),
            analysisHistory,
            interviewHistory
        );
    }
    
    /**
     * 导出简历分析报告为PDF
     */
    public ExportResult exportAnalysisPdf(Long resumeId) {
        Optional<ResumeEntity> resumeOpt = resumePersistenceService.findById(resumeId);
        if (resumeOpt.isEmpty()) {
            throw new BusinessException(ErrorCode.RESUME_NOT_FOUND);
        }
        
        ResumeEntity resume = resumeOpt.get();
        Optional<ResumeAnalysisResponse> analysisOpt = resumePersistenceService.getLatestAnalysisAsDTO(resumeId);
        if (analysisOpt.isEmpty()) {
            throw new BusinessException(ErrorCode.RESUME_ANALYSIS_NOT_FOUND);
        }
        
        try {
            byte[] pdfBytes = pdfExportService.exportResumeAnalysis(resume, analysisOpt.get());
            String filename = "简历分析报告_" + resume.getOriginalFilename() + ".pdf";
            
            return new ExportResult(pdfBytes, filename);
        } catch (Exception e) {
            log.error("导出PDF失败: resumeId={}", resumeId, e);
            throw new BusinessException(ErrorCode.EXPORT_PDF_FAILED, "导出PDF失败: " + e.getMessage());
        }
    }
    
    /**
     * PDF导出结果
     */
    public record ExportResult(byte[] pdfBytes, String filename) {}
}

