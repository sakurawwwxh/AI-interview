package interview.guide.modules.interview.service;

import interview.guide.modules.interview.model.InterviewQuestionDTO;
import interview.guide.modules.interview.model.InterviewStatisticsDTO;
import interview.guide.modules.interview.repository.InterviewAnswerRepository;
import interview.guide.modules.interview.repository.InterviewAnswerRepository.AnswerScoreRow;
import interview.guide.modules.interview.repository.InterviewSessionRepository;
import interview.guide.modules.interview.repository.InterviewSessionRepository.StatisticsSessionRow;
import interview.guide.modules.user.security.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 从已保存的面试报告聚合个人能力、趋势和薄弱项，不触发额外 AI 调用。
 * <p>
 * 读路径刻意只取聚合需要的窄列（见 {@link StatisticsSessionRow} 与 {@link AnswerScoreRow}）：
 * 会话与答案实体上的大文本列一旦随实体取回，在这类远端部署（单次往返约 100ms）上会把
 * 一次统计请求从约 0.3 秒拖到十几秒。
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
    private final InterviewAnswerRepository answerRepository;

    @Transactional(readOnly = true)
    public InterviewStatisticsDTO getStatistics() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return emptyStatistics();
        }
        List<StatisticsSessionRow> recent = sessionRepository.findStatisticsSessions(userId, MAX_STATISTICS_SESSIONS);
        if (recent.isEmpty()) {
            return emptyStatistics();
        }
        // 查询按完成时间倒序取最近的场次，翻回时间正序后用于趋势展示
        List<StatisticsSessionRow> sessions = new ArrayList<>(recent);
        Collections.reverse(sessions);

        List<InterviewStatisticsDTO.TrendPoint> trend = sessions.stream()
            .map(session -> new InterviewStatisticsDTO.TrendPoint(
                session.getSessionId(),
                session.getCompletedAt() != null ? session.getCompletedAt() : session.getCreatedAt(),
                session.getOverallScore()
            ))
            .toList();

        List<Long> sessionIds = sessions.stream().map(StatisticsSessionRow::getId).toList();
        Map<Long, List<AnswerScoreRow>> answersBySession = answerRepository
            .findAnswerScoresBySessionIds(sessionIds).stream()
            .collect(Collectors.groupingBy(AnswerScoreRow::getSessionId));

        Map<String, List<Integer>> categoryScores = new LinkedHashMap<>();
        for (StatisticsSessionRow session : sessions) {
            Map<Integer, String> categoryByQuestionIndex = parseQuestionCategories(session);
            for (AnswerScoreRow answer : answersBySession.getOrDefault(session.getId(), List.of())) {
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
            .map(StatisticsSessionRow::getOverallScore)
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

    private InterviewStatisticsDTO emptyStatistics() {
        return new InterviewStatisticsDTO(0, 0, null, null, List.of(), List.of(), List.of());
    }

    /**
     * 把 {@code 0:PROJECT,1:JAVA_BASIC} 这类「题号:类型」串还原成「题号 → 中文分类」。
     * <p>
     * 题号为空串表示原 JSON 里该题没有 questionIndex（还原成 null 键，与按题号取值的行为一致）；
     * 类型为空串表示该题没有 type，此时不写入条目，随后会回退到答案自身的 category。
     */
    private Map<Integer, String> parseQuestionCategories(StatisticsSessionRow session) {
        String encoded = session.getQuestionTypes();
        if (encoded == null || encoded.isBlank()) {
            return Map.of();
        }
        Map<Integer, String> categories = new LinkedHashMap<>();
        try {
            for (String pair : encoded.split(",")) {
                int separator = pair.indexOf(':');
                String indexPart = separator < 0 ? pair : pair.substring(0, separator);
                String typePart = separator < 0 ? "" : pair.substring(separator + 1);
                if (typePart.isBlank()) {
                    continue;
                }
                categories.put(
                    indexPart.isBlank() ? null : Integer.valueOf(indexPart),
                    CATEGORY_LABELS.get(InterviewQuestionDTO.QuestionType.valueOf(typePart))
                );
            }
        } catch (RuntimeException e) {
            // 类型串解析不了（例如枚举值不在当前版本内）：整场退化成按答案自身 category 归类，
            // 与原先 JSON 反序列化失败后返回空映射的行为保持一致
            log.warn("解析统计题目类型失败: sessionId={}, questionTypes={}", session.getSessionId(), encoded, e);
            return Map.of();
        }
        return categories;
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
