package interview.guide.modules.target.service;

import interview.guide.common.ai.StructuredOutputInvoker;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.resume.service.ResumePersistenceService;
import interview.guide.modules.target.model.JobMatchDTO;
import interview.guide.modules.target.model.JobTargetEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j @Service
public class JobMatchService {
    private final ChatClient chatClient; private final StructuredOutputInvoker invoker; private final ResumePersistenceService resumeService; private final JobTargetService targetService;
    private final BeanOutputConverter<MatchResponse> converter = new BeanOutputConverter<>(MatchResponse.class);
    private record MatchResponse(int score, String summary, List<String> matchedSkills, List<String> missingSkills, List<String> suggestions) {}
    public JobMatchService(ChatClient.Builder builder, StructuredOutputInvoker invoker, ResumePersistenceService resumeService, JobTargetService targetService) { this.chatClient = builder.build(); this.invoker = invoker; this.resumeService = resumeService; this.targetService = targetService; }
    public JobMatchDTO match(Long targetId, Long resumeId) {
        JobTargetEntity target = targetService.getOwned(targetId);
        String resume = resumeService.findById(resumeId).orElseThrow(() -> new interview.guide.common.exception.BusinessException(ErrorCode.RESUME_NOT_FOUND)).getResumeText();
        String system = "你是严谨的招聘顾问。基于简历与岗位JD评估匹配度，只根据给定信息判断。匹配技能和缺失技能应为简洁中文短语，建议必须可执行。" + "\n\n" + converter.getFormat();
        String prompt = "岗位：" + target.getTitle() + "\n公司：" + (target.getCompany() == null ? "未填写" : target.getCompany()) + "\nJD：\n" + target.getJobDescription() + "\n\n简历：\n" + resume;
        MatchResponse result = invoker.invoke(chatClient, system, prompt, converter, ErrorCode.AI_SERVICE_ERROR, "岗位匹配分析失败：", "岗位匹配分析", log);
        return new JobMatchDTO(Math.max(0, Math.min(100, result.score())), result.summary(), safe(result.matchedSkills()), safe(result.missingSkills()), safe(result.suggestions()), LocalDateTime.now());
    }
    private List<String> safe(List<String> value) { return value == null ? List.of() : value; }
}
