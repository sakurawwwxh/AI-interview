package interview.guide.modules.interview.service;

import interview.guide.modules.interview.model.InterviewAnswerEntity;
import interview.guide.modules.interview.model.InterviewQuestionDTO;
import interview.guide.modules.interview.model.InterviewSessionEntity;
import interview.guide.modules.interview.model.InterviewStatisticsDTO;
import interview.guide.modules.interview.repository.InterviewSessionRepository;
import interview.guide.modules.user.security.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 从已保存的面试报告聚合个人能力、趋势和薄弱项，不触发额外 AI 调用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewStatisticsService {

    /** 统计最多拉取最近 100 个会话，避免全表扫描 */
    private static final int MAX_STATISTICS_SESSIONS = 100;

    private static final Map<InterviewQuestionDTO.QuestionType, String> CATEGORY_LABELS = new EnumMap<>(InterviewQuestionDTO.QuestionType.class);

    static {
        CATEGORY_LABELS.put(InterviewQuestionDTO.QuestionType.PROJECT, "项目经历");
        CATEGORY_LABELS.put(InterviewQuestionDTO.QuestionType.JAVA_BASIC, "Java 基础");
        CATEGORY_LABELS.put(InterviewQuestionDTO.QuestionType.JAVA_COLLECTION, "Java 集合");
        CATEGORY_LABELS.put(InterviewQuestionDTO.QuestionType.JAVA_CONCURRENT, "Java 并发");
        CATEGORY_LABELS.put(InterviewQuestionDTO.QuestionType.MYSQL, "MySQL");
        CATEGORY_LABELS.put(InterviewQuestionDTO.QuestionType.REDIS, "Redis");
        CATEGORY_LABELS.put(InterviewQuestionDTO.QuestionType.SPRING, "Spring");
        CATEGORY_LABELS.put(InterviewQuestionDTO.QuestionType.SPRING_BOOT, "Spring Boot");
        CATEGORY_LABELS.put(InterviewQuestionDTO.QuestionType.FRONTEND, "前端");
        CATEGORY_LABELS.put(InterviewQuestionDTO.QuestionType.DISTRIBUTED_SYSTEM, "分布式系统");
        CATEGORY_LABELS.put(InterviewQuestionDTO.QuestionType.ARCHITECTURE, "架构设计");
        CATEGORY_LABELS.put(InterviewQuestionDTO.QuestionType.SOFT_SKILLS, "软技能");
    }

    private final InterviewSessionRepository sessionRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public InterviewStatisticsDTO getStatistics() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return new InterviewStatisticsDTO(0, 0, null, null, List.of(), List.of(), List.of());
        }
        List<Long> sessionIds = sessionRepository.findEvaluatedIdsByUserId(
            userId, PageRequest.of(0, MAX_STATISTICS_SESSIONS));
        if (sessionIds.isEmpty()) {
            return new InterviewStatisticsDTO(0, 0, null, null, List.of(), List.of(), List.of());
        }
        Map<Long, Integer> orderById = java.util.stream.IntStream.range(0, sessionIds.size())
            .boxed()
            .collect(Collectors.toMap(sessionIds::get, index -> index));
        List<InterviewSessionEntity> sessions = new ArrayList<>(
            sessionRepository.findEvaluatedWithAnswersByIdIn(sessionIds));
        sessions.sort(Comparator.comparing(session -> orderById.getOrDefault(session.getId(), Integer.MAX_VALUE)));
        if (sessions.isEmpty()) {
            return new InterviewStatisticsDTO(0, 0, null, null, List.of(), List.of(), List.of());
        }

        List<InterviewStatisticsDTO.TrendPoint> trend = sessions.stream()
            .map(session -> new InterviewStatisticsDTO.TrendPoint(
                session.getSessionId(),
                session.getCompletedAt() != null ? session.getCompletedAt() : session.getCreatedAt(),
                session.getOverallScore()
            ))
            .toList();

        Map<String, List<Integer>> categoryScores = new LinkedHashMap<>();
        for (InterviewSessionEntity session : sessions) {
            Map<Integer, String> categoryByQuestionIndex = readQuestionCategories(session);
            for (InterviewAnswerEntity answer : session.getAnswers()) {
                if (answer.getScore() == null) {
                    continue;
                }
                String category = categoryByQuestionIndex.get(answer.getQuestionIndex());
                if (category == null) {
                    category = fallbackCategory(answer.getCategory());
                }
                categoryScores.computeIfAbsent(category, ignored -> new ArrayList<>()).add(answer.getScore());
            }
        }

        List<InterviewStatisticsDTO.CategoryScore> abilityScores = categoryScores.entrySet().stream()
            .map(entry -> new InterviewStatisticsDTO.CategoryScore(
                entry.getKey(), average(entry.getValue()), entry.getValue().size()
            ))
            .sorted(Comparator.comparing(InterviewStatisticsDTO.CategoryScore::category))
            .toList();

        List<InterviewStatisticsDTO.Weakness> weaknesses = abilityScores.stream()
            .sorted(Comparator.comparingInt(InterviewStatisticsDTO.CategoryScore::averageScore)
                .thenComparing(InterviewStatisticsDTO.CategoryScore::category))
            .limit(3)
            .map(item -> new InterviewStatisticsDTO.Weakness(
                item.category(), item.averageScore(), item.questionCount()))
            .toList();

        int averageScore = average(sessions.stream()
            .map(InterviewSessionEntity::getOverallScore)
            .filter(Objects::nonNull)
            .toList());
        int latestScore = trend.getLast().score();
        // scoreChange = 最新一次 - 上一次（更符合"最近变化"直觉）
        Integer scoreChange = trend.size() < 2
            ? null
            : latestScore - trend.get(trend.size() - 2).score();

        return new InterviewStatisticsDTO(
            sessions.size(), averageScore, latestScore, scoreChange, abilityScores, trend, weaknesses
        );
    }

    private Map<Integer, String> readQuestionCategories(InterviewSessionEntity session) {
        if (session.getQuestionsJson() == null || session.getQuestionsJson().isBlank()) {
            return Map.of();
        }
        try {
            List<InterviewQuestionDTO> questions = objectMapper.readValue(
                session.getQuestionsJson(), new TypeReference<List<InterviewQuestionDTO>>() {
                }
            );
            Map<Integer, String> categories = new LinkedHashMap<>();
            for (InterviewQuestionDTO question : questions) {
                if (question.type() != null) {
                    categories.put(question.questionIndex(), CATEGORY_LABELS.get(question.type()));
                }
            }
            return categories;
        } catch (Exception e) {
            log.warn("解析统计面试题目失败: sessionId={}", session.getSessionId(), e);
            return Map.of();
        }
    }

    private String fallbackCategory(String category) {
        if (category == null || category.isBlank()) {
            return "其他";
        }
        int separatorIndex = category.indexOf('-');
        return separatorIndex > 0 ? category.substring(0, separatorIndex).trim() : category.trim();
    }

    private int average(List<Integer> values) {
        return (int) Math.round(values.stream().mapToInt(Integer::intValue).average().orElse(0));
    }
}
