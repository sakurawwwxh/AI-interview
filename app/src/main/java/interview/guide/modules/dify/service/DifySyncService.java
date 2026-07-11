package interview.guide.modules.dify.service;

import interview.guide.modules.dify.client.DifyApiClient;
import interview.guide.modules.dify.config.DifyConfig;
import interview.guide.modules.dify.exception.DifySyncException;
import interview.guide.modules.dify.model.*;
import interview.guide.modules.dify.repository.DifySyncLogRepository;
import interview.guide.modules.knowledgebase.model.KnowledgeBaseEntity;
import interview.guide.modules.knowledgebase.repository.KnowledgeBaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * 同步知识库到 Dify（本地 → Dify）
     * 在知识库上传/更新时调用
     *
     * @param entity  知识库实体
     * @param content 知识库内容
     */
    @Async
    @Transactional
    public void syncToDify(KnowledgeBaseEntity entity, String content) {
        // 1. 检查同步是否启用
        if (!config.getSync().isEnabled()) {
            log.debug("Dify 同步已禁用，跳过: kbId={}", entity.getId());
            return;
        }

        log.info("开始同步到 Dify: kbId={}, name={}", entity.getId(), entity.getName());

        try {
            // 2. 准备元数据
            Map<String, Object> metadata = Map.of(
                "knowledgeBaseId", entity.getId().toString(),
                "name", entity.getName(),
                "originalFilename", entity.getOriginalFilename(),
                "category", entity.getCategory() != null ? entity.getCategory() : ""
            );

            // 3. 调用 Dify API 创建/更新文档
            String difyDocumentId;
            if (entity.getDifyDocumentId() != null && !entity.getDifyDocumentId().isBlank()) {
                // 已有 Dify 文档，更新
                difyApiClient.updateDocument(config.getDatasetId(), entity.getDifyDocumentId(), content);
                difyDocumentId = entity.getDifyDocumentId();
                log.info("更新 Dify 文档成功: kbId={}, difyDocId={}", entity.getId(), difyDocumentId);
            } else {
                // 新文档，创建
                difyDocumentId = difyApiClient.createDocument(config.getDatasetId(), content, metadata);
                log.info("创建 Dify 文档成功: kbId={}, difyDocId={}", entity.getId(), difyDocumentId);
            }

            // 4. 更新本地实体
            entity.setDifyDocumentId(difyDocumentId);
            entity.setDifySyncStatus(DifySyncStatus.SYNCED);
            entity.setDifySyncTime(LocalDateTime.now());
            entity.setDifySyncError(null);
            knowledgeBaseRepository.save(entity);

            // 5. 记录同步日志
            saveSyncLog(entity.getId(), difyDocumentId, DifySyncDirection.TO_DIFY,
                DifySyncAction.CREATE, DifySyncStatus.SUCCESS, null);

            log.info("同步到 Dify 完成: kbId={}, difyDocId={}", entity.getId(), difyDocumentId);

        } catch (Exception e) {
            log.warn("同步到 Dify 失败，降级到本地模式: kbId={}, error={}", entity.getId(), e.getMessage());

            // 更新失败状态
            entity.setDifySyncStatus(DifySyncStatus.FAILED);
            entity.setDifySyncError(e.getMessage());
            knowledgeBaseRepository.save(entity);

            // 记录失败日志，根据是否已有 difyDocumentId 区分 CREATE 或 UPDATE
            DifySyncAction failAction = (entity.getDifyDocumentId() != null && !entity.getDifyDocumentId().isBlank())
                ? DifySyncAction.UPDATE : DifySyncAction.CREATE;
            saveSyncLog(entity.getId(), entity.getDifyDocumentId(), DifySyncDirection.TO_DIFY,
                failAction, DifySyncStatus.FAILED, e.getMessage());

            // 不抛出异常，允许继续执行
        }
    }

    /**
     * 从 Dify 同步变更（Dify → 本地）
     * 由定时任务调用
     */
    @Transactional
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
     * 处理远程文档
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
                    // Dify 有更新，但以本地为准，记录日志
                    log.info("Dify 文档有更新，但以本地为准: kbId={}, difyDocId={}",
                        entity.getId(), remoteDoc.getId());
                    saveSyncLog(entity.getId(), remoteDoc.getId(), DifySyncDirection.FROM_DIFY,
                        DifySyncAction.UPDATE, DifySyncStatus.SUCCESS,
                        "Dify 有更新，但以本地为准");
                }
            } else {
                // 本地不存在，记录日志（不自动创建，避免数据不一致）
                log.info("发现 Dify 文档在本地不存在: difyDocId={}, name={}",
                    remoteDoc.getId(), remoteDoc.getName());
                saveSyncLog(null, remoteDoc.getId(), DifySyncDirection.FROM_DIFY,
                    DifySyncAction.CREATE, DifySyncStatus.SUCCESS,
                    "Dify 文档在本地不存在，跳过同步");
            }
        } catch (Exception e) {
            log.error("处理远程文档失败: difyDocId={}, error={}", remoteDoc.getId(), e.getMessage(), e);
            // 失败时记录同步日志，便于排查问题
            saveSyncLog(null, remoteDoc.getId(), DifySyncDirection.FROM_DIFY,
                DifySyncAction.UPDATE, DifySyncStatus.FAILED, e.getMessage());
        }
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
