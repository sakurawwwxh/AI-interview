package interview.guide.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import interview.guide.dto.InterviewQuestionDTO;
import interview.guide.dto.InterviewReportDTO;
import interview.guide.dto.InterviewReportDTO.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 答案评估服务
 * 评估用户回答并生成面试报告
 */
@Service
public class AnswerEvaluationService {
    
    private static final Logger log = LoggerFactory.getLogger(AnswerEvaluationService.class);
    
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    
    private static final String EVALUATION_SYSTEM_PROMPT = """
        你是一位资深的Java后端技术面试官，需要评估候选人的面试回答。
        
        评估标准：
        1. 准确性：回答是否正确，概念是否清晰
        2. 完整性：是否覆盖了问题的关键点
        3. 深度：是否有深入的理解和实践经验
        4. 表达：回答是否清晰有条理
        
        评分标准（0-100分）：
        - 90-100：优秀，回答全面深入，有独到见解
        - 75-89：良好，回答正确完整，有一定深度
        - 60-74：及格，基本正确但不够深入
        - 40-59：不及格，有明显错误或遗漏
        - 0-39：较差，回答错误或答非所问
        
        请严格按照JSON格式输出评估结果。
        """;
    
    public AnswerEvaluationService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * 评估完整面试并生成报告
     */
    public InterviewReportDTO evaluateInterview(String sessionId, String resumeText,
                                                 List<InterviewQuestionDTO> questions) {
        log.info("开始评估面试: {}, 共{}题", sessionId, questions.size());
        
        try {
            String evaluationPrompt = buildEvaluationPrompt(resumeText, questions);
            
            SystemMessage systemMessage = new SystemMessage(EVALUATION_SYSTEM_PROMPT);
            UserMessage userMessage = new UserMessage(evaluationPrompt);
            Prompt prompt = new Prompt(List.of(systemMessage, userMessage));
            
            String response = chatClient.prompt(prompt)
                    .call()
                    .content();
            
            log.debug("评估响应: {}", response);
            
            return parseEvaluationResponse(sessionId, response, questions);
            
        } catch (Exception e) {
            log.error("面试评估失败: {}", e.getMessage(), e);
            return createErrorReport(sessionId, questions);
        }
    }
    
    private String buildEvaluationPrompt(String resumeText, List<InterviewQuestionDTO> questions) {
        StringBuilder sb = new StringBuilder();
        sb.append("请评估以下面试表现，并生成详细的面试报告。\n\n");
        sb.append("---候选人简历摘要---\n");
        sb.append(resumeText.length() > 500 ? resumeText.substring(0, 500) + "..." : resumeText);
        sb.append("\n\n---面试问答记录---\n");
        
        for (InterviewQuestionDTO q : questions) {
            sb.append(String.format("问题%d [%s]: %s\n", 
                q.questionIndex() + 1, q.category(), q.question()));
            sb.append(String.format("回答: %s\n\n", 
                q.userAnswer() != null ? q.userAnswer() : "(未回答)"));
        }
        
        sb.append("""
            
            请按以下JSON格式输出评估报告，不要有任何额外文字：
            {
                "overallScore": <总分0-100>,
                "overallFeedback": "<总体评价，150字以内>",
                "strengths": ["<优势1>", "<优势2>", "<优势3>"],
                "improvements": ["<改进建议1>", "<改进建议2>", "<改进建议3>"],
                "questionEvaluations": [
                    {
                        "questionIndex": 0,
                        "score": <分数0-100>,
                        "feedback": "<评价>",
                        "referenceAnswer": "<参考答案>",
                        "keyPoints": ["<要点1>", "<要点2>"]
                    }
                ]
            }
            """);
        
        return sb.toString();
    }
    
    private InterviewReportDTO parseEvaluationResponse(String sessionId, String response,
                                                       List<InterviewQuestionDTO> questions) {
        try {
            String jsonStr = extractJson(response);
            JsonNode root = objectMapper.readTree(jsonStr);
            
            int overallScore = root.get("overallScore").asInt();
            String overallFeedback = root.get("overallFeedback").asText();
            
            List<String> strengths = parseStringList(root.get("strengths"));
            List<String> improvements = parseStringList(root.get("improvements"));
            
            List<QuestionEvaluation> questionDetails = new ArrayList<>();
            List<ReferenceAnswer> referenceAnswers = new ArrayList<>();
            Map<String, List<Integer>> categoryScoresMap = new HashMap<>();
            
            JsonNode evaluationsNode = root.get("questionEvaluations");
            if (evaluationsNode != null && evaluationsNode.isArray()) {
                // 按数组顺序处理，不依赖AI返回的questionIndex（可能从0或1开始）
                int index = 0;
                for (JsonNode evalNode : evaluationsNode) {
                    if (index >= questions.size()) {
                        break;
                    }
                    
                    int score = evalNode.get("score").asInt();
                    String feedback = evalNode.get("feedback").asText();
                    String refAnswer = evalNode.has("referenceAnswer") 
                        ? evalNode.get("referenceAnswer").asText() : "";
                    List<String> keyPoints = evalNode.has("keyPoints") 
                        ? parseStringList(evalNode.get("keyPoints")) : List.of();
                    
                    // 使用我们自己的索引，而不是AI返回的
                    InterviewQuestionDTO q = questions.get(index);
                    int qIndex = q.questionIndex();
                    
                    questionDetails.add(new QuestionEvaluation(
                        qIndex, q.question(), q.category(),
                        q.userAnswer(), score, feedback
                    ));
                    
                    referenceAnswers.add(new ReferenceAnswer(
                        qIndex, q.question(), refAnswer, keyPoints
                    ));
                    
                    // 收集类别分数
                    categoryScoresMap
                        .computeIfAbsent(q.category(), k -> new ArrayList<>())
                        .add(score);
                    
                    index++;
                }
            }
            
            // 计算各类别平均分
            List<CategoryScore> categoryScores = categoryScoresMap.entrySet().stream()
                .map(e -> new CategoryScore(
                    e.getKey(),
                    (int) e.getValue().stream().mapToInt(Integer::intValue).average().orElse(0),
                    e.getValue().size()
                ))
                .collect(Collectors.toList());
            
            return new InterviewReportDTO(
                sessionId,
                questions.size(),
                overallScore,
                categoryScores,
                questionDetails,
                overallFeedback,
                strengths,
                improvements,
                referenceAnswers
            );
            
        } catch (Exception e) {
            log.error("解析评估结果失败: {}", e.getMessage());
            return createErrorReport(sessionId, questions);
        }
    }
    
    private List<String> parseStringList(JsonNode node) {
        List<String> list = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                list.add(item.asText());
            }
        }
        return list;
    }
    
    private String extractJson(String response) {
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start != -1 && end != -1 && end > start) {
            return response.substring(start, end + 1);
        }
        return response;
    }
    
    private InterviewReportDTO createErrorReport(String sessionId, List<InterviewQuestionDTO> questions) {
        return new InterviewReportDTO(
            sessionId,
            questions.size(),
            0,
            List.of(),
            questions.stream()
                .map(q -> new QuestionEvaluation(
                    q.questionIndex(), q.question(), q.category(),
                    q.userAnswer(), 0, "评估服务暂时不可用"
                ))
                .collect(Collectors.toList()),
            "评估过程中出现错误，请稍后重试",
            List.of(),
            List.of("请检查AI服务是否正常运行"),
            List.of()
        );
    }
}
