package interview.guide.modules.target.service;

import interview.guide.common.ai.StructuredOutputInvoker;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.resume.service.ResumePersistenceService;
import interview.guide.modules.target.model.JobMatchDTO;
import interview.guide.modules.target.model.JobTargetEntity;
import interview.guide.modules.target.model.ResumeOptimizationSuggestion;
import interview.guide.modules.userai.service.UserAiChatClientFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class JobMatchService {
    private final UserAiChatClientFactory chatClientFactory;
    private final StructuredOutputInvoker invoker;
    private final ResumePersistenceService resumeService;
    private final JobTargetService targetService;
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
                           ResumePersistenceService resumeService, JobTargetService targetService) {
        this.chatClientFactory = chatClientFactory;
        this.invoker = invoker;
        this.resumeService = resumeService;
        this.targetService = targetService;
    }

    public JobMatchDTO match(Long targetId, Long resumeId) {
        JobTargetEntity target = targetService.getOwned(targetId);
        String resume = resumeService.findById(resumeId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESUME_NOT_FOUND))
            .getResumeText();
        String system = "你是严谨的招聘顾问和简历教练。基于简历与岗位JD评估匹配度，只根据给定信息判断。"
            + "输出可验证的匹配技能、缺失技能和可执行建议。optimizationSuggestions 应给出可复核的局部改写；"
            + "optimizedResumeText 必须保留原简历已有事实，不得虚构经历、技能、业绩或学历，只改善结构、表述与JD关键词覆盖。"
            + "\n\n" + converter.getFormat();
        String prompt = "岗位：" + target.getTitle() + "\n公司："
            + (target.getCompany() == null ? "未填写" : target.getCompany())
            + "\nJD：\n" + target.getJobDescription() + "\n\n简历：\n" + resume;
        MatchResponse result = invoker.invoke(chatClientFactory.forCurrentUser(), chatClientFactory.fallbackForCurrentUser(), system, prompt, converter,
            ErrorCode.AI_SERVICE_ERROR, "岗位匹配分析失败：", "岗位匹配分析", log);
        String optimized = result.optimizedResumeText() == null || result.optimizedResumeText().isBlank()
            ? resume : result.optimizedResumeText().trim();
        return new JobMatchDTO(Math.max(0, Math.min(100, result.score())), result.summary(),
            safe(result.matchedSkills()), safe(result.missingSkills()), safe(result.suggestions()),
            safe(result.optimizationSuggestions()), optimized, LocalDateTime.now());
    }

    private <T> List<T> safe(List<T> value) {
        return value == null ? List.of() : value;
    }
}
