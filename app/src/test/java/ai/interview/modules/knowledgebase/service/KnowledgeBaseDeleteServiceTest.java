package ai.interview.modules.knowledgebase.service;

import ai.interview.common.exception.BusinessException;
import ai.interview.infrastructure.file.FileStorageService;
import ai.interview.modules.dify.service.DifySyncService;
import ai.interview.modules.knowledgebase.model.KnowledgeBaseEntity;
import ai.interview.modules.knowledgebase.model.RagChatSessionEntity;
import ai.interview.modules.knowledgebase.repository.KnowledgeBaseRepository;
import ai.interview.modules.knowledgebase.repository.RagChatSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 删除知识库时的事务边界。
 *
 * <p>要钉住三件事：拆关联/删向量/删记录确实发生在 begin 与 commit 之间（不是「恰好排在
 * template 调用之后」），Dify 与 S3 清理发生在提交之后，以及数据库侧失败时事务回滚且
 * 不再去删外部资源。
 *
 * <p>这里用真实的 {@link TransactionTemplate} 配一个 mock 的事务管理器，而不是 mock 掉
 * {@code executeWithoutResult} —— 后者会把回调内外的调用混成同一串记录，断言就算恒真也看不出来。
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeBaseDeleteServiceTest {

    private static final long KB_ID = 41L;
    private static final String STORAGE_KEY = "knowledgebases/41.md";

    @Mock private KnowledgeBaseRepository knowledgeBaseRepository;
    @Mock private KnowledgeBaseListService listService;
    @Mock private RagChatSessionRepository sessionRepository;
    @Mock private KnowledgeBaseVectorService vectorService;
    @Mock private FileStorageService storageService;
    @Mock private DifySyncService difySyncService;

    private PlatformTransactionManager transactionManager;
    private KnowledgeBaseDeleteService service;

    @BeforeEach
    void setUp() {
        transactionManager = mock(PlatformTransactionManager.class);
        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new KnowledgeBaseDeleteService(knowledgeBaseRepository, listService, sessionRepository,
            vectorService, storageService, difySyncService, new TransactionTemplate(transactionManager));
    }

    @Test
    @DisplayName("拆关联/删向量/删记录在事务内完成，Dify 与 S3 清理在提交之后")
    void keepsDatabaseWorkInsideTheTransactionAndExternalCleanupAfterCommit() {
        KnowledgeBaseEntity kb = knowledgeBase(KB_ID, STORAGE_KEY);
        when(listService.getKnowledgeBaseEntity(KB_ID)).thenReturn(Optional.of(kb));
        when(sessionRepository.findByKnowledgeBaseIds(List.of(KB_ID))).thenReturn(List.of());

        service.deleteKnowledgeBase(KB_ID);

        InOrder inOrder = inOrder(transactionManager, vectorService, knowledgeBaseRepository,
            difySyncService, storageService);
        inOrder.verify(transactionManager).getTransaction(any());
        inOrder.verify(vectorService).deleteByKnowledgeBaseId(KB_ID);
        inOrder.verify(knowledgeBaseRepository).deleteById(KB_ID);
        inOrder.verify(transactionManager).commit(any());
        inOrder.verify(difySyncService).deleteFromDify(kb);
        inOrder.verify(storageService).deleteKnowledgeBase(STORAGE_KEY);
    }

    @Test
    @DisplayName("S3 清理失败只记日志，数据库侧的删除已经提交")
    void doesNotFailTheRequestWhenStorageCleanupFails() {
        KnowledgeBaseEntity kb = knowledgeBase(KB_ID, STORAGE_KEY);
        when(listService.getKnowledgeBaseEntity(KB_ID)).thenReturn(Optional.of(kb));
        when(sessionRepository.findByKnowledgeBaseIds(List.of(KB_ID))).thenReturn(List.of());
        doThrow(new IllegalStateException("s3 down")).when(storageService).deleteKnowledgeBase(STORAGE_KEY);

        assertDoesNotThrow(() -> service.deleteKnowledgeBase(KB_ID));

        verify(transactionManager).commit(any());
        verify(knowledgeBaseRepository).deleteById(KB_ID);
    }

    @Test
    @DisplayName("Dify 清理提交失败也不影响结果：S3 照常清理，不向调用方抛错")
    void doesNotFailWhenDifyCleanupCannotBeSubmitted() {
        KnowledgeBaseEntity kb = knowledgeBase(KB_ID, STORAGE_KEY);
        when(listService.getKnowledgeBaseEntity(KB_ID)).thenReturn(Optional.of(kb));
        when(sessionRepository.findByKnowledgeBaseIds(List.of(KB_ID))).thenReturn(List.of());
        doThrow(new IllegalStateException("executor rejected"))
            .when(difySyncService).deleteFromDify(kb);

        assertDoesNotThrow(() -> service.deleteKnowledgeBase(KB_ID));

        verify(storageService).deleteKnowledgeBase(STORAGE_KEY);
        verify(knowledgeBaseRepository).deleteById(KB_ID);
    }

    @Test
    @DisplayName("删记录失败：事务回滚，且不再去删 Dify 与 S3")
    void rollsBackAndSkipsExternalCleanupWhenDatabaseWorkFails() {
        KnowledgeBaseEntity kb = knowledgeBase(KB_ID, STORAGE_KEY);
        when(listService.getKnowledgeBaseEntity(KB_ID)).thenReturn(Optional.of(kb));
        when(sessionRepository.findByKnowledgeBaseIds(List.of(KB_ID))).thenReturn(List.of());
        // 真实可达的失败点：外键冲突/死锁都发生在删记录这条语句上。
        // 向量删除本身在 KnowledgeBaseVectorService 里被吞掉，失败会以「事务已中止」
        // 的形式在下一步暴露，最终同样走这条回滚路径。
        doThrow(new IllegalStateException("fk violation"))
            .when(knowledgeBaseRepository).deleteById(KB_ID);

        assertThrows(IllegalStateException.class, () -> service.deleteKnowledgeBase(KB_ID));

        verify(transactionManager).rollback(any());
        verify(transactionManager, never()).commit(any());
        verify(difySyncService, never()).deleteFromDify(any());
        verify(storageService, never()).deleteKnowledgeBase(any());
    }

    @Test
    @DisplayName("会话关联在事务内先拆掉，再删知识库记录")
    void unlinksSessionsBeforeDeletingTheRow() {
        KnowledgeBaseEntity kb = knowledgeBase(KB_ID, STORAGE_KEY);
        RagChatSessionEntity session = new RagChatSessionEntity();
        session.setId(9L);
        session.getKnowledgeBases().add(kb);
        when(listService.getKnowledgeBaseEntity(KB_ID)).thenReturn(Optional.of(kb));
        when(sessionRepository.findByKnowledgeBaseIds(List.of(KB_ID))).thenReturn(List.of(session));

        service.deleteKnowledgeBase(KB_ID);

        assertTrue(session.getKnowledgeBases().isEmpty());
        InOrder inOrder = inOrder(transactionManager, sessionRepository, knowledgeBaseRepository);
        inOrder.verify(transactionManager).getTransaction(any());
        inOrder.verify(sessionRepository).save(session);
        inOrder.verify(knowledgeBaseRepository).deleteById(KB_ID);
        inOrder.verify(transactionManager).commit(any());
    }

    @Test
    @DisplayName("不存在或不属于当前用户时直接拒绝，不碰任何外部资源")
    void rejectsUnknownOrForeignKnowledgeBase() {
        when(listService.getKnowledgeBaseEntity(KB_ID)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> service.deleteKnowledgeBase(KB_ID));

        verify(vectorService, never()).deleteByKnowledgeBaseId(any());
        verify(difySyncService, never()).deleteFromDify(any());
        verify(storageService, never()).deleteKnowledgeBase(any());
        verify(knowledgeBaseRepository, never()).deleteById(any());
        verify(transactionManager, never()).getTransaction(any());
    }

    private KnowledgeBaseEntity knowledgeBase(Long id, String storageKey) {
        KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
        entity.setId(id);
        entity.setStorageKey(storageKey);
        return entity;
    }
}
