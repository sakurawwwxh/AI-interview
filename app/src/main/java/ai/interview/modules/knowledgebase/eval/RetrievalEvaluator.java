package ai.interview.modules.knowledgebase.eval;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * RAG 检索评估器：纯计算，不访问数据库、不调用模型，可在 CI 中稳定运行。
 *
 * <p>设计要点——<b>评估的是"给定召回候选，当前检索参数会选出什么"</b>：
 * 观测数据记录的是向量库返回的<b>完整候选列表</b>（未做截断），评估时再套用
 * {@code topK} 与 {@code minScore}。因此调整阈值或 topK 后重跑评估，指标会真实变化，
 * 可以用来回答「阈值 0.18 还是 0.28 更合适」「分块加不加 overlap」这类问题，
 * 而不是只能回归一段固定输出。
 *
 * <p>{@link #select} 复刻生产侧 {@code KnowledgeBaseQueryService} 的筛选语义
 * （分数阈值过滤 + topK 截断），两者口径必须一致，否则评估结论无法迁移到线上。
 */
public final class RetrievalEvaluator {

    private RetrievalEvaluator() {
    }

    /**
     * 复刻生产侧检索筛选：按分数降序，丢弃低于 {@code minScore} 的片段，再取前 {@code topK}。
     *
     * @param candidates 候选片段（顺序不限）
     * @param topK       保留条数，小于 1 时按 1 处理（与生产侧 Math.max(topK, 1) 一致）
     * @param minScore   相似度阈值
     */
    public static List<RetrievedChunk> select(List<RetrievedChunk> candidates, int topK, double minScore) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        return candidates.stream()
            .filter(c -> c.score() >= minScore)
            .sorted(Comparator.comparingDouble(RetrievedChunk::score).reversed())
            .limit(Math.max(topK, 1))
            .toList();
    }

    /**
     * 评估一组用例。
     *
     * @param cases        评估用例
     * @param observations 用例名 -&gt; 候选片段；缺失表示尚未录制
     * @param topK         本次评估使用的 topK
     * @param minScore     本次评估使用的相似度阈值
     */
    public static RetrievalEvalReport evaluate(List<RetrievalEvalCase> cases,
                                               Map<String, List<RetrievedChunk>> observations,
                                               int topK,
                                               double minScore) {
        List<RetrievalEvalReport.CaseResult> results = new ArrayList<>();
        for (RetrievalEvalCase evalCase : cases) {
            List<RetrievedChunk> candidates = observations == null ? null : observations.get(evalCase.name());
            boolean observed = candidates != null;
            List<RetrievedChunk> safe = observed ? candidates : List.of();

            List<RetrievedChunk> kept = select(safe, topK, minScore);
            int droppedBelowThreshold = (int) safe.stream().filter(c -> c.score() < minScore).count();

            int hitRank = 0;
            for (int i = 0; i < kept.size(); i++) {
                if (evalCase.matches(kept.get(i).text())) {
                    hitRank = i + 1;
                    break;
                }
            }
            results.add(new RetrievalEvalReport.CaseResult(
                evalCase.name(), observed, hitRank > 0, hitRank, kept.size(), droppedBelowThreshold));
        }
        return new RetrievalEvalReport(topK, minScore, results);
    }
}
