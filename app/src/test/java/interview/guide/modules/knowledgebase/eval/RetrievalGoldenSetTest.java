package interview.guide.modules.knowledgebase.eval;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 评估数据集完整性校验。
 *
 * <p>最重要的一条是「标记串必须真能在期望文档里找到」——标记串打错一个字，
 * 该用例就会永远 miss，而报告只会显示命中率偏低，很难定位。这条断言把这类
 * 静默错误挡在 CI 里。
 *
 * <p>语料（{@code cases.json} 的 corpus 清单）可能不在仓库内（例如语料是本地资料、
 * 不便公开）。此时用例的结构性校验照常执行，依赖语料正文的三条断言跳过——
 * 数据集生效需要语料，但不该因此让整个测试套件变红。
 */
@DisplayName("RAG 评估数据集完整性")
class RetrievalGoldenSetTest {

    private static Path datasetDir;
    private static Path repoRoot;
    private static RetrievalEvalLoader.GoldenSet goldenSet;

    @BeforeAll
    static void setUp() throws IOException {
        datasetDir = RetrievalEvalLoader.resolveDefaultDir();
        assumeTrue(Files.isRegularFile(datasetDir.resolve("cases.json")),
            "未找到 RAG 评估数据集，跳过：" + datasetDir);
        // evals/retrieval-golden -> 仓库根
        repoRoot = datasetDir.getParent().getParent();
        goldenSet = RetrievalEvalLoader.loadCases(datasetDir);
    }

    /** 语料清单里的文件是否都在本地可读。 */
    private static boolean corpusAvailable() {
        if (goldenSet.corpus().isEmpty()) {
            return false;
        }
        for (String relative : goldenSet.corpus()) {
            if (!Files.isRegularFile(repoRoot.resolve(relative))) {
                return false;
            }
        }
        return true;
    }

    @Test
    @DisplayName("用例非空、名称唯一、语料清单非空")
    void casesAreWellFormed() {
        assertFalse(goldenSet.cases().isEmpty(), "评估用例不能为空");
        assertFalse(goldenSet.corpus().isEmpty(), "语料清单不能为空");

        Set<String> names = new HashSet<>();
        for (RetrievalEvalCase c : goldenSet.cases()) {
            assertTrue(names.add(c.name()), "用例名重复: " + c.name());
        }
    }

    @Test
    @DisplayName("检索参数在合理范围")
    void parametersAreSane() {
        assertTrue(goldenSet.topK() >= 1, "topK 必须 >= 1");
        assertTrue(goldenSet.minScore() >= 0.0 && goldenSet.minScore() < 1.0,
            "minScore 应在 [0,1) 内，实际 " + goldenSet.minScore());
    }

    @Test
    @DisplayName("每个用例都声明了期望文档与标记串")
    void casesDeclareExpectations() {
        for (RetrievalEvalCase c : goldenSet.cases()) {
            assertNotNull(c.expectedDoc(), c.name() + " 缺少 expectedDoc");
            assertFalse(c.expectedDoc().isBlank(), c.name() + " 的 expectedDoc 为空");
            assertFalse(c.expectedMarkers().isEmpty(), c.name() + " 没有标记串");
            for (String marker : c.expectedMarkers()) {
                assertFalse(marker == null || marker.isBlank(),
                    c.name() + " 含空标记串");
            }
        }
    }

    @Test
    @DisplayName("每个用例的期望文档都在语料清单内")
    void expectedDocsDeclaredInCorpus() {
        for (RetrievalEvalCase c : goldenSet.cases()) {
            assertTrue(goldenSet.corpus().contains(c.expectedDoc()),
                c.name() + " 的 expectedDoc 不在 corpus 清单里: " + c.expectedDoc());
        }
    }

    @Test
    @DisplayName("语料文件存在于本地（语料不在仓库时跳过）")
    void expectedDocsExist() {
        assumeTrue(corpusAvailable(), "语料不在本地，跳过（需要 corpus 清单里的文件）");
        for (RetrievalEvalCase c : goldenSet.cases()) {
            Path doc = repoRoot.resolve(c.expectedDoc());
            assertTrue(Files.isRegularFile(doc), "语料文件不存在: " + doc);
        }
    }

    @Test
    @DisplayName("每个标记串都能在期望文档中真找到（语料不在仓库时跳过）")
    void markersActuallyExistInCorpus() throws IOException {
        assumeTrue(corpusAvailable(), "语料不在本地，跳过（无法校验标记串）");
        for (RetrievalEvalCase c : goldenSet.cases()) {
            String docText = readLenient(repoRoot.resolve(c.expectedDoc())).toLowerCase();
            for (String marker : c.expectedMarkers()) {
                assertTrue(docText.contains(marker.toLowerCase().trim()),
                    String.format("用例 %s 的标记 \"%s\" 在 %s 中找不到，该用例会永远 miss",
                        c.name(), marker, c.expectedDoc()));
            }
        }
    }

    @Test
    @DisplayName("问题措辞不与标记串字面相同——否则是字面回声而非检索")
    void questionsAreNotLiteralEchoes() {
        for (RetrievalEvalCase c : goldenSet.cases()) {
            String question = c.question().toLowerCase().trim();
            for (String marker : c.expectedMarkers()) {
                assertFalse(question.equals(marker.toLowerCase().trim()),
                    c.name() + " 的问题与标记串完全相同，测不出检索能力");
            }
        }
    }

    @Test
    @DisplayName("观测数据（若已录制）只包含已知用例")
    void observationsOnlyContainKnownCases() throws IOException {
        var observations = RetrievalEvalLoader.loadObservations(datasetDir);
        if (observations.isEmpty()) {
            return; // 尚未录制，允许
        }
        Set<String> known = new HashSet<>();
        goldenSet.cases().forEach(c -> known.add(c.name()));
        for (String name : observations.keySet()) {
            assertTrue(known.contains(name), "观测数据里出现未知用例: " + name);
        }
    }

    @Test
    @DisplayName("已录制的用例必须有过检索结果")
    void recordedCasesHaveCandidates() throws IOException {
        var observations = RetrievalEvalLoader.loadObservations(datasetDir);
        if (observations.isEmpty()) {
            return;
        }
        observations.forEach((name, chunks) -> {
            assertNotNull(chunks, "观测数据为空: " + name);
            assertFalse(chunks.isEmpty(), "用例 " + name + " 录到了 0 条候选，说明检索本身失败");
        });
    }

    @Test
    @DisplayName("语料清单里的文件都可读且非空（语料不在仓库时跳过）")
    void corpusFilesReadable() throws IOException {
        assumeTrue(corpusAvailable(), "语料不在本地，跳过");
        for (String relative : goldenSet.corpus()) {
            Path doc = repoRoot.resolve(relative);
            assertFalse(readLenient(doc).isBlank(), "语料文件为空: " + doc);
        }
    }

    @Test
    @DisplayName("用例覆盖多个文档，避免只测单一语料")
    void casesCoverMultipleDocuments() {
        Set<String> docs = new HashSet<>();
        goldenSet.cases().forEach(c -> docs.add(c.expectedDoc()));
        assertTrue(docs.size() >= 2, "评估用例应覆盖至少 2 个文档，实际 " + docs.size());
    }

    /**
     * 宽松解码：语料里可能存在个别非法 UTF-8 字节，直接用 {@code Files.readString}
     * 会抛 MalformedInputException。校验标记不应因此失败。
     */
    private static String readLenient(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        return StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)
            .decode(java.nio.ByteBuffer.wrap(bytes))
            .toString();
    }
}
