package interview.guide.modules.knowledgebase.repository;

import interview.guide.modules.dify.model.DifySyncStatus;
import interview.guide.modules.knowledgebase.model.KnowledgeBaseEntity;
import interview.guide.modules.knowledgebase.model.VectorStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 知识库 Repository（按用户隔离）
 */
@Repository
public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBaseEntity, Long> {

    /**
     * 根据文件哈希和用户 ID 查找知识库（用于去重）
     */
    Optional<KnowledgeBaseEntity> findByFileHashAndUserId(String fileHash, Long userId);

    /**
     * 根据 Dify 文档 ID 查找知识库
     */
    Optional<KnowledgeBaseEntity> findByDifyDocumentId(String difyDocumentId);

    /**
     * 检查文件哈希在指定用户下是否存在
     */
    boolean existsByFileHashAndUserId(String fileHash, Long userId);

    /**
     * 按上传时间倒序查找用户的所有知识库
     */
    List<KnowledgeBaseEntity> findAllByUserIdOrderByUploadedAtDesc(Long userId);

    /**
     * 获取用户所有不同的分类
     */
    @Query("SELECT DISTINCT k.category FROM KnowledgeBaseEntity k WHERE k.userId = :userId AND k.category IS NOT NULL ORDER BY k.category")
    List<String> findAllCategoriesByUserId(@Param("userId") Long userId);

    /**
     * 根据分类和用户 ID 查找知识库
     */
    List<KnowledgeBaseEntity> findByUserIdAndCategoryOrderByUploadedAtDesc(Long userId, String category);

    /**
     * 查找用户未分类的知识库
     */
    List<KnowledgeBaseEntity> findByUserIdAndCategoryIsNullOrderByUploadedAtDesc(Long userId);

    /**
     * 按名称或文件名模糊搜索（按用户隔离）
     */
    @Query("SELECT k FROM KnowledgeBaseEntity k WHERE k.userId = :userId AND (LOWER(k.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(k.originalFilename) LIKE LOWER(CONCAT('%', :keyword, '%'))) ORDER BY k.uploadedAt DESC")
    List<KnowledgeBaseEntity> searchByKeywordAndUserId(@Param("keyword") String keyword, @Param("userId") Long userId);

    /**
     * 按文件大小排序（按用户）
     */
    List<KnowledgeBaseEntity> findAllByUserIdOrderByFileSizeDesc(Long userId);

    /**
     * 按访问次数排序（按用户）
     */
    List<KnowledgeBaseEntity> findAllByUserIdOrderByAccessCountDesc(Long userId);

    /**
     * 按提问次数排序（按用户）
     */
    List<KnowledgeBaseEntity> findAllByUserIdOrderByQuestionCountDesc(Long userId);

    /**
     * 按 ID 和用户 ID 查找（鉴权）
     */
    Optional<KnowledgeBaseEntity> findByIdAndUserId(Long id, Long userId);

    List<KnowledgeBaseEntity> findAllByIdInAndUserId(List<Long> ids, Long userId);

    // ==================== 批量更新 ====================

    /**
     * 批量增加知识库提问计数
     */
    @Modifying
    @Query("UPDATE KnowledgeBaseEntity k SET k.questionCount = k.questionCount + 1 WHERE k.id IN :ids")
    int incrementQuestionCountBatch(@Param("ids") List<Long> ids);

    // ==================== 统计查询（按用户） ====================

    /**
     * 统计用户总提问次数
     */
    @Query("SELECT COALESCE(SUM(k.questionCount), 0) FROM KnowledgeBaseEntity k WHERE k.userId = :userId")
    long sumQuestionCountByUserId(@Param("userId") Long userId);

    /**
     * 统计用户总访问次数
     */
    @Query("SELECT COALESCE(SUM(k.accessCount), 0) FROM KnowledgeBaseEntity k WHERE k.userId = :userId")
    long sumAccessCountByUserId(@Param("userId") Long userId);

    /**
     * 按向量化状态和用户统计数量
     */
    long countByVectorStatusAndUserId(VectorStatus vectorStatus, Long userId);

    /**
     * 按向量化状态和用户查找知识库（按上传时间倒序）
     */
    List<KnowledgeBaseEntity> findByVectorStatusAndUserIdOrderByUploadedAtDesc(VectorStatus vectorStatus, Long userId);

    // ==================== Dify 同步相关查询（全局，不按用户） ====================

    /**
     * 根据 Dify 同步状态查找知识库（后台同步用）
     */
    List<KnowledgeBaseEntity> findByDifySyncStatus(DifySyncStatus difySyncStatus);

    /**
     * 统计指定 Dify 同步状态的数量
     */
    long countByDifySyncStatus(DifySyncStatus difySyncStatus);
}
