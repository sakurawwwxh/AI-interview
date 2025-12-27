package interview.guide.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import interview.guide.common.result.Result;
import interview.guide.modules.interview.model.ResumeAnalysisResponse;
import interview.guide.modules.interview.model.InterviewAnswerEntity;
import interview.guide.modules.interview.model.InterviewSessionEntity;
import interview.guide.modules.resume.model.ResumeAnalysisEntity;
import interview.guide.modules.resume.model.ResumeEntity;
import interview.guide.modules.interview.service.InterviewPersistenceService;
import interview.guide.infrastructure.export.PdfExportService;
import interview.guide.modules.resume.service.ResumePersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 历史记录控制器
 * History Controller for viewing past resumes and interviews
 */
@Slf4j
@RestController
@RequestMapping("/api/history")
@RequiredArgsConstructor
public class HistoryController {
    
    private final ResumePersistenceService resumeService;
    private final InterviewPersistenceService interviewService;
    private final PdfExportService pdfExportService;
    private final ObjectMapper objectMapper;
    
    /**
     * 获取所有简历列表
     * GET /api/history/resumes
     */
    @GetMapping("/resumes")
    public Result<List<Map<String, Object>>> getAllResumes() {
        List<ResumeEntity> resumes = resumeService.findAllResumes();
        
        List<Map<String, Object>> result = resumes.stream().map(resume -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", resume.getId());
            map.put("filename", resume.getOriginalFilename());
            map.put("fileSize", resume.getFileSize());
            map.put("uploadedAt", resume.getUploadedAt());
            map.put("accessCount", resume.getAccessCount());
            
            // 获取最新分析结果的分数
            resumeService.getLatestAnalysis(resume.getId()).ifPresent(analysis -> {
                map.put("latestScore", analysis.getOverallScore());
                map.put("lastAnalyzedAt", analysis.getAnalyzedAt());
            });
            
            // 获取面试次数
            List<InterviewSessionEntity> interviews = interviewService.findByResumeId(resume.getId());
            map.put("interviewCount", interviews.size());
            
            return map;
        }).toList();
        
        return Result.success(result);
    }
    
    /**
     * 获取简历详情（包含分析历史）
     * GET /api/history/resumes/{id}
     */
    @GetMapping("/resumes/{id}")
    public Result<Map<String, Object>> getResumeDetail(@PathVariable Long id) {
        Optional<ResumeEntity> resumeOpt = resumeService.findById(id);
        if (resumeOpt.isEmpty()) {
            return Result.error(404, "简历不存在");
        }
        
        ResumeEntity resume = resumeOpt.get();
        
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", resume.getId());
        result.put("filename", resume.getOriginalFilename());
        result.put("fileSize", resume.getFileSize());
        result.put("contentType", resume.getContentType());
        result.put("storageUrl", resume.getStorageUrl());
        result.put("uploadedAt", resume.getUploadedAt());
        result.put("accessCount", resume.getAccessCount());
        result.put("resumeText", resume.getResumeText());
        
        // 获取所有分析记录
        List<ResumeAnalysisEntity> analyses = resumeService.findAnalysesByResumeId(id);
        List<Map<String, Object>> analysisHistory = analyses.stream().map(a -> {
            Map<String, Object> aMap = new LinkedHashMap<>();
            aMap.put("id", a.getId());
            aMap.put("overallScore", a.getOverallScore());
            aMap.put("contentScore", a.getContentScore());
            aMap.put("structureScore", a.getStructureScore());
            aMap.put("skillMatchScore", a.getSkillMatchScore());
            aMap.put("expressionScore", a.getExpressionScore());
            aMap.put("projectScore", a.getProjectScore());
            aMap.put("summary", a.getSummary());
            aMap.put("analyzedAt", a.getAnalyzedAt());
            
            try {
                aMap.put("strengths", objectMapper.readValue(
                    a.getStrengthsJson() != null ? a.getStrengthsJson() : "[]", 
                    new TypeReference<List<String>>() {}));
                aMap.put("suggestions", objectMapper.readValue(
                    a.getSuggestionsJson() != null ? a.getSuggestionsJson() : "[]", 
                    new TypeReference<List<Object>>() {}));
            } catch (JsonProcessingException e) {
                log.error("解析分析JSON失败", e);
            }
            
            return aMap;
        }).toList();
        result.put("analyses", analysisHistory);
        
        // 获取所有面试记录
        List<InterviewSessionEntity> interviews = interviewService.findByResumeId(id);
        List<Map<String, Object>> interviewHistory = interviews.stream().map(this::mapInterviewSession).toList();
        result.put("interviews", interviewHistory);
        
        return Result.success(result);
    }
    
    /**
     * 获取面试会话详情
     * GET /api/history/interviews/{sessionId}
     */
    @GetMapping("/interviews/{sessionId}")
    public Result<Map<String, Object>> getInterviewDetail(@PathVariable String sessionId) {
        Optional<InterviewSessionEntity> sessionOpt = interviewService.findBySessionId(sessionId);
        if (sessionOpt.isEmpty()) {
            return Result.error(404, "面试会话不存在");
        }
        
        InterviewSessionEntity session = sessionOpt.get();
        Map<String, Object> result = mapInterviewSession(session);
        
        // 添加答案详情
        List<InterviewAnswerEntity> answers = session.getAnswers();
        List<Map<String, Object>> answerList = answers.stream().map(a -> {
            Map<String, Object> aMap = new LinkedHashMap<>();
            aMap.put("questionIndex", a.getQuestionIndex());
            aMap.put("question", a.getQuestion());
            aMap.put("category", a.getCategory());
            aMap.put("userAnswer", a.getUserAnswer());
            aMap.put("score", a.getScore());
            aMap.put("feedback", a.getFeedback());
            aMap.put("referenceAnswer", a.getReferenceAnswer());
            // 解析关键点JSON
            if (a.getKeyPointsJson() != null) {
                try {
                    aMap.put("keyPoints", objectMapper.readValue(a.getKeyPointsJson(), 
                        new TypeReference<List<String>>() {}));
                } catch (JsonProcessingException e) {
                    log.error("解析关键点JSON失败", e);
                }
            }
            aMap.put("answeredAt", a.getAnsweredAt());
            return aMap;
        }).toList();
        result.put("answers", answerList);
        
        return Result.success(result);
    }
    
    /**
     * 导出简历分析报告为PDF
     * GET /api/history/export/analysis/{resumeId}
     */
    @GetMapping("/export/analysis/{resumeId}")
    public ResponseEntity<byte[]> exportAnalysisPdf(@PathVariable Long resumeId) {
        Optional<ResumeEntity> resumeOpt = resumeService.findById(resumeId);
        if (resumeOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        ResumeEntity resume = resumeOpt.get();
        Optional<ResumeAnalysisResponse> analysisOpt = resumeService.getLatestAnalysisAsDTO(resumeId);
        if (analysisOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        try {
            byte[] pdfBytes = pdfExportService.exportResumeAnalysis(resume, analysisOpt.get());
            String filename = URLEncoder.encode("简历分析报告_" + resume.getOriginalFilename() + ".pdf", 
                StandardCharsets.UTF_8);
            
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
        } catch (Exception e) {
            log.error("导出PDF失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 导出面试报告为PDF
     * GET /api/history/export/interview/{sessionId}
     */
    @GetMapping("/export/interview/{sessionId}")
    public ResponseEntity<byte[]> exportInterviewPdf(@PathVariable String sessionId) {
        Optional<InterviewSessionEntity> sessionOpt = interviewService.findBySessionId(sessionId);
        if (sessionOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        InterviewSessionEntity session = sessionOpt.get();
        
        try {
            byte[] pdfBytes = pdfExportService.exportInterviewReport(session);
            String filename = URLEncoder.encode("模拟面试报告_" + session.getSessionId() + ".pdf", 
                StandardCharsets.UTF_8);
            
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
        } catch (Exception e) {
            log.error("导出PDF失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 映射面试会话到Map
     */
    private Map<String, Object> mapInterviewSession(InterviewSessionEntity session) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", session.getId());
        map.put("sessionId", session.getSessionId());
        map.put("totalQuestions", session.getTotalQuestions());
        map.put("status", session.getStatus());
        map.put("overallScore", session.getOverallScore());
        map.put("overallFeedback", session.getOverallFeedback());
        map.put("createdAt", session.getCreatedAt());
        map.put("completedAt", session.getCompletedAt());
        
        try {
            if (session.getQuestionsJson() != null) {
                map.put("questions", objectMapper.readValue(session.getQuestionsJson(), 
                    new TypeReference<List<Object>>() {}));
            }
            if (session.getStrengthsJson() != null) {
                map.put("strengths", objectMapper.readValue(session.getStrengthsJson(), 
                    new TypeReference<List<String>>() {}));
            }
            if (session.getImprovementsJson() != null) {
                map.put("improvements", objectMapper.readValue(session.getImprovementsJson(), 
                    new TypeReference<List<String>>() {}));
            }
            if (session.getReferenceAnswersJson() != null) {
                map.put("referenceAnswers", objectMapper.readValue(session.getReferenceAnswersJson(), 
                    new TypeReference<List<Object>>() {}));
            }
        } catch (JsonProcessingException e) {
            log.error("解析面试JSON失败", e);
        }
        
        return map;
    }
}
