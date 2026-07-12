package interview.guide.modules.dify.service;

import interview.guide.infrastructure.file.FileHashService;
import interview.guide.infrastructure.file.FileStorageService;
import interview.guide.modules.dify.client.DifyApiClient;
import interview.guide.modules.dify.config.DifyConfig;
import interview.guide.modules.dify.exception.DifySyncException;
import interview.guide.modules.dify.model.*;
import interview.guide.modules.dify.repository.DifySyncLogRepository;
import interview.guide.modules.knowledgebase.listener.VectorizeStreamProducer;
import interview.guide.modules.knowledgebase.model.KnowledgeBaseEntity;
import interview.guide.modules.knowledgebase.model.VectorStatus;
import interview.guide.modules.knowledgebase.repository.KnowledgeBaseRepository;
import interview.guide.modules.knowledgebase.service.KnowledgeBasePersistenceService;
import interview.guide.modules.knowledgebase.service.KnowledgeBaseVectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Dify 同步服务
 * 负责本地知识库与 Dify 平台之间的数据同步
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DifySyncService {

    private final DifyApiClient difyApiClient;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final DifySyncLogRepository syncLogRepository;
    private final DifyConfig config;
    private final FileStorageService storageService;
    private final FileHashService fileHashService;
    private final KnowledgeBasePersistenceService persistenceService;
    private final VectorizeStreamProducer vectorizeStreamProducer;
    private final KnowledgeBaseVectorService vectorService;

    /**
     * 同步知识库到 Dify（本地 → Dify）
     * 在知识库上传/更新时调用
     *
     * <p><b>避免陈旧实体覆盖</b>：此方法是 {@code @Async}，调用方传入的实体在异步执行期间
     * 可能已被向量消费者更新（如 vectorStatus 从 PENDING 变 COMPLETED）。若直接
     * {@code save(旧实体)} 会把 vectorStatus 等字段回退。因此这里只读入实体的
     * 只读字段用于 Dify API 调用，落库时按 ID 重新查最新实体再更新 Dify 字段。
     *
     * @param kbId             知识库 ID
     * @param name             知识库名称
     * @param originalFilename 原始文件名
     * @param category         分类（可为 null）
     * @param content          知识库内容
     * @param existingDifyDocId 已有的 Dify 文档 ID（可为 null）
     */
    @Async
    @Transactional
    public void syncToDify(Long kbId, String name, String originalFilename,
                            String category, String content, String existingDifyDocId) {
        // 1. 检查同步是否启用
        if (!config.getSync().isEnabled()) {
            log.debug("Dify 同步已禁用，跳过: kbId={}", kbId);
            return;
        }

        log.info("开始同步到 Dify: kbId={}, name={}", kbId, name);

        String datasetId = config.getDatasetId();
        try {
            // 2. 调用 Dify API 创建/更新文档
            String difyDocumentId;
            boolean isUpdate = existingDifyDocId != null && !existingDifyDocId.isBlank();
            if (isUpdate) {
                difyApiClient.updateDocument(datasetId, existingDifyDocId, name, content);
                difyDocumentId = existingDifyDocId;
                log.info("更新 Dify 文档成功: kbId={}, difyDocId={}", kbId, difyDocumentId);
            } else {
                Map<String, Object> metadata = Map.of(
                    "knowledgeBaseId", kbId.toString(),
                    "name", name,
                    "originalFilename", originalFilename,
                    "category", category != null ? category : ""
                );
                difyDocumentId = difyApiClient.createDocument(datasetId, content, metadata);
                log.info("创建 Dify 文档成功: kbId={}, difyDocId={}", kbId, difyDocumentId);
            }

            // 3. 仅更新 Dify 相关字段，绝不覆盖 vectorStatus 等其他字段
            knowledgeBaseRepository.findById(kbId).ifPresent(latestEntity -> {
                latestEntity.setDifyDocumentId(difyDocumentId);
                latestEntity.setDifySyncStatus(DifySyncStatus.SYNCED);
                latestEntity.setDifySyncTime(LocalDateTime.now());
                latestEntity.setDifySyncError(null);
                knowledgeBaseRepository.save(latestEntity);
            });

            // 4. 记录同步日志
            saveSyncLog(kbId, difyDocumentId, DifySyncDirection.TO_DIFY,
                isUpdate ? DifySyncAction.UPDATE : DifySyncAction.CREATE,
                DifySyncStatus.SUCCESS, null);

            log.info("同步到 Dify 完成: kbId={}, difyDocId={}", kbId, difyDocumentId);

        } catch (Exception e) {
            log.warn("同步到 Dify 失败，降级到本地模式: kbId={}, error={}", kbId, e.getMessage());
            // 失败时只更新 Dify 字段
            knowledgeBaseRepository.findById(kbId).ifPresent(latestEntity -> {
                latestEntity.setDifySyncStatus(DifySyncStatus.FAILED);
                String errMsg = e.getMessage() != null && e.getMessage().length() > 500
                    ? e.getMessage().substring(0, 500) : e.getMessage();
                latestEntity.setDifySyncError(errMsg);
                knowledgeBaseRepository.save(latestEntity);
            });

            boolean isUpdate = existingDifyDocId != null && !existingDifyDocId.isBlank();
            saveSyncLog(kbId, existingDifyDocId, DifySyncDirection.TO_DIFY,
                isUpdate ? DifySyncAction.UPDATE : DifySyncAction.CREATE,
                DifySyncStatus.FAILED, e.getMessage());

            // 不抛出异常，允许继续执行
        }
    }

    /**
     * 从 Dify 同步变更（Dify → 本地）
     * 由定时任务调用
     *
     * <p>不带 {@code @Transactional}：避免在长事务内调用外部 Dify API 和 DashScope Embedding API
     * 导致数据库连接长时间占用。子方法 {@code createFromDify/updateFromDify} 各步的
     * {@code findById().ifPresent(save)} 因 Repository 的 save 自带事务，粒度更小。
     */
    public void syncFromDify() {
        if (!config.getSync().isEnabled()) {
            log.debug("Dify 同步已禁用，跳过");
            return;
        }

        log.info("开始从 Dify 同步变更");

        try {
            int page = 1;
            int limit = 100;
            boolean hasMore = true;

            while (hasMore) {
                // 1. 获取 Dify 文档列表
                DifyDocumentList remoteDocs = difyApiClient.listDocuments(
                    config.getDatasetId(), page, limit
                );

                if (remoteDocs.getData() == null || remoteDocs.getData().isEmpty()) {
                    break;
                }

                // 2. 处理每个远程文档
                for (DifyDocument remoteDoc : remoteDocs.getData()) {
                    processRemoteDocument(remoteDoc);
                }

                // 3. 检查是否有更多数据
                hasMore = remoteDocs.isHasMore();
                page++;
            }

            log.info("从 Dify 同步完成");

        } catch (Exception e) {
            log.error("从 Dify 同步失败: {}", e.getMessage(), e);
            throw new DifySyncException("从 Dify 同步失败: " + e.getMessage(), e);
        }
    }

    /**
     * 处理远程文档（双向同步）
     *
     * <p>本地不存在时：拉取 Dify 文档内容 -> 存为 .md 到 RustFS -> 落库 -> 向量化
     * <p>本地存在且 Dify 有更新时：拉取最新内容 -> 覆盖存储 -> 重新向量化
     * <p>本地存在且无更新时：跳过
     */
    private void processRemoteDocument(DifyDocument remoteDoc) {
        try {
            // 查找本地对应的文档
            Optional<KnowledgeBaseEntity> localEntity = knowledgeBaseRepository.findByDifyDocumentId(remoteDoc.getId());

            if (localEntity.isPresent()) {
                // 本地存在，检查是否需要更新
                KnowledgeBaseEntity entity = localEntity.get();
                if (remoteDoc.getUpdatedAt() != null &&
                    (entity.getDifySyncTime() == null || remoteDoc.getUpdatedAt().isAfter(entity.getDifySyncTime()))) {
                    // Dify 有更新，拉取最新内容同步到本地
                    updateFromDify(entity, remoteDoc);
                }
            } else {
                // 本地不存在，从 Dify 拉取创建
                createFromDify(remoteDoc);
            }
        } catch (Exception e) {
            log.error("处理远程文档失败: difyDocId={}, error={}", remoteDoc.getId(), e.getMessage(), e);
            saveSyncLog(null, remoteDoc.getId(), DifySyncDirection.FROM_DIFY,
                DifySyncAction.UPDATE, DifySyncStatus.FAILED, e.getMessage());
        }
    }

    /**
     * 从 Dify 拉取文档并在本地创建知识库记录
     */
    private void createFromDify(DifyDocument remoteDoc) {
        String datasetId = config.getDatasetId();
        String difyDocId = remoteDoc.getId();

        log.info("从 Dify 拉取新文档: difyDocId={}, name={}", difyDocId, remoteDoc.getName());

        // 1. 获取 Dify 分段（复用 Dify 的分块结构，不重新分块）
        List<DifySegment> segments = difyApiClient.fetchAllSegments(datasetId, difyDocId);
        if (segments.isEmpty()) {
            log.warn("Dify 文档分段为空，跳过: difyDocId={}", difyDocId);
            saveSyncLog(null, difyDocId, DifySyncDirection.FROM_DIFY,
                DifySyncAction.CREATE, DifySyncStatus.FAILED, "文档分段为空");
            return;
        }

        // 拼接完整内容用于哈希去重和文件存储
        String content = segments.stream()
            .map(DifySegment::getContent)
            .filter(c -> c != null && !c.isBlank())
            .reduce((a, b) -> a + "\n" + b)
            .orElse("");
        if (content.isBlank()) {
            log.warn("Dify 文档内容为空，跳过: difyDocId={}", difyDocId);
            saveSyncLog(null, difyDocId, DifySyncDirection.FROM_DIFY,
                DifySyncAction.CREATE, DifySyncStatus.FAILED, "文档内容为空");
            return;
        }

        // 2. 计算内容哈希并去重
        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
        String fileHash = fileHashService.calculateHash(contentBytes);
        if (knowledgeBaseRepository.findByFileHash(fileHash).isPresent()) {
            log.info("文档内容已存在（哈希重复），跳过创建: hash={}", fileHash);
            saveSyncLog(null, difyDocId, DifySyncDirection.FROM_DIFY,
                DifySyncAction.CREATE, DifySyncStatus.SUCCESS, "内容哈希重复，跳过创建");
            return;
        }

        // 3. 存为 .md 文件到 RustFS
        String filename = remoteDoc.getName();
        if (!filename.endsWith(".md")) {
            filename = filename + ".md";
        }
        String storageKey = storageService.uploadBytes(contentBytes, filename, "text/markdown", "knowledgebases");
        String storageUrl = storageService.getFileUrl(storageKey);

        // 4. 落库（直接标记 Dify 已同步，避免回环）
        KnowledgeBaseEntity savedKb = persistenceService.saveKnowledgeBaseFromDify(
            remoteDoc.getName(), filename, (long) contentBytes.length,
            "text/markdown", storageKey, storageUrl, fileHash, difyDocId);

        // 5. 复用 Dify 分段直接向量化（不重新分块）
        List<String> segmentContents = segments.stream()
            .map(DifySegment::getContent)
            .filter(c -> c != null && !c.isBlank())
            .toList();
        vectorService.vectorizeSegmentsAndStore(savedKb.getId(), segmentContents);

        // 6. 更新向量化状态为 COMPLETED（vectorizeSegmentsAndStore 本身不更新实体）
        knowledgeBaseRepository.findById(savedKb.getId()).ifPresent(latest -> {
            latest.setVectorStatus(VectorStatus.COMPLETED);
            latest.setVectorError(null);
            knowledgeBaseRepository.save(latest);
        });

        log.info("从 Dify 拉取创建成功: kbId={}, name={}, difyDocId={}, segments={}",
            savedKb.getId(), savedKb.getName(), difyDocId, segmentContents.size());
        saveSyncLog(savedKb.getId(), difyDocId, DifySyncDirection.FROM_DIFY,
            DifySyncAction.CREATE, DifySyncStatus.SUCCESS, null);
    }

    /**
     * 从 Dify 拉取更新内容并同步到本地已有文档
     */
    private void updateFromDify(KnowledgeBaseEntity entity, DifyDocument remoteDoc) {
        String datasetId = config.getDatasetId();
        String difyDocId = remoteDoc.getId();

        log.info("从 Dify 拉取文档更新: kbId={}, difyDocId={}", entity.getId(), difyDocId);

        // 1. 获取 Dify 分段（复用 Dify 的分块结构）
        List<DifySegment> segments = difyApiClient.fetchAllSegments(datasetId, difyDocId);
        if (segments.isEmpty()) {
            log.warn("Dify 文档分段为空，跳过更新: difyDocId={}", difyDocId);
            return;
        }

        // 拼接完整内容用于文件存储
        String content = segments.stream()
            .map(DifySegment::getContent)
            .filter(c -> c != null && !c.isBlank())
            .reduce((a, b) -> a + "\n" + b)
            .orElse("");
        if (content.isBlank()) {
            log.warn("Dify 文档内容为空，跳过更新: difyDocId={}", difyDocId);
            return;
        }

        // 2. 先对比哈希，内容没变就跳过，避免重复向量化
        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
        String newFileHash = fileHashService.calculateHash(contentBytes);
        if (entity.getFileHash() != null && entity.getFileHash().equals(newFileHash)) {
            log.info("Dify 文档内容未变化（哈希相同），跳过更新: kbId={}, difyDocId={}", entity.getId(), difyDocId);
            // 仅更新 difySyncTime 防止重复判定
            knowledgeBaseRepository.findById(entity.getId()).ifPresent(latest -> {
                latest.setDifySyncTime(LocalDateTime.now());
                knowledgeBaseRepository.save(latest);
            });
            return;
        }

        // 3. 上传新内容到 RustFS，覆盖前先记录旧 key 稍后删除
        String oldStorageKey = entity.getStorageKey();
        String rawName = remoteDoc.getName();
        final String filename = rawName.endsWith(".md") ? rawName : rawName + ".md";
        String storageKey = storageService.uploadBytes(contentBytes, filename, "text/markdown", "knowledgebases");
        String storageUrl = storageService.getFileUrl(storageKey);

        // 4. 更新实体（含 fileHash，避免去重失效）
        knowledgeBaseRepository.findById(entity.getId()).ifPresent(latest -> {
            latest.setStorageKey(storageKey);
            latest.setStorageUrl(storageUrl);
            latest.setFileSize((long) contentBytes.length);
            latest.setOriginalFilename(filename);
            latest.setFileHash(newFileHash);
            latest.setDifySyncTime(LocalDateTime.now());
            latest.setVectorStatus(VectorStatus.PENDING); // 向量化前先标记 PENDING
            knowledgeBaseRepository.save(latest);
        });

        // 5. 删除旧 S3 对象，避免孤儿
        if (oldStorageKey != null && !oldStorageKey.isBlank() && !oldStorageKey.equals(storageKey)) {
            try {
                storageService.deleteKnowledgeBase(oldStorageKey);
                log.info("已删除旧 S3 文件: {}", oldStorageKey);
            } catch (Exception delErr) {
                log.warn("删除旧 S3 文件失败（孤儿风险）: {} - {}", oldStorageKey, delErr.getMessage());
            }
        }

        // 6. 复用 Dify 分段重新向量化
        List<String> segmentContents = segments.stream()
            .map(DifySegment::getContent)
            .filter(c -> c != null && !c.isBlank())
            .toList();
        vectorService.vectorizeSegmentsAndStore(entity.getId(), segmentContents);

        // 7. 更新向量化状态为 COMPLETED
        knowledgeBaseRepository.findById(entity.getId()).ifPresent(latest -> {
            latest.setVectorStatus(VectorStatus.COMPLETED);
            latest.setVectorError(null);
            knowledgeBaseRepository.save(latest);
        });

        log.info("从 Dify 更新成功: kbId={}, difyDocId={}, segments={}", entity.getId(), difyDocId, segmentContents.size());
        saveSyncLog(entity.getId(), difyDocId, DifySyncDirection.FROM_DIFY,
            DifySyncAction.UPDATE, DifySyncStatus.SUCCESS, null);
    }

    /**
     * 从 Dify 删除文档
     * 在本地知识库删除时调用
     *
     * @param entity 知识库实体
     */
    @Async
    @Transactional
    public void deleteFromDify(KnowledgeBaseEntity entity) {
        if (!config.getSync().isEnabled()) {
            log.debug("Dify 同步已禁用，跳过删除: kbId={}", entity.getId());
            return;
        }

        if (entity.getDifyDocumentId() == null || entity.getDifyDocumentId().isBlank()) {
            log.debug("知识库没有关联 Dify 文档，跳过删除: kbId={}", entity.getId());
            return;
        }

        log.info("从 Dify 删除文档: kbId={}, difyDocId={}", entity.getId(), entity.getDifyDocumentId());

        try {
            difyApiClient.deleteDocument(config.getDatasetId(), entity.getDifyDocumentId());

            saveSyncLog(entity.getId(), entity.getDifyDocumentId(), DifySyncDirection.TO_DIFY,
                DifySyncAction.DELETE, DifySyncStatus.SUCCESS, null);

            log.info("从 Dify 删除文档成功: kbId={}", entity.getId());

        } catch (Exception e) {
            log.warn("从 Dify 删除文档失败: kbId={}, error={}", entity.getId(), e.getMessage());

            saveSyncLog(entity.getId(), entity.getDifyDocumentId(), DifySyncDirection.TO_DIFY,
                DifySyncAction.DELETE, DifySyncStatus.FAILED, e.getMessage());

            // 不抛出异常，允许继续执行
        }
    }

    /**
     * 获取同步状态
     */
    @Transactional(readOnly = true)
    public List<DifySyncStatusDTO> getSyncStatus() {
        return knowledgeBaseRepository.findAll().stream()
            .map(entity -> new DifySyncStatusDTO(
                entity.getId(),
                entity.getName(),
                entity.getDifyDocumentId(),
                entity.getDifySyncStatus(),
                entity.getDifySyncTime(),
                entity.getDifySyncError()
            ))
            .toList();
    }

    /**
     * 获取同步日志
     */
    @Transactional(readOnly = true)
    public List<DifySyncLogEntity> getSyncLogs(Long knowledgeBaseId, int limit) {
        if (knowledgeBaseId != null) {
            return syncLogRepository.findByKnowledgeBaseIdOrderByCreatedAtDesc(
                knowledgeBaseId, PageRequest.of(0, limit));
        } else {
            return syncLogRepository.findAll(PageRequest.of(0, limit))
                .getContent();
        }
    }

    /**
     * 保存同步日志
     */
    private void saveSyncLog(Long knowledgeBaseId, String difyDocumentId,
                             DifySyncDirection direction, DifySyncAction action,
                             DifySyncStatus status, String errorMessage) {
        try {
            DifySyncLogEntity logEntity = DifySyncLogEntity.builder()
                .knowledgeBaseId(knowledgeBaseId)
                .difyDocumentId(difyDocumentId)
                .syncDirection(direction)
                .syncAction(action)
                .syncStatus(status)
                .errorMessage(errorMessage)
                .build();
            syncLogRepository.save(logEntity);
        } catch (Exception e) {
            log.error("保存同步日志失败: {}", e.getMessage());
        }
    }
}
