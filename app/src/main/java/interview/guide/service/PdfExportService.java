package interview.guide.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import interview.guide.dto.ResumeAnalysisResponse;
import interview.guide.entity.InterviewAnswerEntity;
import interview.guide.entity.InterviewSessionEntity;
import interview.guide.entity.ResumeEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * PDF导出服务
 * PDF Export Service for resume analysis and interview reports
 */
@Service
public class PdfExportService {
    
    private static final Logger log = LoggerFactory.getLogger(PdfExportService.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DeviceRgb HEADER_COLOR = new DeviceRgb(41, 128, 185);
    private static final DeviceRgb SECTION_COLOR = new DeviceRgb(52, 73, 94);
    
    private final ObjectMapper objectMapper;
    
    public PdfExportService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    /**
     * 导出简历分析报告为PDF
     */
    public byte[] exportResumeAnalysis(ResumeEntity resume, ResumeAnalysisResponse analysis) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdfDoc = new PdfDocument(writer);
        Document document = new Document(pdfDoc);
        
        // 使用内置字体支持中文
        PdfFont font = PdfFontFactory.createFont("STSongStd-Light", "UniGB-UCS2-H");
        document.setFont(font);
        
        // 标题
        Paragraph title = new Paragraph("简历分析报告")
            .setFontSize(24)
            .setBold()
            .setTextAlignment(TextAlignment.CENTER)
            .setFontColor(HEADER_COLOR);
        document.add(title);
        
        // 基本信息
        document.add(new Paragraph("\n"));
        document.add(createSectionTitle("基本信息"));
        document.add(new Paragraph("文件名: " + resume.getOriginalFilename()));
        document.add(new Paragraph("上传时间: " + 
            (resume.getUploadedAt() != null ? DATE_FORMAT.format(resume.getUploadedAt()) : "未知")));
        
        // 总分
        document.add(new Paragraph("\n"));
        document.add(createSectionTitle("综合评分"));
        Paragraph scoreP = new Paragraph("总分: " + analysis.overallScore() + " / 100")
            .setFontSize(18)
            .setBold()
            .setFontColor(getScoreColor(analysis.overallScore()));
        document.add(scoreP);
        
        // 各维度评分
        if (analysis.scoreDetail() != null) {
            document.add(new Paragraph("\n"));
            document.add(createSectionTitle("各维度评分"));
            
            Table scoreTable = new Table(UnitValue.createPercentArray(new float[]{2, 1}))
                .useAllAvailableWidth();
            addScoreRow(scoreTable, "内容完整性", analysis.scoreDetail().contentScore(), 25);
            addScoreRow(scoreTable, "结构清晰度", analysis.scoreDetail().structureScore(), 20);
            addScoreRow(scoreTable, "技能匹配度", analysis.scoreDetail().skillMatchScore(), 25);
            addScoreRow(scoreTable, "表达专业性", analysis.scoreDetail().expressionScore(), 15);
            addScoreRow(scoreTable, "项目经验", analysis.scoreDetail().projectScore(), 15);
            document.add(scoreTable);
        }
        
        // 简历摘要
        if (analysis.summary() != null) {
            document.add(new Paragraph("\n"));
            document.add(createSectionTitle("简历摘要"));
            document.add(new Paragraph(analysis.summary()));
        }
        
        // 优势亮点
        if (analysis.strengths() != null && !analysis.strengths().isEmpty()) {
            document.add(new Paragraph("\n"));
            document.add(createSectionTitle("优势亮点"));
            for (String strength : analysis.strengths()) {
                document.add(new Paragraph("• " + strength));
            }
        }
        
        // 改进建议
        if (analysis.suggestions() != null && !analysis.suggestions().isEmpty()) {
            document.add(new Paragraph("\n"));
            document.add(createSectionTitle("改进建议"));
            for (ResumeAnalysisResponse.Suggestion suggestion : analysis.suggestions()) {
                document.add(new Paragraph("【" + suggestion.priority() + "】" + suggestion.category())
                    .setBold());
                document.add(new Paragraph("问题: " + suggestion.issue()));
                document.add(new Paragraph("建议: " + suggestion.recommendation()));
                document.add(new Paragraph("\n"));
            }
        }
        
        document.close();
        return baos.toByteArray();
    }
    
    /**
     * 导出面试报告为PDF
     */
    public byte[] exportInterviewReport(InterviewSessionEntity session) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdfDoc = new PdfDocument(writer);
        Document document = new Document(pdfDoc);
        
        // 使用内置字体支持中文
        PdfFont font = PdfFontFactory.createFont("STSongStd-Light", "UniGB-UCS2-H");
        document.setFont(font);
        
        // 标题
        Paragraph title = new Paragraph("模拟面试报告")
            .setFontSize(24)
            .setBold()
            .setTextAlignment(TextAlignment.CENTER)
            .setFontColor(HEADER_COLOR);
        document.add(title);
        
        // 基本信息
        document.add(new Paragraph("\n"));
        document.add(createSectionTitle("面试信息"));
        document.add(new Paragraph("会话ID: " + session.getSessionId()));
        document.add(new Paragraph("题目数量: " + session.getTotalQuestions()));
        document.add(new Paragraph("面试状态: " + getStatusText(session.getStatus())));
        document.add(new Paragraph("开始时间: " + 
            (session.getCreatedAt() != null ? DATE_FORMAT.format(session.getCreatedAt()) : "未知")));
        if (session.getCompletedAt() != null) {
            document.add(new Paragraph("完成时间: " + DATE_FORMAT.format(session.getCompletedAt())));
        }
        
        // 总分
        if (session.getOverallScore() != null) {
            document.add(new Paragraph("\n"));
            document.add(createSectionTitle("综合评分"));
            Paragraph scoreP = new Paragraph("总分: " + session.getOverallScore() + " / 100")
                .setFontSize(18)
                .setBold()
                .setFontColor(getScoreColor(session.getOverallScore()));
            document.add(scoreP);
        }
        
        // 总体评价
        if (session.getOverallFeedback() != null) {
            document.add(new Paragraph("\n"));
            document.add(createSectionTitle("总体评价"));
            document.add(new Paragraph(session.getOverallFeedback()));
        }
        
        // 优势
        if (session.getStrengthsJson() != null) {
            try {
                List<String> strengths = objectMapper.readValue(session.getStrengthsJson(),
                    new TypeReference<List<String>>() {});
                if (!strengths.isEmpty()) {
                    document.add(new Paragraph("\n"));
                    document.add(createSectionTitle("表现优势"));
                    for (String s : strengths) {
                        document.add(new Paragraph("• " + s));
                    }
                }
            } catch (Exception e) {
                log.error("解析优势JSON失败", e);
            }
        }
        
        // 改进建议
        if (session.getImprovementsJson() != null) {
            try {
                List<String> improvements = objectMapper.readValue(session.getImprovementsJson(),
                    new TypeReference<List<String>>() {});
                if (!improvements.isEmpty()) {
                    document.add(new Paragraph("\n"));
                    document.add(createSectionTitle("改进建议"));
                    for (String s : improvements) {
                        document.add(new Paragraph("• " + s));
                    }
                }
            } catch (Exception e) {
                log.error("解析改进建议JSON失败", e);
            }
        }
        
        // 问答详情
        List<InterviewAnswerEntity> answers = session.getAnswers();
        if (answers != null && !answers.isEmpty()) {
            document.add(new Paragraph("\n"));
            document.add(createSectionTitle("问答详情"));
            
            for (InterviewAnswerEntity answer : answers) {
                document.add(new Paragraph("\n"));
                document.add(new Paragraph("问题 " + (answer.getQuestionIndex() + 1) + 
                    " [" + (answer.getCategory() != null ? answer.getCategory() : "综合") + "]")
                    .setBold()
                    .setFontSize(12));
                document.add(new Paragraph("Q: " + answer.getQuestion()));
                document.add(new Paragraph("A: " + (answer.getUserAnswer() != null ? answer.getUserAnswer() : "未回答")));
                document.add(new Paragraph("得分: " + answer.getScore() + "/100")
                    .setFontColor(getScoreColor(answer.getScore())));
                if (answer.getFeedback() != null) {
                    document.add(new Paragraph("评价: " + answer.getFeedback())
                        .setItalic());
                }
                if (answer.getReferenceAnswer() != null) {
                    document.add(new Paragraph("参考答案: " + answer.getReferenceAnswer())
                        .setFontColor(new DeviceRgb(39, 174, 96)));
                }
            }
        }
        
        document.close();
        return baos.toByteArray();
    }
    
    private Paragraph createSectionTitle(String title) {
        return new Paragraph(title)
            .setFontSize(14)
            .setBold()
            .setFontColor(SECTION_COLOR)
            .setMarginTop(10);
    }
    
    private void addScoreRow(Table table, String dimension, int score, int maxScore) {
        table.addCell(new Cell().add(new Paragraph(dimension)));
        table.addCell(new Cell().add(new Paragraph(score + " / " + maxScore)
            .setFontColor(getScoreColor(score * 100 / maxScore))));
    }
    
    private DeviceRgb getScoreColor(int score) {
        if (score >= 80) return new DeviceRgb(39, 174, 96);   // 绿色
        if (score >= 60) return new DeviceRgb(241, 196, 15);  // 黄色
        return new DeviceRgb(231, 76, 60);                    // 红色
    }
    
    private String getStatusText(InterviewSessionEntity.SessionStatus status) {
        return switch (status) {
            case CREATED -> "已创建";
            case IN_PROGRESS -> "进行中";
            case COMPLETED -> "已完成";
            case EVALUATED -> "已评估";
        };
    }
}
