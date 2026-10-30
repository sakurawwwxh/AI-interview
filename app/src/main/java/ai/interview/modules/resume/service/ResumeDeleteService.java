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
import org.springframework.transaction.support.TransactionTemplate;

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
    private final TransactionTemplate transactionTemplate;
    
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
        
        String storageKey = resume.getStorageKey();

        // 1. 数据库侧放到一个短事务里：会话、岗位匹配、版本、简历要么全删要么全不删。
        //    此前没有任何事务，删会话自带的事务会先提交，后续任一步失败就留下
        //    「简历还在、面试记录已经没了」的半删除状态。
        transactionTemplate.executeWithoutResult(status -> {
            interviewPersistenceService.deleteSessionsByResumeId(id);
            jobMatchRepository.deleteByResumeId(id);
            versionService.deleteForResume(id);
            persistenceService.deleteResume(id);
        });

        // 2. 提交后再删对象存储：反过来事务回滚时文件已经没了，记录会指向空文件
        try {
            storageService.deleteResume(storageKey);
        } catch (Exception e) {
            log.warn("删除存储文件失败，孤儿文件需手工清理: id={}, error={}", id, e.getMessage());
        }

        log.info("简历删除完成: id={}", id);
    }
}

