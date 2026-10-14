package interview.guide.modules.knowledgebase.eval;

import java.util.List;

/**
 * RAG 检索评估报告。
 *
 * <p>指标口径：
 * <ul>
 *   <li><b>命中率 hitRate</b> = 期望片段出现在最终 context 里的用例数 / 总用例数</li>
 *   <li><b>MRR</b> = 首个命中片段的排名倒数均值（未命中记 0）；越接近 1 说明正确片段排得越靠前</li>
 * </ul>
 * 报告完全由观测数据决定，不调用模型做裁判。
 */
public record RetrievalEvalReport(
    int topK,
    double minScore,
    List<CaseResult> caseResults
) {

    /**
     * 单个用例的评估结果。
     *
     * @param name                  用例名
     * @param observed              是否录到观测数据（false 表示尚未录制，不能算作命中）
     * @param hit                   最终 context 中是否包含期望片段
     * @param hitRank               首个命中片段的 1-based 排名，未命中为 0
     * @param kept                  通过分数阈值并进入 topK 的片段数
     * @param droppedBelowThreshold 因低于分数阈值被丢弃的片段数（用于判断阈值是否过紧）
     */
    public record CaseResult(
        String name,
        boolean observed,
        boolean hit,
        int hitRank,
        int kept,
        int droppedBelowThreshold
    ) {
    }

    public RetrievalEvalReport {
        caseResults = List.copyOf(caseResults);
    }

    public int total() {
        return caseResults.size();
    }

    public int hits() {
        return (int) caseResults.stream().filter(CaseResult::hit).count();
    }

    public int observedCount() {
        return (int) caseResults.stream().filter(CaseResult::observed).count();
    }

    /** 命中率；没有观测数据时返回 0，避免除零。 */
    public double hitRate() {
        int observed = observedCount();
        return observed == 0 ? 0.0 : (double) hits() / observed;
    }

    /** MRR（在已观测的用例上计算）。 */
    public double mrr() {
        int observed = observedCount();
        if (observed == 0) {
            return 0.0;
        }
        double sum = 0.0;
        for (CaseResult r : caseResults) {
            if (r.observed() && r.hitRank() > 0) {
                sum += 1.0 / r.hitRank();
            }
        }
        return sum / observed;
    }

    public List<String> missed() {
        return caseResults.stream().filter(r -> !r.hit()).map(CaseResult::name).toList();
    }

    /** 供 CI 日志与人工查阅的纯文本报告。 */
    public String toMarkdown() {
        StringBuilder sb = new StringBuilder();
        sb.append("# RAG 检索评估报告\n\n");
        sb.append(String.format("配置：topK=%d，minScore=%.2f%n%n", topK, minScore));
        sb.append(String.format("已观测 %d / %d 条用例；命中 %d 条，命中率 %.1f%%，MRR %.3f%n%n",
            observedCount(), total(), hits(), hitRate() * 100, mrr()));
        sb.append("| 用例 | 命中 | 排名 | 入选片段 | 低于阈值被丢弃 |\n");
        sb.append("|---|---|---|---|---|\n");
        for (CaseResult r : caseResults) {
            sb.append(String.format("| %s | %s | %s | %d | %d |%n",
                r.name(),
                r.observed() ? (r.hit() ? "是" : "否") : "未录制",
                r.hitRank() > 0 ? String.valueOf(r.hitRank()) : "-",
                r.kept(),
                r.droppedBelowThreshold()));
        }
        if (!missed().isEmpty()) {
            sb.append("\n未命中：").append(String.join("、", missed())).append('\n');
        }
        return sb.toString();
    }
}
