package interview.guide.modules.resume.service;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.infrastructure.export.PdfExportService;
import interview.guide.infrastructure.mapper.ResumeMapper;
import interview.guide.modules.interview.model.ResumeAnalysisResponse;
import interview.guide.modules.interview.repository.InterviewSessionRepository.ResumeInterviewRow;
import interview.guide.modules.interview.service.InterviewPersistenceService;
import interview.guide.modules.resume.model.ResumeAnalysisEntity;
import interview.guide.modules.resume.model.ResumeDetailDTO;
import interview.guide.modules.resume.model.ResumeEntity;
import interview.guide.modules.resume.model.ResumeListItemDTO;
import interview.guide.modules.resume.repository.ResumeRepository;
import interview.guide.modules.user.security.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 简历历史服务
 * 简历历史和导出简历分析报告
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeHistoryService {

    private final ResumePersistenceService resumePersistenceService;
    private final ResumeRepository resumeRepository;
    private final InterviewPersistenceService interviewPersistenceService;
    private final PdfExportService pdfExportService;
    private final ObjectMapper objectMapper;
    private final ResumeMapper resumeMapper;

    /**
     * 获取所有简历列表
     */
    public List<ResumeListItemDTO> getAllResumes() {
        return resumeRepository.findListRowsByUserId(UserContext.getCurrentUserIdOrThrow()).stream()
            .map(row -> new ResumeListItemDTO(
                row.getId(),
                row.getFilename(),
                row.getFileSize(),
                row.getUploadedAt(),
                row.getAccessCount(),
                row.getLatestScore(),
                row.getLastAnalyzedAt(),
                Math.toIntExact(row.getInterviewCount())
            ))
            .toList();
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

        // 获取所有分析记录，使用 MapStruct 批量转换
        List<ResumeAnalysisEntity> analyses = resumePersistenceService.findAnalysesByResumeId(id);
        List<ResumeDetailDTO.AnalysisHistoryDTO> analysisHistory = resumeMapper.toAnalysisHistoryDTOList(
            analyses,
            this::extractStrengths,
            this::extractSuggestions
        );

        // 面试历史只取展示需要的标量列：此前走实体查询会把每场的题目/点评等大文本一起拉回来
        List<Object> interviewHistory = interviewPersistenceService.findResumeInterviewRows(id).stream()
            .map(ResumeHistoryService::toInterviewHistoryItem)
            .map(item -> (Object) item)
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
            resume.getAnalyzeStatus(),
            resume.getAnalyzeError(),
            analysisHistory,
            interviewHistory
        );
    }

    /**
     * 面试历史项的字段名与前端约定，集中在这里维护。
     * 取值与原先的实体路径一致：status 库里就是枚举名，evaluateStatus 为空时保持 null。
     */
    private static Map<String, Object> toInterviewHistoryItem(ResumeInterviewRow row) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", row.getId());
        item.put("sessionId", row.getSessionId());
        item.put("totalQuestions", row.getTotalQuestions());
        item.put("status", row.getStatus());
        item.put("evaluateStatus", row.getEvaluateStatus());
        item.put("evaluateError", row.getEvaluateError());
        item.put("overallScore", row.getOverallScore());
        item.put("createdAt", row.getCreatedAt());
        item.put("completedAt", row.getCompletedAt());
        return item;
    }

    /**
     * 从 JSON 提取 strengths
     */
    private List<String> extractStrengths(ResumeAnalysisEntity entity) {
        try {
            if (entity.getStrengthsJson() != null) {
                return objectMapper.readValue(
                    entity.getStrengthsJson(),
                        new TypeReference<>() {
                        }
                );
            }
        } catch (JacksonException e) {
            log.error("解析 strengths JSON 失败", e);
        }
        return List.of();
    }

    /**
     * 从 JSON 提取 suggestions
     */
    private List<Object> extractSuggestions(ResumeAnalysisEntity entity) {
        try {
            if (entity.getSuggestionsJson() != null) {
                return objectMapper.readValue(
                    entity.getSuggestionsJson(),
                        new TypeReference<>() {
                        }
                );
            }
        } catch (JacksonException e) {
            log.error("解析 suggestions JSON 失败", e);
        }
        return List.of();
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

