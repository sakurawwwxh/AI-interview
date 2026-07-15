package interview.guide.modules.growth.service;

import interview.guide.modules.growth.model.GrowthPlanDTO;
import interview.guide.modules.interview.model.InterviewStatisticsDTO;
import interview.guide.modules.interview.service.InterviewStatisticsService;
import interview.guide.modules.practice.model.PracticeTaskEntity;
import interview.guide.modules.practice.model.PracticeTaskStatus;
import interview.guide.modules.practice.repository.PracticeTaskRepository;
import interview.guide.modules.target.service.JobTargetService;
import interview.guide.modules.user.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GrowthPlanService {
    private final InterviewStatisticsService statisticsService;
    private final PracticeTaskRepository practiceTaskRepository;
    private final JobTargetService jobTargetService;

    @Transactional(readOnly = true)
    public GrowthPlanDTO getPlan() {
        Long userId = UserContext.getCurrentUserIdOrThrow();
        InterviewStatisticsDTO statistics = statisticsService.getStatistics();
        List<PracticeTaskEntity> tasks = practiceTaskRepository.findByUserIdAndStatus(userId, PracticeTaskStatus.TODO,
            PageRequest.of(0, 3, Sort.by(Sort.Direction.ASC, "originalScore"))).getContent();
        String targetRole = jobTargetService.list().stream().filter(item -> item.active()).map(item -> item.title()).findFirst().orElse(null);
        List<GrowthPlanDTO.ActionItem> actions = new ArrayList<>();
        for (InterviewStatisticsDTO.Weakness weakness : statistics.weaknesses()) {
            PracticeTaskEntity related = tasks.stream().filter(task -> weakness.category().equalsIgnoreCase(task.getCategory())).findFirst().orElse(null);
            actions.add(new GrowthPlanDTO.ActionItem(
                "补强「" + weakness.category() + "」",
                "当前平均 " + weakness.averageScore() + " 分；先完成 2 道同类题并复盘关键点。",
                weakness.category(), weakness.averageScore(), related == null ? "/practice?status=TODO&category=" + weakness.category() : "/practice/" + related.getId(),
                related == null ? null : related.getId(), weakness.averageScore() < 60 ? "HIGH" : "MEDIUM"));
        }
        for (PracticeTaskEntity task : tasks) {
            if (actions.stream().noneMatch(action -> task.getId().equals(action.practiceTaskId()))) {
                actions.add(new GrowthPlanDTO.ActionItem("完成错题复练", "原始得分 " + task.getOriginalScore() + " 分，优先重新组织答案。",
                    task.getCategory(), task.getOriginalScore(), "/practice/" + task.getId(), task.getId(), "HIGH"));
            }
        }
        if (actions.isEmpty()) {
            actions.add(new GrowthPlanDTO.ActionItem("完成一次模拟面试", targetRole == null ? "上传简历后开始模拟面试，系统会据此生成能力画像。" : "以「" + targetRole + "」为目标完成一次定向模拟面试。",
                null, null, "/history", null, "MEDIUM"));
        }
        String headline = statistics.completedInterviewCount() == 0 ? "从一次模拟面试开始建立成长档案"
            : "本周优先完成 " + Math.min(3, actions.size()) + " 项训练，让薄弱项转化为稳定得分";
        return new GrowthPlanDTO(headline, targetRole, statistics.completedInterviewCount(), tasks.size(), actions.stream().limit(3).toList());
    }
}
