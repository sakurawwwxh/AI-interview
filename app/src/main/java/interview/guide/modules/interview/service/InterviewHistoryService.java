package interview.guide.modules.interview.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.infrastructure.export.PdfExportService;
import interview.guide.modules.interview.model.InterviewAnswerEntity;
import interview.guide.modules.interview.model.InterviewDetailDTO;
import interview.guide.modules.interview.model.InterviewSessionEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 面试历史服务
 * Interview History Service
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewHistoryService {
    
    private final InterviewPersistenceService interviewPersistenceService;
    private final PdfExportService pdfExportService;
    private final ObjectMapper objectMapper;
    
    /**
     * 获取面试会话详情
     */
    public InterviewDetailDTO getInterviewDetail(String sessionId) {
        Optional<InterviewSessionEntity> sessionOpt = interviewPersistenceService.findBySessionId(sessionId);
        if (sessionOpt.isEmpty()) {
            throw new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND);
        }
        
        InterviewSessionEntity session = sessionOpt.get();
        
        // 解析JSON字段
        List<Object> questions = List.of();
        List<String> strengths = List.of();
        List<String> improvements = List.of();
        List<Object> referenceAnswers = List.of();
        
        try {
            if (session.getQuestionsJson() != null) {
                questions = objectMapper.readValue(
                    session.getQuestionsJson(),
                    new TypeReference<List<Object>>() {}
                );
            }
            if (session.getStrengthsJson() != null) {
                strengths = objectMapper.readValue(
                    session.getStrengthsJson(),
                    new TypeReference<List<String>>() {}
                );
            }
            if (session.getImprovementsJson() != null) {
                improvements = objectMapper.readValue(
                    session.getImprovementsJson(),
                    new TypeReference<List<String>>() {}
                );
            }
            if (session.getReferenceAnswersJson() != null) {
                referenceAnswers = objectMapper.readValue(
                    session.getReferenceAnswersJson(),
                    new TypeReference<List<Object>>() {}
                );
            }
        } catch (JsonProcessingException e) {
            log.error("解析面试JSON失败", e);
        }
        
        // 添加答案详情
        List<InterviewAnswerEntity> answers = session.getAnswers();
        List<InterviewDetailDTO.AnswerDetailDTO> answerList = answers.stream().map(a -> {
            List<String> keyPoints = List.of();
            if (a.getKeyPointsJson() != null) {
                try {
                    keyPoints = objectMapper.readValue(
                        a.getKeyPointsJson(),
                        new TypeReference<List<String>>() {}
                    );
                } catch (JsonProcessingException e) {
                    log.error("解析关键点JSON失败", e);
                }
            }
            
            return new InterviewDetailDTO.AnswerDetailDTO(
                a.getQuestionIndex(),
                a.getQuestion(),
                a.getCategory(),
                a.getUserAnswer(),
                a.getScore(),
                a.getFeedback(),
                a.getReferenceAnswer(),
                keyPoints,
                a.getAnsweredAt()
            );
        }).toList();
        
        return new InterviewDetailDTO(
            session.getId(),
            session.getSessionId(),
            session.getTotalQuestions(),
            session.getStatus().toString(),
            session.getOverallScore(),
            session.getOverallFeedback(),
            session.getCreatedAt(),
            session.getCompletedAt(),
            questions,
            strengths,
            improvements,
            referenceAnswers,
            answerList
        );
    }
    
    /**
     * 导出面试报告为PDF
     */
    public byte[] exportInterviewPdf(String sessionId) {
        Optional<InterviewSessionEntity> sessionOpt = interviewPersistenceService.findBySessionId(sessionId);
        if (sessionOpt.isEmpty()) {
            throw new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND);
        }
        
        InterviewSessionEntity session = sessionOpt.get();
        try {
            return pdfExportService.exportInterviewReport(session);
        } catch (Exception e) {
            log.error("导出PDF失败: sessionId={}", sessionId, e);
            throw new BusinessException(ErrorCode.EXPORT_PDF_FAILED, "导出PDF失败: " + e.getMessage());
        }
    }
}

