package ai.interview.modules.dify.service;

import ai.interview.infrastructure.file.FileHashService;
import ai.interview.infrastructure.file.FileStorageService;
import ai.interview.modules.dify.client.DifyApiClient;
import ai.interview.modules.dify.config.DifyConfig;
import ai.interview.modules.dify.exception.DifySyncException;
import ai.interview.modules.dify.model.DifyDocument;
import ai.interview.modules.dify.model.DifySegment;
import ai.interview.modules.dify.model.DifyDocumentList;
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
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
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

    @Test
    @DisplayName("删除意图写成 PENDING 墓碑；没有 Dify 文档时不写")
    void recordsDeletionTombstone() {
        service.recordDeletionIntent(KB_ID, "doc-1");

        DifySyncLogEntity tombstone = capturedLog();
        assertEquals(KB_ID, tombstone.getKnowledgeBaseId());
        assertEquals("doc-1", tombstone.getDifyDocumentId());
        assertEquals(DifySyncDirection.TO_DIFY, tombstone.getSyncDirection());
        assertEquals(DifySyncAction.DELETE, tombstone.getSyncAction());
        assertEquals(DifySyncStatus.PENDING, tombstone.getSyncStatus());

        service.recordDeletionIntent(KB_ID, "  ");
        verifyNoMoreInteractions(syncLogRepository);
    }

    @Test
    @DisplayName("删除成功：更新已有墓碑，不再插一条新日志")
    void deleteMarksTheTombstoneAsSucceeded() {
        DifySyncLogEntity tombstone = tombstone("doc-1", DifySyncStatus.PENDING);
        when(syncLogRepository.findFirstByDifyDocumentIdAndSyncActionOrderByCreatedAtDesc(
            "doc-1", DifySyncAction.DELETE)).thenReturn(Optional.of(tombstone));

        service.deleteFromDify(knowledgeBaseWith("doc-1"));

        verify(difyApiClient).deleteDocument(DATASET_ID, "doc-1");
        assertEquals(DifySyncStatus.SUCCESS, tombstone.getSyncStatus());
        verify(syncLogRepository).save(tombstone);
    }

    @Test
    @DisplayName("删除失败：墓碑留成 FAILED 供定时重试，不向外抛")
    void deleteMarksTheTombstoneAsFailed() {
        DifySyncLogEntity tombstone = tombstone("doc-1", DifySyncStatus.PENDING);
        when(syncLogRepository.findFirstByDifyDocumentIdAndSyncActionOrderByCreatedAtDesc(
            "doc-1", DifySyncAction.DELETE)).thenReturn(Optional.of(tombstone));
        doThrow(new RuntimeException("dify down")).when(difyApiClient).deleteDocument(DATASET_ID, "doc-1");

        assertDoesNotThrow(() -> service.deleteFromDify(knowledgeBaseWith("doc-1")));

        assertEquals(DifySyncStatus.FAILED, tombstone.getSyncStatus());
        assertEquals("dify down", tombstone.getErrorMessage());
    }

    @Test
    @DisplayName("反向同步遇到墓碑：跳过回拉，不重新建库")
    void syncFromDifySkipsTombstonedDocuments() {
        DifyDocument doc = DifyDocument.builder().id("doc-gone").name("gone.md").build();
        when(difyApiClient.listDocuments(DATASET_ID, 1, 100)).thenReturn(DifyDocumentList.builder()
            .data(List.of(doc)).hasMore(false).build());
        when(knowledgeBaseRepository.findByDifyDocumentId("doc-gone")).thenReturn(Optional.empty());
        when(syncLogRepository.existsByDifyDocumentIdAndSyncAction("doc-gone", DifySyncAction.DELETE))
            .thenReturn(true);

        service.syncFromDify();

        verify(difyApiClient, never()).fetchAllSegments(any(), any());
        verifyNoInteractions(persistenceService);
    }

    @Test
    @DisplayName("重试墓碑：成功与已不存在都算完成，其余失败保留待下次")
    void retriesPendingDeletions() {
        DifySyncLogEntity ok = tombstone("doc-ok", DifySyncStatus.PENDING);
        DifySyncLogEntity gone = tombstone("doc-gone", DifySyncStatus.FAILED);
        DifySyncLogEntity bad = tombstone("doc-bad", DifySyncStatus.PENDING);
        when(syncLogRepository.findRetryable(eq(DifySyncAction.DELETE), anyCollection(), any(), any()))
            .thenReturn(List.of(ok, gone, bad));
        // 严格桩要求把同一个方法的每次调用都显式声明，否则成功那条会触发 PotentialStubbingProblem
        doNothing().when(difyApiClient).deleteDocument(DATASET_ID, "doc-ok");
        doThrow(new DifySyncException("删除文档失败: 404",
            new HttpClientErrorException(HttpStatus.NOT_FOUND)))
            .when(difyApiClient).deleteDocument(DATASET_ID, "doc-gone");
        doThrow(new DifySyncException("dify down"))
            .when(difyApiClient).deleteDocument(DATASET_ID, "doc-bad");

        int cleared = service.retryPendingDeletions();

        assertEquals(2, cleared);
        assertEquals(DifySyncStatus.SUCCESS, ok.getSyncStatus());
        assertEquals(DifySyncStatus.SUCCESS, gone.getSyncStatus());
        assertEquals(DifySyncStatus.FAILED, bad.getSyncStatus());

        // 重试窗口与状态过滤是这个方法的契约，不能只断言结果
        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<Collection<DifySyncStatus>> statuses = ArgumentCaptor.forClass(Collection.class);
        verify(syncLogRepository).findRetryable(eq(DifySyncAction.DELETE), statuses.capture(),
            cutoff.capture(), any());
        assertEquals(Set.of(DifySyncStatus.PENDING, DifySyncStatus.FAILED), Set.copyOf(statuses.getValue()));
        assertTrue(cutoff.getValue().isAfter(LocalDateTime.now().minusDays(8))
                && cutoff.getValue().isBefore(LocalDateTime.now().minusDays(6)),
            "只重试最近 7 天内的墓碑");
    }

    @Test
    @DisplayName("同步关闭时不重试删除")
    void doesNotRetryWhenSyncIsDisabled() {
        config.getSync().setEnabled(false);

        assertEquals(0, service.retryPendingDeletions());
        verifyNoInteractions(syncLogRepository);
    }

    @Test
    @DisplayName("同步期间知识库被删除：为刚建好的远端文档写删除墓碑")
    void writesTombstoneWhenKnowledgeBaseVanishesDuringSync() {
        when(difyApiClient.createDocument(eq(DATASET_ID), any(), anyMap())).thenReturn("doc-orphan");
        when(knowledgeBaseRepository.findById(KB_ID)).thenReturn(Optional.empty());

        service.syncToDify(KB_ID, "Java 并发", "java.md", "Java", "正文", null);

        DifySyncLogEntity tombstone = capturedLog();
        assertEquals("doc-orphan", tombstone.getDifyDocumentId());
        assertEquals(DifySyncAction.DELETE, tombstone.getSyncAction());
        assertEquals(DifySyncStatus.PENDING, tombstone.getSyncStatus());
        verify(knowledgeBaseRepository, never()).save(any());
    }

    @Test
    @DisplayName("回拉落库期间知识库被删除：撤销刚建的记录与对象存储文件")
    void compensatesWhenDeletionHappensDuringPull() {
        DifyDocument doc = DifyDocument.builder().id("doc-race").name("race.md").build();
        when(difyApiClient.listDocuments(DATASET_ID, 1, 100)).thenReturn(DifyDocumentList.builder()
            .data(List.of(doc)).hasMore(false).build());
        when(knowledgeBaseRepository.findByDifyDocumentId("doc-race")).thenReturn(Optional.empty());
        // 拉分段前没有墓碑，落库后再查时命中（并发删除刚刚提交）
        when(syncLogRepository.existsByDifyDocumentIdAndSyncAction("doc-race", DifySyncAction.DELETE))
            .thenReturn(false, true);
        when(difyApiClient.fetchAllSegments(DATASET_ID, "doc-race"))
            .thenReturn(List.of(DifySegment.builder().content("正文").build()));
        when(fileHashService.calculateHash(any(byte[].class))).thenReturn("hash");
        when(storageService.uploadBytes(any(byte[].class), anyString(), anyString(), anyString()))
            .thenReturn("kb/race.md");
        when(storageService.getFileUrl("kb/race.md")).thenReturn("url");
        KnowledgeBaseEntity saved = knowledgeBase();
        saved.setId(99L);
        when(persistenceService.saveKnowledgeBaseFromDify(any(), any(), anyLong(), any(), any(), any(), any(), any(), any()))
            .thenReturn(saved);

        service.syncFromDify();

        verify(knowledgeBaseRepository).deleteById(99L);
        verify(storageService).deleteKnowledgeBase("kb/race.md");
        verify(vectorService, never()).vectorizeSegmentsAndStore(any(), any());
    }

    @Test
    @DisplayName("结构守卫：墓碑写入必须加入调用方事务")
    void deletionIntentMustJoinTheCallerTransaction() throws Exception {
        Method method = DifySyncService.class.getMethod("recordDeletionIntent", Long.class, String.class);

        assertFalse(method.isAnnotationPresent(Async.class));
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertTrue(transactional == null || transactional.propagation() != Propagation.REQUIRES_NEW,
            "墓碑写成新事务会脱离知识库删除的事务");
    }

    private DifySyncLogEntity capturedLog() {
        ArgumentCaptor<DifySyncLogEntity> captor = ArgumentCaptor.forClass(DifySyncLogEntity.class);
        verify(syncLogRepository).save(captor.capture());
        return captor.getValue();
    }

    private DifySyncLogEntity tombstone(String difyDocId, DifySyncStatus status) {
        return DifySyncLogEntity.builder()
            .knowledgeBaseId(KB_ID)
            .difyDocumentId(difyDocId)
            .syncDirection(DifySyncDirection.TO_DIFY)
            .syncAction(DifySyncAction.DELETE)
            .syncStatus(status)
            .build();
    }

    private KnowledgeBaseEntity knowledgeBaseWith(String difyDocId) {
        KnowledgeBaseEntity entity = knowledgeBase();
        entity.setDifyDocumentId(difyDocId);
        return entity;
    }

    private KnowledgeBaseEntity knowledgeBase() {
        KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
        entity.setId(KB_ID);
        entity.setUserId(1L);
        entity.setName("Java 并发");
        return entity;
    }
}
