package ai.interview.modules.knowledgebase.eval;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RetrievalEvaluator 单元测试：指标口径与筛选语义。
 *
 * <p>这些断言同时是「评估器口径」的说明书——改动 {@link RetrievalEvaluator} 时如果口径变了，
 * 这里必须先红，否则历史基线与新结果不可比。
 */
@DisplayName("RetrievalEvaluator - RAG 检索评估")
class RetrievalEvaluatorTest {

    private static RetrievedChunk chunk(String text, double score) {
        return new RetrievedChunk("doc.md", text, score);
    }

    private static RetrievalEvalCase evalCase(String name, String... markers) {
        return new RetrievalEvalCase(name, "问题：" + name, "doc.md", List.of(markers));
    }

    @Nested
    @DisplayName("select - 复刻生产筛选语义")
    class Select {

        @Test
        @DisplayName("丢弃低于阈值的片段")
        void dropsBelowThreshold() {
            List<RetrievedChunk> kept = RetrievalEvaluator.select(
                List.of(chunk("a", 0.9), chunk("b", 0.2), chunk("c", 0.3)), 10, 0.28);
            assertEquals(2, kept.size());
            assertTrue(kept.stream().allMatch(c -> c.score() >= 0.28));
        }

        @Test
        @DisplayName("按分数降序排列并截断到 topK")
        void sortsAndLimits() {
            List<RetrievedChunk> kept = RetrievalEvaluator.select(
                List.of(chunk("low", 0.30), chunk("high", 0.91), chunk("mid", 0.55)), 2, 0.28);
            assertEquals(2, kept.size());
            assertEquals("high", kept.get(0).text());
            assertEquals("mid", kept.get(1).text());
        }

        @Test
        @DisplayName("topK 小于 1 时按 1 处理（与生产侧 Math.max 一致）")
        void topKFloorAtOne() {
            assertEquals(1, RetrievalEvaluator.select(List.of(chunk("a", 0.9)), 0, 0.1).size());
            assertEquals(1, RetrievalEvaluator.select(List.of(chunk("a", 0.9)), -5, 0.1).size());
        }

        @Test
        @DisplayName("空候选返回空列表")
        void emptyCandidates() {
            assertTrue(RetrievalEvaluator.select(List.of(), 8, 0.28).isEmpty());
            assertTrue(RetrievalEvaluator.select(null, 8, 0.28).isEmpty());
        }
    }

    @Nested
    @DisplayName("evaluate - 指标计算")
    class Evaluate {

        @Test
        @DisplayName("首位命中：命中率为 1，MRR 为 1")
        void hitAtFirstRank() {
            RetrievalEvalReport report = RetrievalEvaluator.evaluate(
                List.of(evalCase("c1", "目标片段")),
                Map.of("c1", List.of(chunk("这里就是目标片段的内容", 0.80))),
                8, 0.28);
            assertEquals(1.0, report.hitRate(), 1e-9);
            assertEquals(1.0, report.mrr(), 1e-9);
            assertEquals(1, report.caseResults().get(0).hitRank());
        }

        @Test
        @DisplayName("第三位命中：MRR 为 1/3")
        void hitAtThirdRank() {
            RetrievalEvalReport report = RetrievalEvaluator.evaluate(
                List.of(evalCase("c1", "目标片段")),
                Map.of("c1", List.of(
                    chunk("无关一", 0.90), chunk("无关二", 0.85), chunk("目标片段在此", 0.80))),
                8, 0.28);
            assertEquals(1.0, report.hitRate(), 1e-9);
            assertEquals(1.0 / 3, report.mrr(), 1e-9);
            assertEquals(3, report.caseResults().get(0).hitRank());
        }

        @Test
        @DisplayName("未命中：记入 missed，命中率与 MRR 均为 0")
        void miss() {
            RetrievalEvalReport report = RetrievalEvaluator.evaluate(
                List.of(evalCase("c1", "目标片段")),
                Map.of("c1", List.of(chunk("完全无关", 0.90))),
                8, 0.28);
            assertEquals(0.0, report.hitRate(), 1e-9);
            assertEquals(0.0, report.mrr(), 1e-9);
            assertEquals(List.of("c1"), report.missed());
            assertFalse(report.caseResults().get(0).hit());
        }

        @Test
        @DisplayName("阈值过紧把正确片段过滤掉：算未命中，并暴露被丢弃条数")
        void thresholdTooStrict() {
            RetrievalEvalReport report = RetrievalEvaluator.evaluate(
                List.of(evalCase("c1", "目标片段")),
                Map.of("c1", List.of(chunk("目标片段", 0.20))),
                8, 0.28);
            assertFalse(report.caseResults().get(0).hit());
            assertEquals(0, report.caseResults().get(0).kept());
            assertEquals(1, report.caseResults().get(0).droppedBelowThreshold());
        }

        @Test
        @DisplayName("阈值放宽后同一条观测转为命中——这是参数实验的基础")
        void looserThresholdFlipsToHit() {
            Map<String, List<RetrievedChunk>> obs = Map.of("c1", List.of(chunk("目标片段", 0.20)));
            List<RetrievalEvalCase> cases = List.of(evalCase("c1", "目标片段"));

            assertFalse(RetrievalEvaluator.evaluate(cases, obs, 8, 0.28).caseResults().get(0).hit());
            assertTrue(RetrievalEvaluator.evaluate(cases, obs, 8, 0.18).caseResults().get(0).hit());
        }

        @Test
        @DisplayName("topK 截断把正确片段挤出：算未命中")
        void topKTruncation() {
            RetrievalEvalReport report = RetrievalEvaluator.evaluate(
                List.of(evalCase("c1", "目标片段")),
                Map.of("c1", List.of(
                    chunk("无关一", 0.90), chunk("目标片段", 0.80))),
                1, 0.28);
            assertFalse(report.caseResults().get(0).hit());
        }

        @Test
        @DisplayName("未录制观测的用例不计入命中率分母")
        void unobservedExcludedFromDenominator() {
            RetrievalEvalReport report = RetrievalEvaluator.evaluate(
                List.of(evalCase("recorded", "目标"), evalCase("pending", "目标")),
                Map.of("recorded", List.of(chunk("目标", 0.90))),
                8, 0.28);
            assertEquals(2, report.total());
            assertEquals(1, report.observedCount());
            assertEquals(1.0, report.hitRate(), 1e-9);
            assertEquals(1, report.hits());
        }

        @Test
        @DisplayName("标记匹配忽略大小写并容忍首尾空白")
        void markerMatchIsCaseInsensitive() {
            RetrievalEvalReport report = RetrievalEvaluator.evaluate(
                List.of(new RetrievalEvalCase("c1", "问题", "doc.md", List.of("  MVCC 原理  "))),
                Map.of("c1", List.of(chunk("...mvcc 原理：通过 undo log 实现...", 0.70))),
                8, 0.28);
            assertTrue(report.caseResults().get(0).hit());
        }

        @Test
        @DisplayName("多个标记任一命中即算命中")
        void anyMarkerHits() {
            RetrievalEvalReport report = RetrievalEvaluator.evaluate(
                List.of(evalCase("c1", "标记甲", "标记乙")),
                Map.of("c1", List.of(chunk("正文里只有标记乙", 0.70))),
                8, 0.28);
            assertTrue(report.caseResults().get(0).hit());
        }

        @Test
        @DisplayName("MRR 在多条用例上取平均")
        void mrrAveragedOverCases() {
            RetrievalEvalReport report = RetrievalEvaluator.evaluate(
                List.of(evalCase("first", "目标"), evalCase("third", "目标"), evalCase("none", "目标")),
                Map.of(
                    "first", List.of(chunk("目标", 0.9)),
                    "third", List.of(chunk("a", 0.9), chunk("b", 0.8), chunk("目标", 0.7)),
                    "none", List.of(chunk("x", 0.9))),
                8, 0.1);
            // (1 + 1/3 + 0) / 3
            assertEquals((1.0 + 1.0 / 3) / 3, report.mrr(), 1e-9);
            assertEquals(2.0 / 3, report.hitRate(), 1e-9);
        }
    }

    @Test
    @DisplayName("报告渲染为文本时包含关键指标")
    void markdownContainsMetrics() {
        RetrievalEvalReport report = RetrievalEvaluator.evaluate(
            List.of(evalCase("c1", "目标")),
            Map.of("c1", List.of(chunk("目标", 0.90))),
            8, 0.28);
        String md = report.toMarkdown();
        assertTrue(md.contains("命中率"));
        assertTrue(md.contains("MRR"));
        assertTrue(md.contains("c1"));
    }
}
