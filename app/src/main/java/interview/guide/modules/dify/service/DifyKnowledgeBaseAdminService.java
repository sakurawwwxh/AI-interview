package interview.guide.modules.dify.service;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.knowledgebase.model.KnowledgeBaseEntity;
import interview.guide.modules.knowledgebase.repository.KnowledgeBaseRepository;
import interview.guide.modules.knowledgebase.service.KnowledgeBaseParseService;
import interview.guide.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dify 管理台中的单知识库操作。
 *
 * <p>这些操作只允许管理员调用。归属变更影响本地 RAG 的用户隔离；Dify 仍使用共享数据集，
 * 因此普通用户不应直接调用 Dify 工作流。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DifyKnowledgeBaseAdminService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final UserRepository userRepository;
    private final KnowledgeBaseParseService parseService;
    private final DifySyncService difySyncService;

    /**
     * 将单个本地知识库重新推送到 Dify。
     * Dify API 调用由 DifySyncService 的异步方法执行；此处只做实体和原文件的安全校验。
     */
    public void syncKnowledgeBaseToDify(Long knowledgeBaseId) {
        KnowledgeBaseEntity entity = getKnowledgeBaseOrThrow(knowledgeBaseId);
        String content = parseService.downloadAndParseContent(entity.getStorageKey(), entity.getOriginalFilename());
        if (content == null || content.isBlank()) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_PARSE_FAILED, "无法从知识库文件中提取文本内容");
        }

        difySyncService.syncToDify(
            entity.getId(),
            entity.getName(),
            entity.getOriginalFilename(),
            entity.getCategory(),
            content,
            entity.getDifyDocumentId()
        );
        log.info("管理员已请求同步本地知识库到 Dify: kbId={}", knowledgeBaseId);
    }

    /** 将本地知识库的访问归属转交给一个已存在的用户。 */
    @Transactional
    public void transferKnowledgeBaseOwner(Long knowledgeBaseId, Long userId) {
        KnowledgeBaseEntity entity = getKnowledgeBaseOrThrow(knowledgeBaseId);
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "目标用户不存在");
        }
        if (userId.equals(entity.getUserId())) {
            return;
        }

        Long previousUserId = entity.getUserId();
        entity.setUserId(userId);
        knowledgeBaseRepository.save(entity);
        log.info("管理员已转交知识库归属: kbId={}, fromUserId={}, toUserId={}",
            knowledgeBaseId, previousUserId, userId);
    }

    private KnowledgeBaseEntity getKnowledgeBaseOrThrow(Long knowledgeBaseId) {
        return knowledgeBaseRepository.findById(knowledgeBaseId)
            .orElseThrow(() -> new BusinessException(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND, "知识库不存在"));
    }
}
