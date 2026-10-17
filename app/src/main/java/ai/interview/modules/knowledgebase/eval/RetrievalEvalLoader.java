package ai.interview.modules.knowledgebase.eval;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * RAG 评估数据集的读写。
 *
 * <p>目录约定（与 {@code user.dir} 相对，通常为仓库根）：
 * <pre>
 * evals/retrieval-golden/
 *   cases.json                  评估用例（ground truth，人工维护，进版本库）
 *   baseline.json               上一次录制的指标基线（命中率 / MRR）
 *   observed/observations.json  真实检索的观测数据（由录制用例生成）
 * </pre>
 *
 * <p>观测文件刻意只有一个，避免中文用例名直接作为文件名在不同平台上的兼容问题。
 */
public final class RetrievalEvalLoader {

    /** 默认数据集目录，相对当前工作目录。 */
    public static final Path DEFAULT_DIR = Path.of("evals", "retrieval-golden");

    private static final String CASES_FILE = "cases.json";
    private static final String OBSERVED_DIR = "observed";
    private static final String OBSERVED_FILE = "observations.json";
    private static final String BASELINE_FILE = "baseline.json";

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .enable(SerializationFeature.INDENT_OUTPUT);

    private RetrievalEvalLoader() {
    }

    /** 评估用例集合。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GoldenSet(
        int version,
        List<String> corpus,
        int topK,
        double minScore,
        List<RetrievalEvalCase> cases
    ) {
        public GoldenSet {
            corpus = corpus == null ? List.of() : List.copyOf(corpus);
            cases = cases == null ? List.of() : List.copyOf(cases);
        }
    }

    /** 观测数据文件内容。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Observations(
        String recordedAt,
        int topK,
        double minScore,
        Map<String, List<RetrievedChunk>> observations
    ) {
        public Observations {
            observations = observations == null ? Map.of() : Map.copyOf(observations);
        }
    }

    /** 指标基线。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Baseline(
        String recordedAt,
        int topK,
        double minScore,
        int total,
        int observed,
        double hitRate,
        double mrr
    ) {
    }

    public static GoldenSet loadCases(Path dir) throws IOException {
        return MAPPER.readValue(Files.readString(dir.resolve(CASES_FILE), StandardCharsets.UTF_8), GoldenSet.class);
    }

    public static GoldenSet loadDefault() throws IOException {
        return loadCases(resolveDefaultDir());
    }

    /**
     * 定位数据集目录。多模块 Maven 构建下 surefire 的工作目录是模块目录（app/），
     * 而数据集放在仓库根的 evals/ 下，因此从当前工作目录逐级向上查找，
     * 找不到时回退到 {@link #DEFAULT_DIR}（由调用方报出清晰的文件不存在错误）。
     */
    public static Path resolveDefaultDir() {
        Path current = Path.of("").toAbsolutePath();
        for (int i = 0; i < 5 && current != null; i++) {
            Path candidate = current.resolve(DEFAULT_DIR);
            if (Files.isRegularFile(candidate.resolve(CASES_FILE))) {
                return candidate;
            }
            current = current.getParent();
        }
        return DEFAULT_DIR;
    }

    public static boolean isPresent(Path dir) {
        return Files.isRegularFile(dir.resolve(CASES_FILE));
    }

    public static Path observedFile(Path dir) {
        return dir.resolve(OBSERVED_DIR).resolve(OBSERVED_FILE);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, List<RetrievedChunk>> loadObservations(Path dir) throws IOException {
        Path file = observedFile(dir);
        if (!Files.isRegularFile(file)) {
            return Map.of();
        }
        Observations loaded = MAPPER.readValue(Files.readString(file, StandardCharsets.UTF_8), Observations.class);
        return loaded.observations();
    }

    /** 录制一份观测数据（用例名 -&gt; 完整候选列表，未做阈值过滤与截断）。 */
    public static void writeObservations(Path dir,
                                         int topK,
                                         double minScore,
                                         Map<String, List<RetrievedChunk>> observations) throws IOException {
        Files.createDirectories(observedFile(dir).getParent());
        Map<String, List<RetrievedChunk>> ordered = new TreeMap<>(observations);
        Observations payload = new Observations(Instant.now().toString(), topK, minScore, ordered);
        Files.writeString(observedFile(dir), MAPPER.writeValueAsString(payload), StandardCharsets.UTF_8);
    }

    public static Optional<Baseline> loadBaseline(Path dir) throws IOException {
        Path file = dir.resolve(BASELINE_FILE);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        return Optional.of(MAPPER.readValue(Files.readString(file, StandardCharsets.UTF_8), Baseline.class));
    }

    /** 把本次评估结果写为基线，后续回归以此为准。 */
    public static void writeBaseline(Path dir, RetrievalEvalReport report) throws IOException {
        Files.createDirectories(dir);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("recordedAt", Instant.now().toString());
        payload.put("topK", report.topK());
        payload.put("minScore", report.minScore());
        payload.put("total", report.total());
        payload.put("observed", report.observedCount());
        payload.put("hitRate", round4(report.hitRate()));
        payload.put("mrr", round4(report.mrr()));
        Files.writeString(dir.resolve(BASELINE_FILE), MAPPER.writeValueAsString(payload), StandardCharsets.UTF_8);
    }

    private static double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}
