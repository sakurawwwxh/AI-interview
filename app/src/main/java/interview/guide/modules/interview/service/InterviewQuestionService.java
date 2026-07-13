package interview.guide.modules.interview.service;

import interview.guide.common.ai.StructuredOutputInvoker;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.model.InterviewTemplateConfig;
import interview.guide.modules.interview.model.InterviewQuestionDTO;
import interview.guide.modules.interview.model.InterviewQuestionDTO.QuestionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 面试问题生成服务
 * 基于简历内容生成针对性的面试问题
 */
@Service
public class InterviewQuestionService {
    
    private static final Logger log = LoggerFactory.getLogger(InterviewQuestionService.class);
    
    private final ChatClient chatClient;
    private final PromptTemplate systemPromptTemplate;
    private final PromptTemplate userPromptTemplate;
    private final PromptTemplate followUpSystemPromptTemplate;
    private final BeanOutputConverter<QuestionListDTO> outputConverter;
    private final BeanOutputConverter<FollowUpDecisionDTO> followUpOutputConverter;
    private final StructuredOutputInvoker structuredOutputInvoker;
    private final int followUpCount;
    
    private static final int MAX_FOLLOW_UP_COUNT = 2;

    // 中间DTO用于接收AI响应
    private record QuestionListDTO(
        List<QuestionDTO> questions
    ) {}
    
    private record QuestionDTO(
        String question,
        String type,
        String category,
        List<String> followUps
    ) {}

    private record FollowUpDecisionDTO(
        boolean shouldFollowUp,
        String followUpQuestion,
        String category
    ) {}
    
    public InterviewQuestionService(
            ChatClient.Builder chatClientBuilder,
            StructuredOutputInvoker structuredOutputInvoker,
            @Value("classpath:prompts/interview-question-system.st") Resource systemPromptResource,
            @Value("classpath:prompts/interview-question-user.st") Resource userPromptResource,
            @Value("classpath:prompts/interview-followup-system.st") Resource followUpSystemPromptResource,
            @Value("${app.interview.follow-up-count:1}") int followUpCount) throws IOException {
        this.chatClient = chatClientBuilder.build();
        this.structuredOutputInvoker = structuredOutputInvoker;
        this.systemPromptTemplate = new PromptTemplate(systemPromptResource.getContentAsString(StandardCharsets.UTF_8));
        this.userPromptTemplate = new PromptTemplate(userPromptResource.getContentAsString(StandardCharsets.UTF_8));
        this.followUpSystemPromptTemplate = new PromptTemplate(followUpSystemPromptResource.getContentAsString(StandardCharsets.UTF_8));
        this.outputConverter = new BeanOutputConverter<>(QuestionListDTO.class);
        this.followUpOutputConverter = new BeanOutputConverter<>(FollowUpDecisionDTO.class);
        this.followUpCount = Math.max(0, Math.min(followUpCount, MAX_FOLLOW_UP_COUNT));
    }
    
    /**
     * 生成面试问题
     * 
     * @param resumeText 简历文本
     * @param questionCount 问题数量
     * @param historicalQuestions 历史问题列表（可选）
     * @return 面试问题列表
     */
    public List<InterviewQuestionDTO> generateQuestions(String resumeText, int questionCount, List<String> historicalQuestions) {
        return generateQuestions(resumeText, questionCount, historicalQuestions,
            InterviewTemplateConfig.defaultBackend(followUpCount));
    }

    public List<InterviewQuestionDTO> generateQuestions(
            String resumeText,
            int questionCount,
            List<String> historicalQuestions,
            InterviewTemplateConfig template) {
        log.info("开始生成面试问题，简历长度: {}, 问题数量: {}, 历史问题数: {}", 
            resumeText.length(), questionCount, historicalQuestions != null ? historicalQuestions.size() : 0);
        
        InterviewTemplateConfig normalizedTemplate = template.normalize(followUpCount);
        TemplateDistribution distribution = calculateTemplateDistribution(questionCount, normalizedTemplate);
        
        try {
            // 加载系统提示词
            String systemPrompt = systemPromptTemplate.render();
            
            // 加载用户提示词并填充变量
            Map<String, Object> variables = new HashMap<>();
            variables.put("questionCount", questionCount);
            variables.put("questionPlan", distribution.questionPlan());
            variables.put("difficultyDistribution", difficultyText(normalizedTemplate.difficultyDistribution()));
            variables.put("followUpCount", normalizedTemplate.followUpCount());
            variables.put("resumeText", resumeText);
            
            // 添加历史问题
            if (historicalQuestions != null && !historicalQuestions.isEmpty()) {
                String historicalText = String.join("\n", historicalQuestions);
                variables.put("historicalQuestions", historicalText);
            } else {
                variables.put("historicalQuestions", "暂无历史提问");
            }
            
            String userPrompt = userPromptTemplate.render(variables);
            
            // 添加格式指令到系统提示词
            String systemPromptWithFormat = systemPrompt + "\n\n" + outputConverter.getFormat();
            
            // 调用AI
            QuestionListDTO dto;
            try {
                dto = structuredOutputInvoker.invoke(
                    chatClient,
                    systemPromptWithFormat,
                    userPrompt,
                    outputConverter,
                    ErrorCode.INTERVIEW_QUESTION_GENERATION_FAILED,
                    "面试问题生成失败：",
                    "结构化问题生成",
                    log
                );
                log.debug("AI响应解析成功: questions count={}", dto.questions().size());
            } catch (Exception e) {
                log.error("面试问题生成AI调用失败: {}", e.getMessage(), e);
                throw new BusinessException(ErrorCode.INTERVIEW_QUESTION_GENERATION_FAILED, 
                    "面试问题生成失败：" + e.getMessage());
            }
            
            // 转换为业务对象
            List<InterviewQuestionDTO> questions = convertToQuestions(dto, normalizedTemplate.followUpCount());
            log.info("成功生成 {} 个面试问题", questions.size());
            
            return questions;
            
        } catch (BusinessException e) {
            // 业务异常（如 AI 调用明确失败）应向上传播，不降级为默认题库
            throw e;
        } catch (Exception e) {
            log.error("生成面试问题失败，降级为默认题库: {}", e.getMessage(), e);
            // 返回默认问题集
            return generateDefaultQuestions(questionCount, normalizedTemplate.followUpCount());
        }
    }

    /**
     * 生成面试问题（不带历史问题）
     */
    public List<InterviewQuestionDTO> generateQuestions(String resumeText, int questionCount) {
        return generateQuestions(resumeText, questionCount, null);
    }
    
    /**
     * 转换DTO为业务对象
     */
    private List<InterviewQuestionDTO> convertToQuestions(QuestionListDTO dto, int followUpLimit) {
        List<InterviewQuestionDTO> questions = new ArrayList<>();
        int index = 0;

        if (dto == null || dto.questions() == null) {
            return questions;
        }

        for (QuestionDTO q : dto.questions()) {
            if (q == null || q.question() == null || q.question().isBlank()) {
                continue;
            }
            QuestionType type = parseQuestionType(q.type());
            questions.add(InterviewQuestionDTO.create(index++, q.question(), type, q.category(), false, null));

        }
        
        return questions;
    }
    
    private QuestionType parseQuestionType(String typeStr) {
        try {
            return QuestionType.valueOf(typeStr.toUpperCase());
        } catch (Exception e) {
            return QuestionType.JAVA_BASIC;
        }
    }
    
    /**
     * 生成默认问题（备用）
     */
    private List<InterviewQuestionDTO> generateDefaultQuestions(int count, int followUpLimit) {
        List<InterviewQuestionDTO> questions = new ArrayList<>();
        
        String[][] defaultQuestions = {
            {"请介绍一下你在简历中提到的最重要的项目，你在其中承担了什么角色？", "PROJECT", "项目经历"},
            {"MySQL的索引有哪些类型？B+树索引的原理是什么？", "MYSQL", "MySQL"},
            {"Redis支持哪些数据结构？各自的使用场景是什么？", "REDIS", "Redis"},
            {"Java中HashMap的底层实现原理是什么？JDK8做了哪些优化？", "JAVA_COLLECTION", "Java集合"},
            {"synchronized和ReentrantLock有什么区别？", "JAVA_CONCURRENT", "Java并发"},
            {"Spring的IoC和AOP原理是什么？", "SPRING", "Spring"},
            {"MySQL事务的ACID特性是什么？隔离级别有哪些？", "MYSQL", "MySQL"},
            {"Redis的持久化机制有哪些？RDB和AOF的区别？", "REDIS", "Redis"},
            {"Java的垃圾回收机制是怎样的？常见的GC算法有哪些？", "JAVA_BASIC", "Java基础"},
            {"线程池的核心参数有哪些？如何合理配置？", "JAVA_CONCURRENT", "Java并发"},
        };
        
        int index = 0;
        for (int i = 0; i < Math.min(count, defaultQuestions.length); i++) {
            String mainQuestion = defaultQuestions[i][0];
            QuestionType type = QuestionType.valueOf(defaultQuestions[i][1]);
            String category = defaultQuestions[i][2];
            questions.add(InterviewQuestionDTO.create(
                index++,
                mainQuestion,
                type,
                category,
                false,
                null
            ));

        }
        
        return questions;
    }

    private String buildFollowUpCategory(String category, int order) {
        String baseCategory = (category == null || category.isBlank()) ? "追问" : category;
        return baseCategory + "（追问" + order + "）";
    }

    private TemplateDistribution calculateTemplateDistribution(int total, InterviewTemplateConfig template) {
        int totalWeight = template.questionTypes().stream()
            .mapToInt(InterviewTemplateConfig.QuestionTypeWeight::weight)
            .sum();
        List<Integer> counts = new ArrayList<>();
        List<Double> remainders = new ArrayList<>();
        int assigned = 0;
        for (InterviewTemplateConfig.QuestionTypeWeight item : template.questionTypes()) {
            double exact = (double) total * item.weight() / totalWeight;
            int count = (int) Math.floor(exact);
            counts.add(count);
            remainders.add(exact - count);
            assigned += count;
        }
        for (int i = assigned; i < total; i++) {
            int maxIndex = 0;
            for (int j = 1; j < remainders.size(); j++) {
                if (remainders.get(j) > remainders.get(maxIndex)) {
                    maxIndex = j;
                }
            }
            counts.set(maxIndex, counts.get(maxIndex) + 1);
            remainders.set(maxIndex, -1D);
        }
        StringBuilder plan = new StringBuilder("| 类型 | 数量 |\\n|------|------|\\n");
        for (int i = 0; i < template.questionTypes().size(); i++) {
            if (counts.get(i) > 0) {
                plan.append("| ").append(template.questionTypes().get(i).type().name())
                    .append(" | ").append(counts.get(i)).append(" 题 |\\n");
            }
        }
        return new TemplateDistribution(plan.toString());
    }

    private String difficultyText(InterviewTemplateConfig.DifficultyDistribution difficulty) {
        int total = difficulty.basic() + difficulty.advanced() + difficulty.expert();
        return "基础 " + Math.round(difficulty.basic() * 100.0 / total) + "% / 进阶 "
            + Math.round(difficulty.advanced() * 100.0 / total) + "% / 专家 "
            + Math.round(difficulty.expert() * 100.0 / total) + "%";
    }

    private record TemplateDistribution(String questionPlan) {
    }

    public int getDefaultFollowUpCount() {
        return followUpCount;
    }

    /**
     * 根据刚提交的回答决定是否立即深挖。失败时返回空，主流程继续下一题。
     */
    public Optional<InterviewQuestionDTO> generateDynamicFollowUp(
        InterviewQuestionDTO question,
        String answer,
        int nextQuestionIndex,
        int parentQuestionIndex,
        int existingFollowUpCount,
        int maximumFollowUpCount
    ) {
        if (maximumFollowUpCount <= existingFollowUpCount || answer == null || answer.isBlank()) {
            return Optional.empty();
        }
        String systemPrompt = followUpSystemPromptTemplate.render() + "\n\n" + followUpOutputConverter.getFormat();
        String userPrompt = "原题：" + question.question() + "\n题型：" + question.type() +
            "\n候选人回答：" + answer + "\n已有追问数：" + existingFollowUpCount +
            "，最多追问数：" + maximumFollowUpCount;
        try {
            FollowUpDecisionDTO decision = structuredOutputInvoker.invoke(
                chatClient,
                systemPrompt,
                userPrompt,
                followUpOutputConverter,
                ErrorCode.INTERVIEW_QUESTION_GENERATION_FAILED,
                "动态追问生成失败",
                "动态追问",
                log
            );
            if (decision == null || !decision.shouldFollowUp() || decision.followUpQuestion() == null
                || decision.followUpQuestion().isBlank()) {
                return Optional.empty();
            }
            String category = decision.category() == null || decision.category().isBlank()
                ? buildFollowUpCategory(question.category(), existingFollowUpCount + 1)
                : decision.category().trim();
            return Optional.of(InterviewQuestionDTO.create(
                nextQuestionIndex,
                decision.followUpQuestion().trim(),
                question.type(),
                category,
                true,
                parentQuestionIndex
            ));
        } catch (Exception e) {
            log.warn("动态追问决策失败，将继续下一题: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
