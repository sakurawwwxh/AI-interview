package interview.guide.modules.target.service;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.target.model.*;
import interview.guide.modules.target.repository.JobTargetRepository;
import interview.guide.modules.user.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service @RequiredArgsConstructor
public class JobTargetService {
    private final JobTargetRepository repository;
    public List<JobTargetDTO> list() { Long id = UserContext.getCurrentUserIdOrThrow(); return repository.findAllByUserIdOrderByUpdatedAtDesc(id).stream().map(this::dto).toList(); }
    public JobTargetEntity getOwned(Long targetId) { Long id = UserContext.getCurrentUserIdOrThrow(); return repository.findByIdAndUserId(targetId, id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "岗位目标不存在")); }
    @Transactional public JobTargetDTO create(JobTargetRequest request) { Long userId = UserContext.getCurrentUserIdOrThrow(); JobTargetEntity entity = new JobTargetEntity(); entity.setUserId(userId); apply(entity, request); if (repository.findByUserIdAndActiveTrue(userId).isEmpty()) entity.setActive(true); return dto(repository.save(entity)); }
    @Transactional public JobTargetDTO update(Long targetId, JobTargetRequest request) { JobTargetEntity entity = getOwned(targetId); apply(entity, request); return dto(repository.save(entity)); }
    @Transactional public JobTargetDTO activate(Long targetId) { JobTargetEntity entity = getOwned(targetId); repository.findByUserIdAndActiveTrue(entity.getUserId()).forEach(item -> { item.setActive(false); repository.save(item); }); entity.setActive(true); return dto(repository.save(entity)); }
    @Transactional public void delete(Long targetId) { repository.delete(getOwned(targetId)); }
    private void apply(JobTargetEntity target, JobTargetRequest request) { target.setTitle(request.title().trim()); target.setCompany(request.company() == null ? null : request.company().trim()); target.setJobDescription(request.jobDescription().trim()); }
    private JobTargetDTO dto(JobTargetEntity item) { return new JobTargetDTO(item.getId(), item.getTitle(), item.getCompany(), item.getJobDescription(), item.isActive(), item.getCreatedAt(), item.getUpdatedAt()); }
}
