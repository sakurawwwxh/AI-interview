package ai.interview.modules.resume.service;

import ai.interview.common.exception.BusinessException;
import ai.interview.common.exception.ErrorCode;
import ai.interview.infrastructure.file.FileStorageService;
import ai.interview.modules.interview.service.InterviewPersistenceService;
import ai.interview.modules.resume.model.ResumeEntity;
import ai.interview.modules.target.repository.JobMatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 简历删除服务
 * 处理简历删除的业务逻辑
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeDeleteService {
    
    private final ResumePersistenceService persistenceService;
    private final ResumeVersionService versionService;
    private final JobMatchRepository jobMatchRepository;
    private final InterviewPersistenceService interviewPersistenceService;
    private final FileStorageService storageService;
    
    /**
     * 删除简历
     * 
     * @param id 简历ID
     * @throws ai.interview.common.exception.BusinessException 如果简历不存在
     */
    public void deleteResume(Long id) {
        log.info("收到删除简历请求: id={}", id);
        
        // 获取简历信息（用于删除存储文件）
        ResumeEntity resume = persistenceService.findById(id)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.RESUME_NOT_FOUND));
        
        // 1. 删除存储的文件（FileStorageService 已内置存在性检查）
        try {
            storageService.deleteResume(resume.getStorageKey());
        } catch (Exception e) {
            log.warn("删除存储文件失败，继续删除数据库记录: {}", e.getMessage());
        }
        
        // 2. 删除面试会话（会自动删除面试答案）
        interviewPersistenceService.deleteSessionsByResumeId(id);
        
        // 3. 删除岗位匹配记录、版本与简历
        jobMatchRepository.deleteByResumeId(id);
        versionService.deleteForResume(id);
        persistenceService.deleteResume(id);
        
        log.info("简历删除完成: id={}", id);
    }
}

