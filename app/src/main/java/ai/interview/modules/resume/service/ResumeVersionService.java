package ai.interview.modules.resume.service;

import ai.interview.common.exception.BusinessException;
import ai.interview.common.exception.ErrorCode;
import ai.interview.modules.resume.model.*;
import ai.interview.modules.resume.repository.ResumeRepository;
import ai.interview.modules.resume.repository.ResumeVersionRepository;
import ai.interview.modules.user.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ResumeVersionService {
    private final ResumeRepository resumeRepository;
    private final ResumeVersionRepository versionRepository;

    public List<ResumeVersionDTO> list(Long resumeId) {
        ResumeEntity resume = ownedResume(resumeId);
        Long userId = UserContext.getCurrentUserIdOrThrow();
        List<ResumeVersionEntity> versions = versionRepository.findByResumeIdAndUserIdOrderByVersionNumberDesc(resumeId, userId);
        if (versions.isEmpty()) {
            // Legacy uploads remain readable as an implicit, immutable original until their first edit.
            return List.of(new ResumeVersionDTO(null, 1, "原始上传", resume.getResumeText(), "UPLOAD", resume.getUploadedAt(), true));
        }
        return versions.stream().map(version -> toDto(version, version.getContent().equals(resume.getResumeText()))).toList();
    }

    @Transactional
    public ResumeVersionDTO update(Long resumeId, UpdateResumeContentRequest request) {
        ResumeEntity resume = ownedResumeForUpdate(resumeId);
        Long userId = UserContext.getCurrentUserIdOrThrow();
        List<ResumeVersionEntity> versions = versionRepository.findByResumeIdAndUserIdOrderByVersionNumberDesc(resumeId, userId);
        if (versions.isEmpty()) {
            saveSnapshot(resume, userId, 1, "原始上传", resume.getResumeText(), "UPLOAD");
        }
        int next = (int) versionRepository.countByResumeIdAndUserId(resumeId, userId) + 1;
        ResumeVersionEntity version = saveSnapshot(resume, userId, next, request.title().trim(), request.content().trim(), "EDIT");
        resume.setResumeText(version.getContent());
        resumeRepository.save(resume);
        return toDto(version, true);
    }

    @Transactional
    public ResumeVersionDTO restore(Long resumeId, Long versionId) {
        ResumeEntity resume = ownedResumeForUpdate(resumeId);
        Long userId = UserContext.getCurrentUserIdOrThrow();
        ResumeVersionEntity source = versionRepository.findByIdAndResumeIdAndUserId(versionId, resumeId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESUME_NOT_FOUND));
        int next = (int) versionRepository.countByResumeIdAndUserId(resumeId, userId) + 1;
        ResumeVersionEntity restored = saveSnapshot(resume, userId, next, "恢复：" + source.getTitle(), source.getContent(), "RESTORE");
        resume.setResumeText(restored.getContent());
        resumeRepository.save(resume);
        return toDto(restored, true);
    }

    @Transactional
    public void deleteForResume(Long resumeId) { versionRepository.deleteByResumeId(resumeId); }

    /** 写路径：锁住简历行再把「已有版本数 + 1」作为新版本号，避免并发撞唯一索引。 */
    private ResumeEntity ownedResumeForUpdate(Long id) {
        Long userId = UserContext.getCurrentUserIdOrThrow();
        return resumeRepository.findByIdAndUserIdForUpdate(id, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESUME_NOT_FOUND));
    }

    private ResumeEntity ownedResume(Long id) {
        Long userId = UserContext.getCurrentUserIdOrThrow();
        return resumeRepository.findByIdAndUserId(id, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESUME_NOT_FOUND));
    }

    private ResumeVersionEntity saveSnapshot(ResumeEntity resume, Long userId, int number, String title, String content, String source) {
        ResumeVersionEntity version = new ResumeVersionEntity();
        version.setResumeId(resume.getId()); version.setUserId(userId); version.setVersionNumber(number);
        version.setTitle(title); version.setContent(content == null ? "" : content); version.setSource(source);
        return versionRepository.save(version);
    }

    private ResumeVersionDTO toDto(ResumeVersionEntity item, boolean current) {
        return new ResumeVersionDTO(item.getId(), item.getVersionNumber(), item.getTitle(), item.getContent(), item.getSource(), item.getCreatedAt(), current);
    }
}
