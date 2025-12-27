package interview.guide.modules.resume.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import interview.guide.modules.interview.model.ResumeAnalysisResponse;
import interview.guide.modules.interview.model.ResumeAnalysisResponse.ScoreDetail;
import interview.guide.modules.interview.model.ResumeAnalysisResponse.Suggestion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 简历评分服务
 * 使用Spring AI调用LLM对简历进行评分和建议
 * Resume Grading Service using Spring AI
 */
@Service
public class ResumeGradingService {
    
    private static final Logger log = LoggerFactory.getLogger(ResumeGradingService.class);
    
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    
    private static final String SYSTEM_PROMPT = """
        你是一位资深的HR专家和简历顾问，拥有10年以上的招聘经验。
        你的任务是分析候选人的简历，提供专业的评分和改进建议。
        
        评分维度（总分100分）：
        1. 内容完整性 (0-25分)：教育背景、工作经验、技能、项目经历是否完整
        2. 结构清晰度 (0-20分)：简历结构是否清晰、易读、逻辑性强
        3. 技能匹配度 (0-25分)：技术技能描述是否具体、有深度
        4. 表达专业性 (0-15分)：语言表达是否专业、简洁、有力
        5. 项目经验 (0-15分)：项目描述是否突出成果和贡献
        
        请严格按照以下JSON格式输出，不要有任何额外文字：
        {
            "overallScore": <总分>,
            "scoreDetail": {
                "contentScore": <内容完整性分数>,
                "structureScore": <结构清晰度分数>,
                "skillMatchScore": <技能匹配度分数>,
                "expressionScore": <表达专业性分数>,
                "projectScore": <项目经验分数>
            },
            "summary": "<简历整体评价，100字以内>",
            "strengths": ["<优点1>", "<优点2>", "<优点3>"],
            "suggestions": [
                {
                    "category": "<类别：内容/结构/技能/表达/项目>",
                    "priority": "<优先级：高/中/低>",
                    "issue": "<问题描述>",
                    "recommendation": "<具体改进建议>"
                }
            ]
        }
        """;
    
    public ResumeGradingService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * 分析简历并返回评分和建议
     * 
     * @param resumeText 简历文本内容
     * @return 分析结果
     */
    public ResumeAnalysisResponse analyzeResume(String resumeText) {
        log.info("开始分析简历，文本长度: {} 字符", resumeText.length());
        
        String userPrompt = """
            请分析以下简历内容，按照要求给出评分和改进建议：
            
            ---简历内容开始---
            %s
            ---简历内容结束---
            
            请严格按照JSON格式输出分析结果。
            """.formatted(resumeText);
        
        try {
            SystemMessage systemMessage = new SystemMessage(SYSTEM_PROMPT);
            UserMessage userMessage = new UserMessage(userPrompt);
            Prompt prompt = new Prompt(List.of(systemMessage, userMessage));
            
            String response = chatClient.prompt(prompt)
                    .call()
                    .content();
            
            log.debug("LLM响应: {}", response);
            
            // 解析JSON响应
            ResumeAnalysisResponse result = parseResponse(response, resumeText);
            log.info("简历分析完成，总分: {}", result.overallScore());
            
            return result;
            
        } catch (Exception e) {
            log.error("简历分析失败: {}", e.getMessage(), e);
            // 返回一个默认的错误响应
            return createErrorResponse(resumeText, e.getMessage());
        }
    }
    
    /**
     * 解析LLM响应为结构化对象
     */
    private ResumeAnalysisResponse parseResponse(String response, String originalText) {
        try {
            // 提取JSON部分（处理可能的前后缀文字）
            String jsonStr = extractJson(response);
            
            // 解析为中间对象
            var jsonNode = objectMapper.readTree(jsonStr);
            
            int overallScore = jsonNode.get("overallScore").asInt();
            
            var scoreNode = jsonNode.get("scoreDetail");
            ScoreDetail scoreDetail = new ScoreDetail(
                scoreNode.get("contentScore").asInt(),
                scoreNode.get("structureScore").asInt(),
                scoreNode.get("skillMatchScore").asInt(),
                scoreNode.get("expressionScore").asInt(),
                scoreNode.get("projectScore").asInt()
            );
            
            String summary = jsonNode.get("summary").asText();
            
            List<String> strengths = objectMapper.convertValue(
                jsonNode.get("strengths"),
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
            );
            
            List<Suggestion> suggestions = new java.util.ArrayList<>();
            var suggestionsNode = jsonNode.get("suggestions");
            if (suggestionsNode != null && suggestionsNode.isArray()) {
                for (var suggestionNode : suggestionsNode) {
                    suggestions.add(new Suggestion(
                        suggestionNode.get("category").asText(),
                        suggestionNode.get("priority").asText(),
                        suggestionNode.get("issue").asText(),
                        suggestionNode.get("recommendation").asText()
                    ));
                }
            }
            
            return new ResumeAnalysisResponse(
                overallScore,
                scoreDetail,
                summary,
                strengths,
                suggestions,
                originalText
            );
            
        } catch (JsonProcessingException e) {
            log.error("解析LLM响应失败: {}", e.getMessage());
            return createErrorResponse(originalText, "AI响应解析失败");
        }
    }
    
    /**
     * 从响应中提取JSON字符串
     */
    private String extractJson(String response) {
        // 找到第一个{和最后一个}
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        
        if (start != -1 && end != -1 && end > start) {
            return response.substring(start, end + 1);
        }
        
        return response;
    }
    
    /**
     * 创建错误响应
     */
    private ResumeAnalysisResponse createErrorResponse(String originalText, String errorMessage) {
        return new ResumeAnalysisResponse(
            0,
            new ScoreDetail(0, 0, 0, 0, 0),
            "分析过程中出现错误: " + errorMessage,
            List.of(),
            List.of(new Suggestion(
                "系统",
                "高",
                "AI分析服务暂时不可用",
                "请稍后重试，或检查Ollama服务是否正常运行"
            )),
            originalText
        );
    }
}
