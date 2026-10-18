package ai.interview.modules.knowledgebase.service;

import ai.interview.common.exception.BusinessException;
import ai.interview.common.exception.ErrorCode;
import ai.interview.infrastructure.file.FileStorageService;
import ai.interview.modules.dify.service.DifySyncService;
import ai.interview.modules.knowledgebase.model.KnowledgeBaseEntity;
import ai.interview.modules.knowledgebase.model.RagChatSessionEntity;
import ai.interview.modules.knowledgebase.repository.KnowledgeBaseRepository;
import ai.interview.modules.knowledgebase.repository.RagChatSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * 知识库删除服务
 * 负责知识库的删除操作
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseDeleteService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KnowledgeBaseListService listService;
    private final RagChatSessionRepository sessionRepository;
    private final KnowledgeBaseVectorService vectorService;
    private final FileStorageService storageService;
    private final DifySyncService difySyncService;
    private final TransactionTemplate transactionTemplate;

    /**
     * 删除知识库
     * 包括：RAG会话关联、向量数据、RustFS文件、数据库记录
     *
     * <p>整个方法刻意不包在 {@code @Transactional} 里：Dify 删除和 S3 删除都是外部调用，
     * 事务开着会让数据库连接在整个网络往返期间被占用；它们失败时本来也只记日志（尽力而为）。
     *
     * <p>数据库侧的三步——拆会话关联、删向量、删记录——必须在同一个短事务里：向量表与业务表
     * 共用同一个 DataSource，分开提交的话，一旦删记录失败（外键冲突、死锁），记录还在、
     * 向量却已经没了，RAG 查询会永久召不回内容。
     *
     * <p>外部清理放在提交之后：反过来的话，事务回滚时 S3 文件已经删掉，本地记录就成了
     * 一个指向空文件的残骸；放在提交之后最坏也只留下无人引用的孤儿对象。
     */
    public void deleteKnowledgeBase(Long id) {
        // 1. 获取知识库信息（归属校验）
        KnowledgeBaseEntity kb = listService.getKnowledgeBaseEntity(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND, "知识库不存在或无权访问"));
        String storageKey = kb.getStorageKey();

        // 2. 数据库侧：拆会话关联 → 删向量 → 删记录（短事务，要么全成要么全不成）
        transactionTemplate.executeWithoutResult(status -> {
            removeSessionLinks(id);
            // 向量删除未抛异常时事务照常提交；它自身在失败时只记日志，保留原有降级语义
            vectorService.deleteByKnowledgeBaseId(id);
            knowledgeBaseRepository.deleteById(id);
        });

        // 3. 提交后再清理外部资源；此刻记录已经删掉，清理失败不应让用户看到「删除失败」
        try {
            difySyncService.deleteFromDify(kb);
        } catch (Exception e) {
            log.warn("提交 Dify 删除失败，需手工清理: kbId={}, difyDocId={}, error={}",
                id, kb.getDifyDocumentId(), e.getMessage());
        }
        try {
            storageService.deleteKnowledgeBase(storageKey);
        } catch (Exception e) {
            log.warn("删除RustFS文件失败，孤儿文件需手工清理: kbId={}, storageKey={}, error={}",
                id, storageKey, e.getMessage());
        }

        log.info("知识库已删除: id={}", id);
    }

    private void removeSessionLinks(Long id) {
        // 必须先删除关联，否则外键约束会阻止删除
        List<RagChatSessionEntity> sessions = sessionRepository.findByKnowledgeBaseIds(List.of(id));
        for (RagChatSessionEntity session : sessions) {
            session.getKnowledgeBases().removeIf(kbEntity -> kbEntity.getId().equals(id));
            sessionRepository.save(session);
            log.debug("已从会话中移除知识库关联: sessionId={}, kbId={}", session.getId(), id);
        }
        if (!sessions.isEmpty()) {
            log.info("已从 {} 个会话中移除知识库关联: kbId={}", sessions.size(), id);
        }
    }
}
