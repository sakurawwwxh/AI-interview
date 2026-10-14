package interview.guide.modules.knowledgebase.eval;

import interview.guide.modules.knowledgebase.model.KnowledgeBaseEntity;
import interview.guide.modules.knowledgebase.repository.KnowledgeBaseRepository;
import interview.guide.modules.knowledgebase.service.KnowledgeBaseVectorService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 录制真实检索的观测数据（需要真实环境，默认不跑）。
 *
 * <p>运行前提：应用所需中间件（PostgreSQL/pgvector、Redis 等）可用，模型 Key 已配置，
 * 且目标知识库中已经导入了 {@code cases.json} 里 corpus 列出的文档。
 *
 * <pre>
 * # 显式开启录制（EVAL_KB_IDS 可省略，省略时对全部知识库检索）
 * mvn -pl app test -Dtest=RetrievalEvalRecordingTest -Deval.record=true
 * EVAL_KB_IDS=1,2 mvn -pl app test -Dtest=RetrievalEvalRecordingTest -Deval.record=true
 * </pre>
 *
 * <p>录制策略：<b>故意放宽到「候选池取大、阈值设为 0」</b>，把向量库返回的完整候选列表
 * 落盘。阈值过滤与 topK 截断交给 {@link RetrievalEvaluator} 在离线评估时施加，
 * 这样同一份观测数据可以反复用来回答「阈值 0.18 还是 0.28」「topK 8 还是 20」。
 * 录制完成后会同时刷新 {@code baseline.json}。
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "eval.record", matches = "true")
@DisplayName("RAG 评估观测数据录制")
class RetrievalEvalRecordingTest {

    /** 录制时故意取大：候选池越大，离线调参的空间越大。 */
    private static final int CANDIDATE_POOL = 50;

    @Autowired
    private KnowledgeBaseVectorService vectorService;

    @Autowired
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @Test
    @DisplayName("跑真实检索，写入观测数据并刷新基线")
    void recordObservations() throws IOException {
        List<Long> knowledgeBaseIds = resolveKnowledgeBaseIds();
        System.out.printf("检索范围：知识库 %s%n", knowledgeBaseIds);
        Path datasetDir = RetrievalEvalLoader.resolveDefaultDir();
        RetrievalEvalLoader.GoldenSet goldenSet = RetrievalEvalLoader.loadCases(datasetDir);

        Map<String, List<RetrievedChunk>> observations = new LinkedHashMap<>();
        for (RetrievalEvalCase evalCase : goldenSet.cases()) {
            long startedAt = System.currentTimeMillis();
            List<Document> documents =
                vectorService.similaritySearch(evalCase.question(), knowledgeBaseIds, CANDIDATE_POOL, 0.0);
            long elapsedMs = System.currentTimeMillis() - startedAt;

            List<RetrievedChunk> chunks = documents == null
                ? List.of()
                : documents.stream()
                    .map(doc -> new RetrievedChunk(sourceOf(doc), doc.getText(), scoreOf(doc)))
                    .toList();

            observations.put(evalCase.name(), chunks);
            System.out.printf("[record] %-24s 候选=%-3d 耗时=%dms%n",
                evalCase.name(), chunks.size(), elapsedMs);
        }

        boolean hasScores = observations.values().stream()
            .flatMap(List::stream)
            .anyMatch(chunk -> chunk.score() > 0.0);
        assertTrue(hasScores,
            "召回的片段没有相似度分数，无法做阈值实验；请确认向量库返回 score（pgvector 应返回）");

        RetrievalEvalLoader.writeObservations(datasetDir, goldenSet.topK(), goldenSet.minScore(), observations);

        RetrievalEvalReport report = RetrievalEvaluator.evaluate(
            goldenSet.cases(), observations, goldenSet.topK(), goldenSet.minScore());
        System.out.println(report.toMarkdown());
        RetrievalEvalLoader.writeBaseline(datasetDir, report);
        System.out.printf("已写入观测数据与基线：%s%n", RetrievalEvalLoader.observedFile(datasetDir));
    }

    /** 未显式指定 EVAL_KB_IDS 时对全部知识库检索，省去手工查 ID。 */
    private List<Long> resolveKnowledgeBaseIds() {
        String raw = System.getenv("EVAL_KB_IDS");
        if (raw != null && !raw.isBlank()) {
            return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(Long::parseLong)
                .toList();
        }
        List<KnowledgeBaseEntity> all = knowledgeBaseRepository.findAll();
        all.forEach(kb -> System.out.printf("  候选知识库 kb#%d  %s  (%s)%n",
            kb.getId(), kb.getName(), kb.getOriginalFilename()));
        List<Long> ids = all.stream().map(KnowledgeBaseEntity::getId).toList();
        assertTrue(!ids.isEmpty(),
            "数据库里没有知识库；请先把 cases.json 中 corpus 列出的文档导入知识库，或显式指定 EVAL_KB_IDS");
        return ids;
    }

    private static String sourceOf(Document doc) {
        Map<String, Object> metadata = doc.getMetadata();
        if (metadata == null) {
            return null;
        }
        for (String key : List.of("source", "file_name", "filename", "doc_name", "title")) {
            Object value = metadata.get(key);
            if (value != null) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    private static double scoreOf(Document doc) {
        Double score = doc.getScore();
        return score == null ? 0.0 : score;
    }
}
