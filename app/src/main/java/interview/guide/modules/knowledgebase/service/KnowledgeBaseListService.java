package interview.guide.modules.knowledgebase.service;

import interview.guide.modules.knowledgebase.model.KnowledgeBaseEntity;
import interview.guide.modules.knowledgebase.model.KnowledgeBaseListItemDTO;
import interview.guide.modules.knowledgebase.repository.KnowledgeBaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 知识库查询服务
 * 负责知识库列表和详情的查询
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseListService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;

    /**
     * 获取所有知识库列表
     */
    public List<KnowledgeBaseListItemDTO> listKnowledgeBases() {
        return knowledgeBaseRepository.findAllByOrderByUploadedAtDesc().stream()
            .map(KnowledgeBaseListItemDTO::fromEntity)
            .collect(Collectors.toList());
    }

    /**
     * 根据ID获取知识库详情
     */
    public Optional<KnowledgeBaseListItemDTO> getKnowledgeBase(Long id) {
        return knowledgeBaseRepository.findById(id)
            .map(KnowledgeBaseListItemDTO::fromEntity);
    }

    /**
     * 根据ID获取知识库实体（用于删除等操作）
     */
    public Optional<KnowledgeBaseEntity> getKnowledgeBaseEntity(Long id) {
        return knowledgeBaseRepository.findById(id);
    }

    /**
     * 根据ID列表获取知识库名称列表
     */
    public List<String> getKnowledgeBaseNames(List<Long> ids) {
        return ids.stream()
            .map(id -> knowledgeBaseRepository.findById(id)
                .map(KnowledgeBaseEntity::getName)
                .orElse("未知知识库"))
            .collect(Collectors.toList());
    }

    // ========== 分类管理 ==========

    /**
     * 获取所有分类
     */
    public List<String> getAllCategories() {
        return knowledgeBaseRepository.findAllCategories();
    }

    /**
     * 根据分类获取知识库列表
     */
    public List<KnowledgeBaseListItemDTO> listByCategory(String category) {
        List<KnowledgeBaseEntity> entities;
        if (category == null || category.isBlank()) {
            entities = knowledgeBaseRepository.findByCategoryIsNullOrderByUploadedAtDesc();
        } else {
            entities = knowledgeBaseRepository.findByCategoryOrderByUploadedAtDesc(category);
        }
        return entities.stream()
            .map(KnowledgeBaseListItemDTO::fromEntity)
            .collect(Collectors.toList());
    }

    /**
     * 更新知识库分类
     */
    @Transactional
    public void updateCategory(Long id, String category) {
        KnowledgeBaseEntity entity = knowledgeBaseRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("知识库不存在"));
        entity.setCategory(category != null && !category.isBlank() ? category : null);
        knowledgeBaseRepository.save(entity);
        log.info("更新知识库分类: id={}, category={}", id, category);
    }

    // ========== 搜索功能 ==========

    /**
     * 按关键词搜索知识库
     */
    public List<KnowledgeBaseListItemDTO> search(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return listKnowledgeBases();
        }
        return knowledgeBaseRepository.searchByKeyword(keyword.trim()).stream()
            .map(KnowledgeBaseListItemDTO::fromEntity)
            .collect(Collectors.toList());
    }

    // ========== 排序功能 ==========

    /**
     * 按指定字段排序获取知识库列表
     */
    public List<KnowledgeBaseListItemDTO> listSorted(String sortBy) {
        List<KnowledgeBaseEntity> entities;
        switch (sortBy != null ? sortBy.toLowerCase() : "time") {
            case "size" -> entities = knowledgeBaseRepository.findAllByOrderByFileSizeDesc();
            case "access" -> entities = knowledgeBaseRepository.findAllByOrderByAccessCountDesc();
            case "question" -> entities = knowledgeBaseRepository.findAllByOrderByQuestionCountDesc();
            default -> entities = knowledgeBaseRepository.findAllByOrderByUploadedAtDesc();
        }
        return entities.stream()
            .map(KnowledgeBaseListItemDTO::fromEntity)
            .collect(Collectors.toList());
    }
}

