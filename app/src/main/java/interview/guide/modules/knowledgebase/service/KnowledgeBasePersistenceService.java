package interview.guide.modules.knowledgebase.service;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.dify.model.DifySyncStatus;
import interview.guide.modules.knowledgebase.model.KnowledgeBaseEntity;
import interview.guide.modules.knowledgebase.model.VectorStatus;
import interview.guide.modules.knowledgebase.repository.KnowledgeBaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 知识库持久化服务
 * 处理所有需要事务的数据库操作
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBasePersistenceService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;

    /**
     * 处理重复知识库（更新访问计数）
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> handleDuplicateKnowledgeBase(KnowledgeBaseEntity kb, String fileHash) {
        log.info("检测到重复知识库，返回已有记录: kbId={}", kb.getId());
        
        // 更新访问计数（在事务中）
        kb.incrementAccessCount();
        knowledgeBaseRepository.save(kb);
        
        // 重复知识库的向量数据应该已经存在，不需要重新向量化
        return Map.of(
            "knowledgeBase", Map.of(
                "id", kb.getId(),
                "name", kb.getName(),
                "fileSize", kb.getFileSize(),
                "contentLength", 0  // 不再存储content，所以长度为0
            ),
            "storage", Map.of(
                "fileKey", kb.getStorageKey() != null ? kb.getStorageKey() : "",
                "fileUrl", kb.getStorageUrl() != null ? kb.getStorageUrl() : ""
            ),
            "duplicate", true
        );
    }

    /**
     * 保存新知识库元数据到数据库
     */
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeBaseEntity saveKnowledgeBase(MultipartFile file, String name, String category,
                                                  String storageKey, String storageUrl, String fileHash) {
        try {
            KnowledgeBaseEntity kb = new KnowledgeBaseEntity();
            kb.setFileHash(fileHash);
            kb.setName(name != null && !name.trim().isEmpty() ? name : extractNameFromFilename(file.getOriginalFilename()));
            kb.setCategory(category != null && !category.trim().isEmpty() ? category.trim() : null);
            kb.setOriginalFilename(file.getOriginalFilename());
            kb.setFileSize(file.getSize());
            kb.setContentType(file.getContentType());
            kb.setStorageKey(storageKey);
            kb.setStorageUrl(storageUrl);

            KnowledgeBaseEntity saved = knowledgeBaseRepository.save(kb);
            log.info("知识库已保存: id={}, name={}, category={}, hash={}", saved.getId(), saved.getName(), saved.getCategory(), fileHash);
            return saved;
        } catch (Exception e) {
            log.error("保存知识库失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "保存知识库失败");
        }
    }

    /**
     * 从 Dify 拉取的文档保存为本地知识库记录
     *
     * <p>不接受 MultipartFile，用标量参数。Dify 拉取的记录直接设置
     * difyDocumentId / difySyncStatus=SYNCED / difySyncTime，避免回环触发 syncToDify。
     *
     * @param name            知识库名称
     * @param originalFilename 原始文件名
     * @param fileSize        文件大小（字节）
     * @param contentType     MIME 类型
     * @param storageKey      RustFS 存储键
     * @param storageUrl      RustFS 存储URL
     * @param fileHash        文件内容 SHA-256 哈希
     * @param difyDocumentId  Dify 文档 ID
     * @return 保存后的知识库实体
     */
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeBaseEntity saveKnowledgeBaseFromDify(String name, String originalFilename,
                                                          long fileSize, String contentType,
                                                          String storageKey, String storageUrl,
                                                          String fileHash, String difyDocumentId) {
        try {
            KnowledgeBaseEntity kb = new KnowledgeBaseEntity();
            kb.setFileHash(fileHash);
            kb.setName(name != null && !name.trim().isEmpty() ? name : extractNameFromFilename(originalFilename));
            kb.setOriginalFilename(originalFilename);
            kb.setFileSize(fileSize);
            kb.setContentType(contentType);
            kb.setStorageKey(storageKey);
            kb.setStorageUrl(storageUrl);
            // Dify 拉取的记录直接标记为已同步，避免回环
            kb.setDifyDocumentId(difyDocumentId);
            kb.setDifySyncStatus(DifySyncStatus.SYNCED);
            kb.setDifySyncTime(LocalDateTime.now());

            KnowledgeBaseEntity saved = knowledgeBaseRepository.save(kb);
            log.info("Dify 拉取知识库已保存: id={}, name={}, difyDocId={}", saved.getId(), saved.getName(), difyDocumentId);
            return saved;
        } catch (Exception e) {
            log.error("保存 Dify 拉取知识库失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "保存知识库失败");
        }
    }

    /**
     * 更新知识库向量化状态为 PENDING
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateVectorStatusToPending(Long kbId) {
        KnowledgeBaseEntity kb = knowledgeBaseRepository.findById(kbId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "知识库不存在"));
        
        kb.setVectorStatus(VectorStatus.PENDING);
        kb.setVectorError(null);
        knowledgeBaseRepository.save(kb);
        
        log.info("知识库向量化状态已更新为 PENDING: kbId={}", kbId);
    }

    /**
     * 从文件名提取知识库名称（去除扩展名）
     */
    private String extractNameFromFilename(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "未命名知识库";
        }
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0) {
            return filename.substring(0, lastDot);
        }
        return filename;
    }
}

