package interview.guide.modules.interview.service;

import interview.guide.common.ai.StructuredOutputInvoker;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.model.InterviewTemplateConfig;
import interview.guide.modules.interview.model.InterviewQuestionDTO;
import interview.guide.modules.interview.model.InterviewQuestionDTO.QuestionType;
import interview.guide.modules.interview.model.QuestionGenerationResult;
import interview.guide.modules.userai.service.UserAiChatClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 面试问题生成服务
 * 基于简历内容生成针对性的面试问题
 */
@Service
public class InterviewQuestionService {
    
    private static final Logger log = LoggerFactory.getLogger(InterviewQuestionService.class);
    
    private final UserAiChatClientFactory chatClientFactory;
    private final PromptTemplate systemPromptTemplate;
    private final PromptTemplate userPromptTemplate;
    private final PromptTemplate followUpSystemPromptTemplate;
    private final BeanOutputConverter<QuestionListDTO> outputConverter;
    private final BeanOutputConverter<FollowUpDecisionDTO> followUpOutputConverter;
    private final StructuredOutputInvoker structuredOutputInvoker;
    private final int followUpCount;
    
    private static final int MAX_FOLLOW_UP_COUNT = 2;
    /** 历史题相似度阈值：归一化后包含或字符重叠率超过该值视为重复 */
    private static final double HISTORY_OVERLAP_THRESHOLD = 0.72;
    /** 归一化时去掉空白与常见中英文标点 */
    private static final Pattern NON_WORD = Pattern.compile("[\\s\\p{Punct}\\u3000-\\u303F\\uFF00-\\uFFEF]+");

    /**
     * JD 关键词 → 题型加成。命中时对应题型权重按倍率放大。
     */
    private static final Map<QuestionType, List<String>> JD_TYPE_KEYWORDS = Map.ofEntries(
        Map.entry(QuestionType.MYSQL, List.of("mysql", "sql", "数据库", "索引", "事务", "innodb")),
        Map.entry(QuestionType.REDIS, List.of("redis", "缓存", "分布式锁", "缓存一致性")),
        Map.entry(QuestionType.JAVA_BASIC, List.of("java", "jvm", "gc", "垃圾回收", "面向对象")),
        Map.entry(QuestionType.JAVA_COLLECTION, List.of("集合", "hashmap", "concurrenthashmap", "list", "map")),
        Map.entry(QuestionType.JAVA_CONCURRENT, List.of("并发", "多线程", "线程池", "锁", "synchronized", "juc")),
        Map.entry(QuestionType.SPRING, List.of("spring", "ioc", "aop", "bean", "依赖注入")),
        Map.entry(QuestionType.SPRING_BOOT, List.of("spring boot", "springboot", "starter", "自动配置")),
        Map.entry(QuestionType.FRONTEND, List.of("前端", "react", "vue", "javascript", "typescript", "css")),
        Map.entry(QuestionType.DISTRIBUTED_SYSTEM, List.of("分布式", "微服务", "rpc", "消息队列", "kafka", "mq")),
        Map.entry(QuestionType.ARCHITECTURE, List.of("架构", "高可用", "高并发", "系统设计", "限流", "降级")),
        Map.entry(QuestionType.PROJECT, List.of("项目", "业务", "落地", "负责", "架构设计")),
        Map.entry(QuestionType.SOFT_SKILLS, List.of("沟通", "协作", "领导力", "软技能", "团队"))
    );

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
            UserAiChatClientFactory chatClientFactory,
            StructuredOutputInvoker structuredOutputInvoker,
            @Value("classpath:prompts/interview-question-system.st") Resource systemPromptResource,
            @Value("classpath:prompts/interview-question-user.st") Resource userPromptResource,
            @Value("classpath:prompts/interview-followup-system.st") Resource followUpSystemPromptResource,
            @Value("${app.interview.follow-up-count:1}") int followUpCount) throws IOException {
        this.chatClientFactory = chatClientFactory;
        this.structuredOutputInvoker = structuredOutputInvoker;
        this.systemPromptTemplate = new PromptTemplate(systemPromptResource.getContentAsString(StandardCharsets.UTF_8));
        this.userPromptTemplate = new PromptTemplate(userPromptResource.getContentAsString(StandardCharsets.UTF_8));
        this.followUpSystemPromptTemplate = new PromptTemplate(followUpSystemPromptResource.getContentAsString(StandardCharsets.UTF_8));
        this.outputConverter = new BeanOutputConverter<>(QuestionListDTO.class);
        this.followUpOutputConverter = new BeanOutputConverter<>(FollowUpDecisionDTO.class);
        this.followUpCount = Math.max(0, Math.min(followUpCount, MAX_FOLLOW_UP_COUNT));
    }
    
    /**
     * 生成面试问题（兼容旧调用，仅返回题目列表）
     */
    public List<InterviewQuestionDTO> generateQuestions(String resumeText, int questionCount, List<String> historicalQuestions) {
        return generateQuestionsWithSource(resumeText, questionCount, historicalQuestions,
            InterviewTemplateConfig.defaultBackend(followUpCount), null).questions();
    }

    public List<InterviewQuestionDTO> generateQuestions(
            String resumeText,
            int questionCount,
            List<String> historicalQuestions,
            InterviewTemplateConfig template) {
        return generateQuestionsWithSource(resumeText, questionCount, historicalQuestions, template, null).questions();
    }

    public List<InterviewQuestionDTO> generateQuestions(
            String resumeText,
            int questionCount,
            List<String> historicalQuestions,
            InterviewTemplateConfig template,
            String targetJob) {
        return generateQuestionsWithSource(resumeText, questionCount, historicalQuestions, template, targetJob).questions();
    }

    /**
     * 生成面试问题并标记来源（AI / DEFAULT）。
     * AI 失败时降级默认题库，保证本地可用。
     */
    public QuestionGenerationResult generateQuestionsWithSource(
            String resumeText,
            int questionCount,
            List<String> historicalQuestions,
            InterviewTemplateConfig template,
            String targetJob) {
        log.info("开始生成面试问题，简历长度: {}, 问题数量: {}, 历史问题数: {}",
            resumeText.length(), questionCount, historicalQuestions != null ? historicalQuestions.size() : 0);

        InterviewTemplateConfig normalizedTemplate = template.normalize(followUpCount);
        // 有 JD 时按关键词放大相关题型权重
        InterviewTemplateConfig weightedTemplate = boostTemplateByJobDescription(normalizedTemplate, targetJob);
        TemplateDistribution distribution = calculateTemplateDistribution(questionCount, weightedTemplate);

        try {
            String systemPrompt = systemPromptTemplate.render();
            Map<String, Object> variables = new HashMap<>();
            variables.put("questionCount", questionCount);
            variables.put("questionPlan", distribution.questionPlan());
            variables.put("difficultyDistribution", difficultyText(weightedTemplate.difficultyDistribution()));
            variables.put("followUpCount", weightedTemplate.followUpCount());
            variables.put("resumeText", resumeText);
            variables.put("targetJob", targetJob == null || targetJob.isBlank()
                ? "未指定岗位目标；请基于简历进行综合面试。" : targetJob);

            if (historicalQuestions != null && !historicalQuestions.isEmpty()) {
                variables.put("historicalQuestions", String.join("\n", historicalQuestions));
            } else {
                variables.put("historicalQuestions", "暂无历史提问");
            }

            String userPrompt = userPromptTemplate.render(variables);
            String systemPromptWithFormat = systemPrompt + "\n\n" + outputConverter.getFormat();

            QuestionListDTO dto = structuredOutputInvoker.invoke(
                chatClientFactory.forCurrentUser(),
                chatClientFactory.fallbackForCurrentUser(),
                systemPromptWithFormat,
                userPrompt,
                outputConverter,
                ErrorCode.INTERVIEW_QUESTION_GENERATION_FAILED,
                "面试问题生成失败：",
                "结构化问题生成",
                log
            );

            List<InterviewQuestionDTO> questions = convertToQuestions(dto, weightedTemplate.followUpCount());
            if (questions.isEmpty()) {
                log.warn("AI 返回空题列表，降级为默认题库");
                return QuestionGenerationResult.defaults(
                    generateDefaultQuestions(questionCount, weightedTemplate.followUpCount(), historicalQuestions));
            }

            questions = dedupeAgainstHistory(questions, historicalQuestions, questionCount, weightedTemplate.followUpCount());
            log.info("成功生成 {} 个面试问题（AI）", questions.size());
            return QuestionGenerationResult.ai(questions);

        } catch (Exception e) {
            log.error("生成面试问题失败，降级为默认题库: {}", e.getMessage(), e);
            return QuestionGenerationResult.defaults(
                generateDefaultQuestions(questionCount, weightedTemplate.followUpCount(), historicalQuestions));
        }
    }

    /**
     * 生成面试问题（不带历史问题）
     */
    public List<InterviewQuestionDTO> generateQuestions(String resumeText, int questionCount) {
        return generateQuestions(resumeText, questionCount, null);
    }

    /**
     * 根据 JD 文本提升相关题型权重（未命中则保持原样）。
     */
    InterviewTemplateConfig boostTemplateByJobDescription(InterviewTemplateConfig template, String targetJob) {
        if (targetJob == null || targetJob.isBlank()) {
            return template;
        }
        String jd = targetJob.toLowerCase(Locale.ROOT);
        List<InterviewTemplateConfig.QuestionTypeWeight> boosted = new ArrayList<>();
        boolean anyBoost = false;
        for (InterviewTemplateConfig.QuestionTypeWeight item : template.questionTypes()) {
            List<String> keywords = JD_TYPE_KEYWORDS.getOrDefault(item.type(), List.of());
            boolean hit = keywords.stream().anyMatch(jd::contains);
            if (hit) {
                // 命中关键词：权重 ×1.8 并至少 +8，使分配更倾斜
                int newWeight = Math.max(item.weight() + 8, (int) Math.round(item.weight() * 1.8));
                boosted.add(new InterviewTemplateConfig.QuestionTypeWeight(item.type(), newWeight));
                anyBoost = true;
            } else {
                boosted.add(item);
            }
        }
        if (!anyBoost) {
            return template;
        }
        log.info("已按 JD 关键词调整题型权重");
        return new InterviewTemplateConfig(
            template.id(),
            template.name(),
            boosted,
            template.difficultyDistribution(),
            template.followUpCount()
        );
    }

    /**
     * 过滤与历史题高度相似的新题，不足数量时用默认题补齐。
     */
    List<InterviewQuestionDTO> dedupeAgainstHistory(
        List<InterviewQuestionDTO> generated,
        List<String> historicalQuestions,
        int expectedCount,
        int followUpLimit
    ) {
        if (historicalQuestions == null || historicalQuestions.isEmpty()) {
            return reindex(generated.stream().limit(expectedCount).toList());
        }
        List<String> historyNorm = historicalQuestions.stream()
            .map(this::normalizeQuestionText)
            .filter(s -> !s.isBlank())
            .toList();

        List<InterviewQuestionDTO> kept = new ArrayList<>();
        Set<String> usedNorm = new HashSet<>();
        for (InterviewQuestionDTO q : generated) {
            if (q == null || q.question() == null || q.question().isBlank()) {
                continue;
            }
            String norm = normalizeQuestionText(q.question());
            if (usedNorm.contains(norm) || isSimilarToAny(norm, historyNorm)) {
                log.debug("过滤与历史重复的题目: {}", q.question());
                continue;
            }
            kept.add(q);
            usedNorm.add(norm);
            if (kept.size() >= expectedCount) {
                break;
            }
        }

        if (kept.size() < expectedCount) {
            for (InterviewQuestionDTO fallback : generateDefaultQuestions(expectedCount * 2, followUpLimit, historicalQuestions)) {
                String norm = normalizeQuestionText(fallback.question());
                if (usedNorm.contains(norm) || isSimilarToAny(norm, historyNorm)) {
                    continue;
                }
                kept.add(fallback);
                usedNorm.add(norm);
                if (kept.size() >= expectedCount) {
                    break;
                }
            }
        }
        return reindex(kept.stream().limit(expectedCount).toList());
    }

    private boolean isSimilarToAny(String normalized, List<String> historyNorm) {
        for (String history : historyNorm) {
            if (history.isBlank() || normalized.isBlank()) {
                continue;
            }
            if (history.contains(normalized) || normalized.contains(history)) {
                return true;
            }
            if (charOverlapRatio(normalized, history) >= HISTORY_OVERLAP_THRESHOLD) {
                return true;
            }
        }
        return false;
    }

    /** 基于字符集合 Jaccard 的粗粒度重叠率 */
    private double charOverlapRatio(String a, String b) {
        Set<Character> setA = new HashSet<>();
        Set<Character> setB = new HashSet<>();
        for (char c : a.toCharArray()) {
            setA.add(c);
        }
        for (char c : b.toCharArray()) {
            setB.add(c);
        }
        if (setA.isEmpty() || setB.isEmpty()) {
            return 0;
        }
        int intersection = 0;
        for (Character c : setA) {
            if (setB.contains(c)) {
                intersection++;
            }
        }
        int union = setA.size() + setB.size() - intersection;
        return union == 0 ? 0 : (double) intersection / union;
    }

    private String normalizeQuestionText(String text) {
        if (text == null) {
            return "";
        }
        return NON_WORD.matcher(text.toLowerCase(Locale.ROOT)).replaceAll("");
    }

    private List<InterviewQuestionDTO> reindex(List<InterviewQuestionDTO> questions) {
        List<InterviewQuestionDTO> result = new ArrayList<>();
        int index = 0;
        for (InterviewQuestionDTO q : questions) {
            result.add(InterviewQuestionDTO.create(
                index++,
                q.question(),
                q.type(),
                q.category(),
                q.isFollowUp(),
                q.parentQuestionIndex()
            ));
        }
        return result;
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
     * 生成默认问题（备用），尽量避开历史题。
     */
    private List<InterviewQuestionDTO> generateDefaultQuestions(
        int count,
        int followUpLimit,
        List<String> historicalQuestions
    ) {
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

        List<String> historyNorm = historicalQuestions == null
            ? List.of()
            : historicalQuestions.stream().map(this::normalizeQuestionText).filter(s -> !s.isBlank()).toList();

        List<InterviewQuestionDTO> preferred = new ArrayList<>();
        List<InterviewQuestionDTO> fallback = new ArrayList<>();
        for (String[] row : defaultQuestions) {
            InterviewQuestionDTO q = InterviewQuestionDTO.create(
                0, row[0], QuestionType.valueOf(row[1]), row[2], false, null);
            String norm = normalizeQuestionText(q.question());
            if (isSimilarToAny(norm, historyNorm)) {
                fallback.add(q);
            } else {
                preferred.add(q);
            }
        }

        List<InterviewQuestionDTO> selected = new ArrayList<>();
        selected.addAll(preferred);
        for (InterviewQuestionDTO q : fallback) {
            if (selected.size() >= count) {
                break;
            }
            selected.add(q);
        }
        // followUpLimit 预留参数，默认题不预生成追问
        return reindex(selected.stream().limit(count).toList());
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
                chatClientFactory.forCurrentUser(),
                chatClientFactory.fallbackForCurrentUser(),
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
