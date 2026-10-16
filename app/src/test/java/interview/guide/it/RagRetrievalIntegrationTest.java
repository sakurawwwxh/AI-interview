package interview.guide.it;

import interview.guide.modules.knowledgebase.model.KnowledgeBaseEntity;
import interview.guide.modules.knowledgebase.repository.KnowledgeBaseRepository;
import interview.guide.modules.knowledgebase.service.KnowledgeBaseVectorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RAG 检索链路的集成测试：真实数据库 + 真实 pgvector 索引。
 *
 * <p>与既有的 200 个 Mockito 单测互补——单测验证「组件自己的逻辑」，这里验证
 * **接线是否真的通**：分块 → 向量化 → pgvector 写入 → 相似度检索 → kb_id 前置过滤 → 阈值生效。
 * 这几步里任何一处配置写错（过滤器表达式、维度、扩展未装），单测都发现不了。
 *
 * <p>模型用 {@link StubEmbeddingConfiguration} 的确定性 stub，所以不依赖外部服务、可重复。
 * 数据写在独立测试库 {@code interview_guide_it}（见 {@link IntegrationTestDatabase}），
 * 业务库不受影响。数据库不可达时整类跳过。
 */
@SpringBootTest
@Import(StubEmbeddingConfiguration.class)
@EnabledIf("interview.guide.it.IntegrationTestDatabase#available")
@DisplayName("RAG 检索链路 · 集成测试")
class RagRetrievalIntegrationTest {

    private static final String DOC_OVERLAP = """
        分块策略与检索召回
        分块 overlap 是检索召回率的关键参数：两块之间保留少量重叠，可以避免关键句
        正好被切在边界上而两边都检索不到。overlap 太小等于没有，太大则向量数量膨胀。
        实践上通常取分块长度的 5% 到 15%。
        """;

    private static final String DOC_UNRELATED = """
        支付回调验签
        支付宝异步通知需要校验 RSA 签名，并按真实订单金额发起支付，避免金额被篡改。
        """;

    @DynamicPropertySource
    static void useIsolatedDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", IntegrationTestDatabase::jdbcUrl);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        // 检索链路不调用对话模型，但 Spring AI 的 OpenAI 自动配置在启动时就要求 key 存在；
        // 给个占位值让上下文能起，测试不需要真实凭据即可运行。
        registry.add("spring.ai.openai.api-key", () -> "it-placeholder-key");
    }

    @Autowired
    private KnowledgeBaseVectorService vectorService;

    @Autowired
    private KnowledgeBaseRepository knowledgeBaseRepository;

    private Long knowledgeBaseId;

    @BeforeEach
    void createKnowledgeBase() {
        KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
        entity.setUserId(1L);
        entity.setFileHash("it-" + UUID.randomUUID());
        entity.setName("it-kb-" + UUID.randomUUID());
        entity.setOriginalFilename("it.md");
        entity.setUploadedAt(LocalDateTime.now());
        knowledgeBaseId = knowledgeBaseRepository.save(entity).getId();
    }

    @Test
    @DisplayName("分块→向量化→pgvector 写入→相似度检索，能召回正确片段")
    void vectorizeThenRetrieve() {
        vectorService.vectorizeAndStore(knowledgeBaseId, DOC_OVERLAP);

        List<Document> hits = vectorService.similaritySearch("分块 overlap 是什么", List.of(knowledgeBaseId), 5, 0.0);

        assertFalse(hits.isEmpty(), "向量化写入后应能检索到片段（写入或索引链路断了？）");
        assertTrue(hits.stream().anyMatch(doc -> doc.getText().contains("overlap")),
            "命中的片段应包含关键词 overlap，实际内容: " + hits.get(0).getText());
    }

    @Test
    @DisplayName("pgvector 真的写进去了：换个上下文再检索依然能召回")
    void vectorizeIsPersisted() {
        vectorService.vectorizeAndStore(knowledgeBaseId, DOC_OVERLAP);

        // 换一个「重新查询」的动作，确认不是内存态侥幸命中
        List<Document> first = vectorService.similaritySearch("分块边界导致检索不到", List.of(knowledgeBaseId), 3, 0.0);
        List<Document> second = vectorService.similaritySearch("overlap 取多少合适", List.of(knowledgeBaseId), 3, 0.0);

        assertFalse(first.isEmpty(), "第一次检索应有结果");
        assertFalse(second.isEmpty(), "第二次检索应有结果（说明向量确实落库了）");
    }

    @Test
    @DisplayName("kb_id 前置过滤生效：隔离的知识库里检索不到另一库的内容")
    void knowledgeBasesAreIsolated() {
        Long otherKnowledgeBaseId = createAnotherKnowledgeBase();
        vectorService.vectorizeAndStore(knowledgeBaseId, DOC_OVERLAP);
        vectorService.vectorizeAndStore(otherKnowledgeBaseId, DOC_UNRELATED);

        List<Document> hitsInOther = vectorService.similaritySearch(
            "分块 overlap 是什么", List.of(otherKnowledgeBaseId), 5, 0.0);

        assertTrue(hitsInOther.stream().noneMatch(doc -> doc.getText().contains("overlap")),
            "不该跨知识库召回：另一个库里不该出现本库的 overlap 内容");
    }

    @Test
    @DisplayName("相似度阈值真的生效：提高阈值后同一 query 结果被过滤")
    void minScoreFiltersResults() {
        vectorService.vectorizeAndStore(knowledgeBaseId, DOC_OVERLAP);

        List<Document> loose = vectorService.similaritySearch("分块 overlap", List.of(knowledgeBaseId), 5, 0.0);
        List<Document> strict = vectorService.similaritySearch("分块 overlap", List.of(knowledgeBaseId), 5, 0.999);

        assertFalse(loose.isEmpty(), "不限阈值时应有召回");
        assertTrue(strict.size() < loose.size(),
            "阈值 0.999 应过滤掉部分结果，实际: loose=" + loose.size() + " strict=" + strict.size());
    }

    @Test
    @DisplayName("空知识库检索返回空列表，不抛异常")
    void emptyKnowledgeBaseReturnsEmpty() {
        List<Document> hits = vectorService.similaritySearch("任意问题", List.of(knowledgeBaseId), 5, 0.0);
        assertTrue(hits.isEmpty(), "没有写入任何内容时不该有召回");
    }

    @Test
    @DisplayName("重复向量化同一知识库不产生重复片段（先删后插的幂等语义）")
    void revectorizeIsIdempotent() {
        vectorService.vectorizeAndStore(knowledgeBaseId, DOC_OVERLAP);
        int firstRound = vectorService.similaritySearch("分块 overlap", List.of(knowledgeBaseId), 50, 0.0).size();

        vectorService.vectorizeAndStore(knowledgeBaseId, DOC_OVERLAP);
        int secondRound = vectorService.similaritySearch("分块 overlap", List.of(knowledgeBaseId), 50, 0.0).size();

        assertEquals(firstRound, secondRound,
            "重复向量化应覆盖旧向量而不是叠加（否则 PEL 回收重放会让片段越滚越多）");
    }

    private Long createAnotherKnowledgeBase() {
        KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
        entity.setUserId(1L);
        entity.setFileHash("it-other-" + UUID.randomUUID());
        entity.setName("it-kb-other-" + UUID.randomUUID());
        entity.setOriginalFilename("it-other.md");
        entity.setUploadedAt(LocalDateTime.now());
        return knowledgeBaseRepository.save(entity).getId();
    }
}
