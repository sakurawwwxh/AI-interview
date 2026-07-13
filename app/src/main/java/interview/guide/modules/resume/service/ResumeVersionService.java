package interview.guide.modules.resume.service;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.resume.model.*;
import interview.guide.modules.resume.repository.ResumeRepository;
import interview.guide.modules.resume.repository.ResumeVersionRepository;
import interview.guide.modules.user.security.UserContext;
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
        ResumeEntity resume = ownedResume(resumeId);
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
        ResumeEntity resume = ownedResume(resumeId);
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
