package interview.guide.modules.knowledgebase.eval;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * RAG 检索指标回归：拿已录制的观测数据重算指标，与基线比较。
 *
 * <p>这条测试在 CI 中稳定运行——不访问数据库、不调用模型。它的作用是防止
 * 「改了筛选逻辑或分块策略，召回悄悄变差」这类回归。
 *
 * <p>参数与基线不一致时会直接失败：那说明这是一次参数实验，新旧指标不可比，
 * 需要显式重新录制基线，而不是让回归测试给出一个假结论。
 */
@DisplayName("RAG 检索指标回归")
class RetrievalGoldenRegressionTest {

    private static final double TOLERANCE = 1e-9;

    private static Path datasetDir;
    private static RetrievalEvalLoader.GoldenSet goldenSet;
    private static Map<String, List<RetrievedChunk>> observations;

    @BeforeAll
    static void setUp() throws IOException {
        datasetDir = RetrievalEvalLoader.resolveDefaultDir();
        assumeTrue(Files.isRegularFile(datasetDir.resolve("cases.json")),
            "未找到 RAG 评估数据集，跳过：" + datasetDir);
        goldenSet = RetrievalEvalLoader.loadCases(datasetDir);
        observations = RetrievalEvalLoader.loadObservations(datasetDir);
        assumeTrue(!observations.isEmpty(),
            "尚未录制观测数据（evals/retrieval-golden/observed/observations.json），跳过指标回归；"
                + "录制方法见 evals/retrieval-golden/README.md");
    }

    @Test
    @DisplayName("已观测用例的命中率与 MRR 不低于基线")
    void metricsNotBelowBaseline() throws IOException {
        RetrievalEvalReport report = RetrievalEvaluator.evaluate(
            goldenSet.cases(), observations, goldenSet.topK(), goldenSet.minScore());
        System.out.println(report.toMarkdown());

        Optional<RetrievalEvalLoader.Baseline> baseline = RetrievalEvalLoader.loadBaseline(datasetDir);
        assumeTrue(baseline.isPresent(), "未找到 baseline.json，本次仅打印报告不做比较");

        RetrievalEvalLoader.Baseline b = baseline.get();
        assertTrue(report.hitRate() + TOLERANCE >= b.hitRate(),
            String.format("命中率回退：本次 %.4f < 基线 %.4f（topK=%d, minScore=%.2f）",
                report.hitRate(), b.hitRate(), report.topK(), report.minScore()));
        assertTrue(report.mrr() + TOLERANCE >= b.mrr(),
            String.format("MRR 回退：本次 %.4f < 基线 %.4f", report.mrr(), b.mrr()));
    }

    @Test
    @DisplayName("参数与基线不一致时明确报错，避免拿不可比的数字下结论")
    void parametersMatchRecordedBaseline() throws IOException {
        Optional<RetrievalEvalLoader.Baseline> baseline = RetrievalEvalLoader.loadBaseline(datasetDir);
        if (baseline.isEmpty()) {
            return;
        }
        RetrievalEvalLoader.Baseline b = baseline.get();
        assertTrue(b.topK() == goldenSet.topK() && Math.abs(b.minScore() - goldenSet.minScore()) < 1e-9,
            String.format("cases.json 参数（topK=%d, minScore=%.2f）与基线（topK=%d, minScore=%.2f）不一致："
                    + "要么重新录制基线，要么明确这是一次参数实验并单独记录结论",
                goldenSet.topK(), goldenSet.minScore(), b.topK(), b.minScore()));
    }
}
