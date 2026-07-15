package interview.guide.modules.target.service;

import interview.guide.common.ai.StructuredOutputInvoker;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.resume.service.ResumePersistenceService;
import interview.guide.modules.target.model.JobMatchDTO;
import interview.guide.modules.target.model.JobMatchEntity;
import interview.guide.modules.target.model.JobTargetEntity;
import interview.guide.modules.target.model.ResumeOptimizationSuggestion;
import interview.guide.modules.target.repository.JobMatchRepository;
import interview.guide.modules.user.security.UserContext;
import interview.guide.modules.userai.service.UserAiChatClientFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class JobMatchService {
    private final UserAiChatClientFactory chatClientFactory;
    private final StructuredOutputInvoker invoker;
    private final ResumePersistenceService resumeService;
    private final JobTargetService targetService;
    private final JobMatchRepository matchRepository;
    private final ObjectMapper objectMapper;
    private final BeanOutputConverter<MatchResponse> converter = new BeanOutputConverter<>(MatchResponse.class);

    private record MatchResponse(
        int score,
        String summary,
        List<String> matchedSkills,
        List<String> missingSkills,
        List<String> suggestions,
        List<ResumeOptimizationSuggestion> optimizationSuggestions,
        String optimizedResumeText
    ) {}

    public JobMatchService(UserAiChatClientFactory chatClientFactory, StructuredOutputInvoker invoker,
                           ResumePersistenceService resumeService, JobTargetService targetService,
                           JobMatchRepository matchRepository, ObjectMapper objectMapper) {
        this.chatClientFactory = chatClientFactory;
        this.invoker = invoker;
        this.resumeService = resumeService;
        this.targetService = targetService;
        this.matchRepository = matchRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 实时调用 AI 匹配，并将结果持久化。
     */
    @Transactional
    public JobMatchDTO match(Long targetId, Long resumeId) {
        Long userId = UserContext.getCurrentUserIdOrThrow();
        JobTargetEntity target = targetService.getOwned(targetId);
        var resumeEntity = resumeService.findById(resumeId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESUME_NOT_FOUND));
        String resume = resumeEntity.getResumeText();

        String system = "你是严谨的招聘顾问和简历教练。基于简历与岗位JD评估匹配度，只根据给定信息判断。"
            + "输出可验证的匹配技能、缺失技能和可执行建议。optimizationSuggestions 应给出可复核的局部改写；"
            + "optimizedResumeText 必须保留原简历已有事实，不得虚构经历、技能、业绩或学历，只改善结构、表述与JD关键词覆盖。"
            + "\n\n" + converter.getFormat();
        String prompt = "岗位：" + target.getTitle() + "\n公司："
            + (target.getCompany() == null ? "未填写" : target.getCompany())
            + "\nJD：\n" + target.getJobDescription() + "\n\n简历：\n" + resume;

        MatchResponse result = invoker.invoke(
            chatClientFactory.forCurrentUser(),
            chatClientFactory.fallbackForCurrentUser(),
            system, prompt, converter,
            ErrorCode.AI_SERVICE_ERROR, "岗位匹配分析失败：", "岗位匹配分析", log);

        String optimized = result.optimizedResumeText() == null || result.optimizedResumeText().isBlank()
            ? resume : result.optimizedResumeText().trim();
        int score = Math.max(0, Math.min(100, result.score()));
        LocalDateTime now = LocalDateTime.now();

        JobMatchEntity entity = new JobMatchEntity();
        entity.setUserId(userId);
        entity.setResumeId(resumeId);
        entity.setJobTargetId(targetId);
        entity.setJobTitleSnapshot(target.getTitle());
        entity.setScore(score);
        entity.setSummary(result.summary());
        entity.setMatchedSkillsJson(toJson(safe(result.matchedSkills())));
        entity.setMissingSkillsJson(toJson(safe(result.missingSkills())));
        entity.setSuggestionsJson(toJson(safe(result.suggestions())));
        entity.setOptimizationSuggestionsJson(toJson(safe(result.optimizationSuggestions())));
        entity.setOptimizedResumeText(optimized);
        entity.setAnalyzedAt(now);
        JobMatchEntity saved = matchRepository.save(entity);

        log.info("岗位匹配已持久化: matchId={}, resumeId={}, targetId={}, score={}",
            saved.getId(), resumeId, targetId, score);

        return toDto(saved);
    }

    /** 某简历 + 某岗位 最近一次匹配（无则 empty） */
    public Optional<JobMatchDTO> latest(Long targetId, Long resumeId) {
        Long userId = UserContext.getCurrentUserIdOrThrow();
        // 校验岗位归属；简历归属由 resumeService 保证
        targetService.getOwned(targetId);
        resumeService.findById(resumeId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESUME_NOT_FOUND));
        return matchRepository
            .findFirstByUserIdAndResumeIdAndJobTargetIdOrderByAnalyzedAtDesc(userId, resumeId, targetId)
            .map(this::toDto);
    }

    /** 简历下历史匹配列表（新→旧） */
    public List<JobMatchDTO> listByResume(Long resumeId) {
        Long userId = UserContext.getCurrentUserIdOrThrow();
        resumeService.findById(resumeId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESUME_NOT_FOUND));
        return matchRepository.findByUserIdAndResumeIdOrderByAnalyzedAtDesc(userId, resumeId)
            .stream().map(this::toDto).toList();
    }

    private JobMatchDTO toDto(JobMatchEntity e) {
        return new JobMatchDTO(
            e.getId(),
            e.getResumeId(),
            e.getJobTargetId(),
            e.getJobTitleSnapshot(),
            e.getScore() == null ? 0 : e.getScore(),
            e.getSummary(),
            readStringList(e.getMatchedSkillsJson()),
            readStringList(e.getMissingSkillsJson()),
            readStringList(e.getSuggestionsJson()),
            readOptimizationList(e.getOptimizationSuggestionsJson()),
            e.getOptimizedResumeText(),
            e.getAnalyzedAt()
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            log.warn("序列化匹配结果失败: {}", ex.getMessage());
            return "[]";
        }
    }

    private List<String> readStringList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception ex) {
            log.warn("反序列化字符串列表失败: {}", ex.getMessage());
            return List.of();
        }
    }

    private List<ResumeOptimizationSuggestion> readOptimizationList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<ResumeOptimizationSuggestion>>() {});
        } catch (Exception ex) {
            log.warn("反序列化优化建议失败: {}", ex.getMessage());
            return List.of();
        }
    }

    private <T> List<T> safe(List<T> value) {
        return value == null ? List.of() : value;
    }
}
