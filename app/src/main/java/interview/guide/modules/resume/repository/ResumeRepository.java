package interview.guide.modules.resume.repository;

import interview.guide.modules.resume.model.ResumeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 简历 Repository（按用户隔离）
 */
@Repository
public interface ResumeRepository extends JpaRepository<ResumeEntity, Long> {

    /**
     * 根据文件哈希和用户 ID 查找简历（用于去重）
     */
    Optional<ResumeEntity> findByFileHashAndUserId(String fileHash, Long userId);

    /**
     * 检查文件哈希在指定用户下是否存在
     */
    boolean existsByFileHashAndUserId(String fileHash, Long userId);

    /**
     * 查找用户的所有简历（按上传时间倒序）
     */
    List<ResumeEntity> findAllByUserIdOrderByUploadedAtDesc(Long userId);

    /**
     * 按 ID 和用户 ID 查找（鉴权）
     */
    Optional<ResumeEntity> findByIdAndUserId(Long id, Long userId);

    List<ResumeEntity> findAllByUserIdIsNull();
}
