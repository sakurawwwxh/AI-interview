package ai.interview.modules.dify.service;

import ai.interview.infrastructure.file.FileHashService;
import ai.interview.infrastructure.file.FileStorageService;
import ai.interview.modules.dify.client.DifyApiClient;
import ai.interview.modules.dify.config.DifyConfig;
import ai.interview.modules.dify.exception.DifySyncException;
import ai.interview.modules.dify.model.*;
import ai.interview.modules.dify.repository.DifySyncLogRepository;
import ai.interview.modules.knowledgebase.listener.VectorizeStreamProducer;
import ai.interview.modules.knowledgebase.model.KnowledgeBaseEntity;
import ai.interview.modules.knowledgebase.model.VectorStatus;
import ai.interview.modules.knowledgebase.repository.KnowledgeBaseRepository;
import ai.interview.modules.knowledgebase.service.KnowledgeBasePersistenceService;
import ai.interview.modules.knowledgebase.service.KnowledgeBaseVectorService;
import ai.interview.modules.user.model.UserEntity;
import ai.interview.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private final UserRepository userRepository;

    /**
     * 同步知识库到 Dify（本地 → Dify）
     * 在知识库上传/更新时调用
     *
     * <p><b>避免陈旧实体覆盖</b>：此方法是 {@code @Async}，调用方传入的实体在异步执行期间
     * 可能已被向量消费者更新（如 vectorStatus 从 PENDING 变 COMPLETED）。若直接
     * {@code save(旧实体)} 会把 vectorStatus 等字段回退。因此这里只读入实体的
     * 只读字段用于 Dify API 调用，落库时按 ID 重新查最新实体再更新 Dify 字段。
     *
     * <p>刻意不加 {@code @Transactional}：方法主体是 Dify 的 HTTP 调用（创建或更新文档），
     * 一个事务会让数据库连接在整个网络往返期间被占用。落库只有两步互相独立的写
     * （更新实体、写同步日志），各自走 Repository 自带的事务即可。
     *
     * @param kbId             知识库 ID
     * @param name             知识库名称
     * @param originalFilename 原始文件名
     * @param category         分类（可为 null）
     * @param content          知识库内容
     * @param existingDifyDocId 已有的 Dify 文档 ID（可为 null）
     */
    @Async
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
            Optional<KnowledgeBaseEntity> latestEntity = knowledgeBaseRepository.findById(kbId);
            if (latestEntity.isEmpty()) {
                // 本地在同步期间被删了（此时本地还没有 docId，删除方写不出墓碑）：
                // 远端刚建好的这篇必须登记删除意图，否则反向同步会把它当作新文档拉回来
                recordDeletionIntent(kbId, difyDocumentId);
                log.info("知识库已被删除，登记 Dify 删除墓碑: kbId={}, difyDocId={}", kbId, difyDocumentId);
                return;
            }
            latestEntity.get().setDifyDocumentId(difyDocumentId);
            latestEntity.get().setDifySyncStatus(DifySyncStatus.SYNCED);
            latestEntity.get().setDifySyncTime(LocalDateTime.now());
            latestEntity.get().setDifySyncError(null);
            knowledgeBaseRepository.save(latestEntity.get());

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

        // 本地主动删过：这是删除墓碑，不是待拉取的新文档。否则下次同步会把删掉的知识库重新拉回来。
        if (syncLogRepository.existsByDifyDocumentIdAndSyncAction(difyDocId, DifySyncAction.DELETE)) {
            log.info("本地已删除该文档，跳过回拉: difyDocId={}", difyDocId);
            return;
        }

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

        // 2. 计算内容哈希并去重（Dify 同步是后台任务，检查所有用户）
        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
        String fileHash = fileHashService.calculateHash(contentBytes);
        if (knowledgeBaseRepository.findByDifyDocumentId(difyDocId).isPresent()) {
            log.info("文档已存在（Dify docId 重复），跳过创建: difyDocId={}", difyDocId);
            saveSyncLog(null, difyDocId, DifySyncDirection.FROM_DIFY,
                DifySyncAction.CREATE, DifySyncStatus.SUCCESS, "Dify docId 重复，跳过创建");
            return;
        }

        // 3. 存为 .md 文件到 RustFS
        String filename = remoteDoc.getName();
        if (!filename.endsWith(".md")) {
            filename = filename + ".md";
        }
        String storageKey = storageService.uploadBytes(contentBytes, filename, "text/markdown", "knowledgebases");
        String storageUrl = storageService.getFileUrl(storageKey);

        // 4. 落库（直接标记 Dify 已同步，避免回环）-- 后台同步无用户上下文，归到系统用户
        KnowledgeBaseEntity savedKb = persistenceService.saveKnowledgeBaseFromDify(
            remoteDoc.getName(), filename, (long) contentBytes.length,
            "text/markdown", storageKey, storageUrl, fileHash, difyDocId, 1L);

        // 上面的检查到这次落库之间有秒级的网络往返（拉分段、传对象存储），期间知识库可能已被删除。
        // 再确认一次：命中墓碑就撤掉刚建的记录与文件，否则这条会被永久保留。
        if (syncLogRepository.existsByDifyDocumentIdAndSyncAction(difyDocId, DifySyncAction.DELETE)) {
            knowledgeBaseRepository.deleteById(savedKb.getId());
            try {
                storageService.deleteKnowledgeBase(storageKey);
            } catch (Exception cleanupError) {
                log.warn("撤销复活记录时删除对象失败: storageKey={}, error={}", storageKey, cleanupError.getMessage());
            }
            log.info("落库期间知识库被删除，已撤销回拉: difyDocId={}, kbId={}", difyDocId, savedKb.getId());
            return;
        }

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
     * <p>同样不加 {@code @Transactional}：删文档是 Dify 的 HTTP 调用，事务没有要保护的
     * 多步写入（只有一条同步日志），没必要占着连接等网络。
     *
     * @param entity 知识库实体
     */
    @Async
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

        String difyDocId = entity.getDifyDocumentId();
        Optional<DifySyncLogEntity> tombstone = syncLogRepository
            .findFirstByDifyDocumentIdAndSyncActionOrderByCreatedAtDesc(difyDocId, DifySyncAction.DELETE);

        try {
            difyApiClient.deleteDocument(config.getDatasetId(), difyDocId);

            markDeletionOutcome(tombstone, entity.getId(), difyDocId, DifySyncStatus.SUCCESS, null);

            log.info("从 Dify 删除文档成功: kbId={}", entity.getId());

        } catch (Exception e) {
            log.warn("从 Dify 删除文档失败，留待定时任务重试: kbId={}, error={}", entity.getId(), e.getMessage());

            markDeletionOutcome(tombstone, entity.getId(), difyDocId, DifySyncStatus.FAILED, e.getMessage());

            // 不抛出异常，允许继续执行
        }
    }

    /**
     * 记录「本地已删除、需要从 Dify 删除」的意图（墓碑）。
     *
     * <p>必须与知识库删除写在同一个事务里提交：事务一旦提交，即使进程立刻退出、
     * 或这次 Dify 调用失败，墓碑都还在——定时任务会继续重试删除，
     * 反向同步也不会把这篇文档当作新文档重新拉回本地。
     */
    public void recordDeletionIntent(Long kbId, String difyDocId) {
        if (difyDocId == null || difyDocId.isBlank()) {
            return;
        }
        syncLogRepository.save(DifySyncLogEntity.builder()
            .knowledgeBaseId(kbId)
            .difyDocumentId(difyDocId)
            .syncDirection(DifySyncDirection.TO_DIFY)
            .syncAction(DifySyncAction.DELETE)
            .syncStatus(DifySyncStatus.PENDING)
            .build());
    }

    /**
     * 重试未完成的 Dify 删除，由定时任务在拉取变更前调用。
     *
     * <p>只重试最近 7 天的墓碑：更早的失败通常意味着配置问题（key 失效、dataset 被删），
     * 每 10 分钟重试一次只会刷日志，留给人工处理。
     */
    public int retryPendingDeletions() {
        if (!config.getSync().isEnabled()) {
            return 0;
        }

        List<DifySyncLogEntity> tombstones = syncLogRepository.findRetryable(
            DifySyncAction.DELETE,
            List.of(DifySyncStatus.PENDING, DifySyncStatus.FAILED),
            LocalDateTime.now().minusDays(7),
            PageRequest.of(0, 20));

        int cleared = 0;
        for (DifySyncLogEntity tombstone : tombstones) {
            String difyDocId = tombstone.getDifyDocumentId();
            try {
                difyApiClient.deleteDocument(config.getDatasetId(), difyDocId);
                markDeletionOutcome(Optional.of(tombstone), tombstone.getKnowledgeBaseId(), difyDocId,
                    DifySyncStatus.SUCCESS, null);
                cleared++;
            } catch (Exception e) {
                if (isDocumentNotFound(e)) {
                    // 远端已经没有这篇文档，删除目标已达成
                    markDeletionOutcome(Optional.of(tombstone), tombstone.getKnowledgeBaseId(), difyDocId,
                        DifySyncStatus.SUCCESS, null);
                    cleared++;
                    log.info("Dify 文档已不存在，删除墓碑标记完成: difyDocId={}", difyDocId);
                } else {
                    markDeletionOutcome(Optional.of(tombstone), tombstone.getKnowledgeBaseId(), difyDocId,
                        DifySyncStatus.FAILED, e.getMessage());
                    log.warn("重试删除 Dify 文档失败: difyDocId={}, error={}", difyDocId, e.getMessage());
                }
            }
        }
        if (!tombstones.isEmpty()) {
            log.info("Dify 删除重试：候选 {} 条，完成 {} 条", tombstones.size(), cleared);
        }

        // 超出重试窗口仍未完成的墓碑不会再被自动处理：远端文档会残留在 Dify，
        // 而墓碑又永久阻断回拉，必须让人看得到
        LocalDateTime retryWindowStart = LocalDateTime.now().minusDays(7);
        long abandoned = syncLogRepository.countBySyncActionAndSyncStatusInAndCreatedAtBefore(
            DifySyncAction.DELETE,
            List.of(DifySyncStatus.PENDING, DifySyncStatus.FAILED),
            retryWindowStart);
        if (abandoned > 0) {
            log.error("有 {} 条 Dify 删除墓碑超过 7 天仍未完成，远端文档可能残留，需要人工处理", abandoned);
        }

        // 已完成的墓碑保留 30 天用于拦回拉，之后清掉避免无限增长
        long pruned = syncLogRepository.deleteBySyncActionAndSyncStatusAndCreatedAtBefore(
            DifySyncAction.DELETE, DifySyncStatus.SUCCESS, LocalDateTime.now().minusDays(30));
        if (pruned > 0) {
            log.info("清理了 {} 条超过 30 天且已完成的 Dify 删除墓碑", pruned);
        }
        return cleared;
    }

    /** 墓碑存在时更新它，否则退回「插一条日志」的旧行为（例如没有墓碑的历史调用方）。 */
    private void markDeletionOutcome(Optional<DifySyncLogEntity> tombstone, Long kbId, String difyDocId,
                                     DifySyncStatus status, String error) {
        if (tombstone.isPresent()) {
            DifySyncLogEntity logEntity = tombstone.get();
            logEntity.setSyncStatus(status);
            logEntity.setErrorMessage(truncateError(error));
            syncLogRepository.save(logEntity);
            return;
        }
        saveSyncLog(kbId, difyDocId, DifySyncDirection.TO_DIFY, DifySyncAction.DELETE, status, error);
    }

    private static String truncateError(String error) {
        if (error == null || error.length() <= 500) {
            return error;
        }
        return error.substring(0, 500);
    }

    private static boolean isDocumentNotFound(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof RestClientResponseException response
                && response.getStatusCode().value() == 404) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取同步状态
     */
    @Transactional(readOnly = true)
    public List<DifySyncStatusDTO> getSyncStatus() {
        List<KnowledgeBaseEntity> knowledgeBases = knowledgeBaseRepository.findAll();
        Set<Long> ownerIds = knowledgeBases.stream()
            .map(KnowledgeBaseEntity::getUserId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Long, String> ownerNames = userRepository.findAllById(ownerIds).stream()
            .collect(Collectors.toMap(UserEntity::getId, UserEntity::getUsername, (left, right) -> left));

        return knowledgeBases.stream()
            .map(entity -> new DifySyncStatusDTO(
                entity.getId(),
                entity.getName(),
                entity.getUserId(),
                ownerNames.get(entity.getUserId()),
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
