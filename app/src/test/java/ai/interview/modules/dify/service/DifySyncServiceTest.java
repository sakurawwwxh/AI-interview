package ai.interview.modules.dify.service;

import ai.interview.infrastructure.file.FileHashService;
import ai.interview.infrastructure.file.FileStorageService;
import ai.interview.modules.dify.client.DifyApiClient;
import ai.interview.modules.dify.config.DifyConfig;
import ai.interview.modules.dify.model.DifySyncAction;
import ai.interview.modules.dify.model.DifySyncDirection;
import ai.interview.modules.dify.model.DifySyncLogEntity;
import ai.interview.modules.dify.model.DifySyncStatus;
import ai.interview.modules.dify.repository.DifySyncLogRepository;
import ai.interview.modules.knowledgebase.listener.VectorizeStreamProducer;
import ai.interview.modules.knowledgebase.model.KnowledgeBaseEntity;
import ai.interview.modules.knowledgebase.repository.KnowledgeBaseRepository;
import ai.interview.modules.knowledgebase.service.KnowledgeBasePersistenceService;
import ai.interview.modules.knowledgebase.service.KnowledgeBaseVectorService;
import ai.interview.modules.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Dify 同步的写回语义与事务边界。
 *
 * <p>两次 Dify HTTP 调用（创建/更新文档）必须跑在事务之外，否则数据库连接会被网络往返
 * 占住；这条约束行为测试看不出来，只能用结构断言守住。
 */
@ExtendWith(MockitoExtension.class)
class DifySyncServiceTest {

    private static final long KB_ID = 41L;
    private static final String DATASET_ID = "ds-1";

    @Mock private DifyApiClient difyApiClient;
    @Mock private KnowledgeBaseRepository knowledgeBaseRepository;
    @Mock private DifySyncLogRepository syncLogRepository;
    @Mock private FileStorageService storageService;
    @Mock private FileHashService fileHashService;
    @Mock private KnowledgeBasePersistenceService persistenceService;
    @Mock private VectorizeStreamProducer vectorizeStreamProducer;
    @Mock private KnowledgeBaseVectorService vectorService;
    @Mock private UserRepository userRepository;

    private DifyConfig config;
    private DifySyncService service;

    @BeforeEach
    void setUp() {
        config = new DifyConfig();
        config.getSync().setEnabled(true);
        config.setDatasetId(DATASET_ID);
        service = new DifySyncService(difyApiClient, knowledgeBaseRepository, syncLogRepository, config,
            storageService, fileHashService, persistenceService, vectorizeStreamProducer, vectorService,
            userRepository);
    }

    @Test
    @DisplayName("新建文档成功：只回写 Dify 字段，日志记 CREATE/SUCCESS")
    void writesBackOnlyDifyFieldsAfterCreatingTheDocument() {
        KnowledgeBaseEntity entity = knowledgeBase();
        when(difyApiClient.createDocument(eq(DATASET_ID), eq("正文"), anyMap())).thenReturn("doc-1");
        when(knowledgeBaseRepository.findById(KB_ID)).thenReturn(Optional.of(entity));

        service.syncToDify(KB_ID, "Java 并发", "java.md", "Java", "正文", null);

        assertEquals("doc-1", entity.getDifyDocumentId());
        assertEquals(DifySyncStatus.SYNCED, entity.getDifySyncStatus());
        assertNull(entity.getDifySyncError());
        assertNotNull(entity.getDifySyncTime());
        verify(knowledgeBaseRepository).save(entity);

        DifySyncLogEntity log = capturedLog();
        assertEquals(KB_ID, log.getKnowledgeBaseId());
        assertEquals("doc-1", log.getDifyDocumentId());
        assertEquals(DifySyncDirection.TO_DIFY, log.getSyncDirection());
        assertEquals(DifySyncAction.CREATE, log.getSyncAction());
        assertEquals(DifySyncStatus.SUCCESS, log.getSyncStatus());
    }

    @Test
    @DisplayName("已有 Dify 文档：走更新接口，日志记 UPDATE")
    void updatesExistingDocumentInsteadOfCreatingAnother() {
        KnowledgeBaseEntity entity = knowledgeBase();
        when(knowledgeBaseRepository.findById(KB_ID)).thenReturn(Optional.of(entity));

        service.syncToDify(KB_ID, "Java 并发", "java.md", "Java", "正文", "doc-old");

        verify(difyApiClient).updateDocument(DATASET_ID, "doc-old", "Java 并发", "正文");
        assertEquals("doc-old", entity.getDifyDocumentId());
        assertEquals(DifySyncAction.UPDATE, capturedLog().getSyncAction());
    }

    @Test
    @DisplayName("Dify 调用失败：状态置 FAILED、错误截到 500 字，但不向外抛")
    void degradesToFailedStatusWithoutThrowing() {
        KnowledgeBaseEntity entity = knowledgeBase();
        String longMessage = "x".repeat(600);
        when(difyApiClient.createDocument(eq(DATASET_ID), any(), anyMap()))
            .thenThrow(new IllegalStateException(longMessage));
        when(knowledgeBaseRepository.findById(KB_ID)).thenReturn(Optional.of(entity));

        assertDoesNotThrow(() ->
            service.syncToDify(KB_ID, "Java 并发", "java.md", "Java", "正文", null));

        assertEquals(DifySyncStatus.FAILED, entity.getDifySyncStatus());
        assertEquals(500, entity.getDifySyncError().length());
        assertEquals(DifySyncStatus.FAILED, capturedLog().getSyncStatus());
    }

    @Test
    @DisplayName("同步关闭时不发任何请求")
    void doesNothingWhenSyncIsDisabled() {
        config.getSync().setEnabled(false);

        service.syncToDify(KB_ID, "Java 并发", "java.md", "Java", "正文", null);

        verify(difyApiClient, never()).createDocument(any(), any(), anyMap());
        verify(knowledgeBaseRepository, never()).findById(any());
    }

    @Test
    @DisplayName("删除文档：没有 Dify 关联时直接跳过")
    void skipsDeletionWhenNoDifyDocumentIsLinked() {
        KnowledgeBaseEntity entity = knowledgeBase();

        service.deleteFromDify(entity);

        verify(difyApiClient, never()).deleteDocument(any(), any());
    }

    @Test
    @DisplayName("结构守卫：Dify 外呼方法（含类级注解）都不得带事务，且仍是 @Async")
    void externalCallsMustStayOutsideTransactions() {
        // 类级 @Transactional 会连带把两个外呼方法包进事务；jakarta 那套注解 Spring 同样会解析
        assertFalse(DifySyncService.class.isAnnotationPresent(Transactional.class),
            "类级 @Transactional 会把 Dify 外呼一起包进事务");
        assertFalse(DifySyncService.class.isAnnotationPresent(jakarta.transaction.Transactional.class),
            "类级 jakarta @Transactional 同样会开启事务");

        for (String name : new String[]{"syncToDify", "deleteFromDify"}) {
            List<Method> methods = Arrays.stream(DifySyncService.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(name))
                .toList();
            assertFalse(methods.isEmpty(), "方法不存在: " + name);
            for (Method method : methods) {
                assertFalse(method.isAnnotationPresent(Transactional.class),
                    name + " 在事务里做 Dify HTTP 调用，会占住数据库连接");
                assertFalse(method.isAnnotationPresent(jakarta.transaction.Transactional.class),
                    name + " 用了 jakarta @Transactional，Spring 同样会开启事务");
                assertTrue(method.isAnnotationPresent(Async.class), name + " 应当保持异步");
            }
        }
    }

    private DifySyncLogEntity capturedLog() {
        ArgumentCaptor<DifySyncLogEntity> captor = ArgumentCaptor.forClass(DifySyncLogEntity.class);
        verify(syncLogRepository).save(captor.capture());
        return captor.getValue();
    }

    private KnowledgeBaseEntity knowledgeBase() {
        KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
        entity.setId(KB_ID);
        entity.setUserId(1L);
        entity.setName("Java 并发");
        return entity;
    }
}
