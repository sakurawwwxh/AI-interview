package interview.guide.modules.interview.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import interview.guide.modules.interview.model.InterviewQuestionDTO;
import interview.guide.modules.interview.model.InterviewQuestionDTO.QuestionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 面试问题生成服务
 * 基于简历内容生成针对性的面试问题
 */
@Service
public class InterviewQuestionService {
    
    private static final Logger log = LoggerFactory.getLogger(InterviewQuestionService.class);
    
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    
    // 问题类型权重分配（按优先级）
    // MySQL + Redis >= Java > Spring + Spring Boot
    // 假设总题目数为10：项目2题，MySQL 2题，Redis 2题，Java基础1题，集合1题，并发1题，Spring/Boot 1题
    private static final double PROJECT_RATIO = 0.20;      // 20% 项目经历
    private static final double MYSQL_RATIO = 0.20;        // 20% MySQL
    private static final double REDIS_RATIO = 0.20;        // 20% Redis
    private static final double JAVA_BASIC_RATIO = 0.10;   // 10% Java基础
    private static final double JAVA_COLLECTION_RATIO = 0.10; // 10% 集合
    private static final double JAVA_CONCURRENT_RATIO = 0.10; // 10% 并发
    private static final double SPRING_RATIO = 0.10;       // 10% Spring/SpringBoot
    
    private static final String SYSTEM_PROMPT = """
        你是一位资深的Java后端技术面试官，拥有10年以上的面试经验。
        你需要根据候选人的简历内容，生成针对性的面试问题。
        
        问题生成要求：
        1. 项目经历问题：深入候选人的项目经验，考察真实性和深度
        2. MySQL问题：考察索引、事务、锁、优化、主从复制等
        3. Redis问题：考察数据结构、持久化、集群、缓存策略等
        4. Java基础：考察面向对象、异常处理、IO、反射等
        5. Java集合：考察List、Map、Set实现原理和使用场景
        6. Java并发：考察线程、锁、线程池、并发工具类等
        7. Spring/SpringBoot：考察IoC、AOP、事务、自动配置等
        
        问题难度应该循序渐进，从基础到深入。
        每个问题应该简洁明确，便于候选人理解。
        
        请严格按照以下JSON格式输出，不要有任何额外文字：
        {
            "questions": [
                {
                    "question": "问题内容",
                    "type": "问题类型(PROJECT/JAVA_BASIC/JAVA_COLLECTION/JAVA_CONCURRENT/MYSQL/REDIS/SPRING/SPRING_BOOT)",
                    "category": "问题类别中文名称"
                }
            ]
        }
        """;
    
    public InterviewQuestionService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * 生成面试问题
     * 
     * @param resumeText 简历文本
     * @param questionCount 问题数量
     * @return 面试问题列表
     */
    public List<InterviewQuestionDTO> generateQuestions(String resumeText, int questionCount) {
        log.info("开始生成面试问题，简历长度: {}, 问题数量: {}", resumeText.length(), questionCount);
        
        // 计算各类型问题数量
        QuestionDistribution distribution = calculateDistribution(questionCount);
        
        String userPrompt = """
            请根据以下简历内容，生成%d个面试问题。
            
            问题分布要求：
            - 项目经历相关: %d题（基于简历中的具体项目提问）
            - MySQL: %d题
            - Redis: %d题
            - Java基础: %d题
            - Java集合: %d题
            - Java并发: %d题
            - Spring/SpringBoot: %d题
            
            ---简历内容---
            %s
            ---简历结束---
            
            请生成问题列表，严格按照JSON格式输出。
            """.formatted(
                questionCount,
                distribution.project,
                distribution.mysql,
                distribution.redis,
                distribution.javaBasic,
                distribution.javaCollection,
                distribution.javaConcurrent,
                distribution.spring,
                resumeText
            );
        
        try {
            SystemMessage systemMessage = new SystemMessage(SYSTEM_PROMPT);
            UserMessage userMessage = new UserMessage(userPrompt);
            Prompt prompt = new Prompt(List.of(systemMessage, userMessage));
            
            String response = chatClient.prompt(prompt)
                    .call()
                    .content();
            
            log.debug("LLM响应: {}", response);
            
            List<InterviewQuestionDTO> questions = parseQuestions(response);
            log.info("成功生成 {} 个面试问题", questions.size());
            
            return questions;
            
        } catch (Exception e) {
            log.error("生成面试问题失败: {}", e.getMessage(), e);
            // 返回默认问题集
            return generateDefaultQuestions(questionCount);
        }
    }
    
    /**
     * 计算各类型问题分布
     */
    private QuestionDistribution calculateDistribution(int total) {
        int project = Math.max(1, (int) Math.round(total * PROJECT_RATIO));
        int mysql = Math.max(1, (int) Math.round(total * MYSQL_RATIO));
        int redis = Math.max(1, (int) Math.round(total * REDIS_RATIO));
        int javaBasic = Math.max(1, (int) Math.round(total * JAVA_BASIC_RATIO));
        int javaCollection = (int) Math.round(total * JAVA_COLLECTION_RATIO);
        int javaConcurrent = (int) Math.round(total * JAVA_CONCURRENT_RATIO);
        int spring = total - project - mysql - redis - javaBasic - javaCollection - javaConcurrent;
        
        // 确保至少有1个
        spring = Math.max(0, spring);
        
        return new QuestionDistribution(project, mysql, redis, javaBasic, javaCollection, javaConcurrent, spring);
    }
    
    private record QuestionDistribution(
        int project, int mysql, int redis, 
        int javaBasic, int javaCollection, int javaConcurrent, int spring
    ) {}
    
    /**
     * 解析LLM响应
     */
    private List<InterviewQuestionDTO> parseQuestions(String response) {
        List<InterviewQuestionDTO> questions = new ArrayList<>();
        
        try {
            String jsonStr = extractJson(response);
            JsonNode root = objectMapper.readTree(jsonStr);
            JsonNode questionsNode = root.get("questions");
            
            if (questionsNode != null && questionsNode.isArray()) {
                int index = 0;
                for (JsonNode qNode : questionsNode) {
                    String question = qNode.get("question").asText();
                    String typeStr = qNode.get("type").asText();
                    String category = qNode.get("category").asText();
                    
                    QuestionType type = parseQuestionType(typeStr);
                    questions.add(InterviewQuestionDTO.create(index++, question, type, category));
                }
            }
        } catch (Exception e) {
            log.error("解析问题列表失败: {}", e.getMessage());
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
    
    private String extractJson(String response) {
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start != -1 && end != -1 && end > start) {
            return response.substring(start, end + 1);
        }
        return response;
    }
    
    /**
     * 生成默认问题（备用）
     */
    private List<InterviewQuestionDTO> generateDefaultQuestions(int count) {
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
        
        for (int i = 0; i < Math.min(count, defaultQuestions.length); i++) {
            questions.add(InterviewQuestionDTO.create(
                i,
                defaultQuestions[i][0],
                QuestionType.valueOf(defaultQuestions[i][1]),
                defaultQuestions[i][2]
            ));
        }
        
        return questions;
    }
}
