package interview.guide.modules.interview.service;

import interview.guide.modules.interview.model.InterviewQuestionDTO;
import interview.guide.modules.knowledgebase.model.VectorStatus;
import interview.guide.modules.knowledgebase.repository.KnowledgeBaseRepository;
import interview.guide.modules.knowledgebase.service.KnowledgeBaseVectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * 为技术题评分提供本地知识库依据。检索失败时降级为空上下文，不阻断面试报告。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewEvaluationEvidenceService {

    private static final Set<InterviewQuestionDTO.QuestionType> UNSUITABLE_TYPES = Set.of(
        InterviewQuestionDTO.QuestionType.PROJECT,
        InterviewQuestionDTO.QuestionType.SOFT_SKILLS
    );

    /** 单条 evidence 文本的最大字符数（按得分降序截断，防 prompt 过大） */
    private static final int MAX_EVIDENCE_LENGTH = 6000;
    /** 单条知识库片段截断长度 */
    private static final int FRAGMENT_MAX_LENGTH = 600;

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KnowledgeBaseVectorService vectorService;

    @Value("${app.interview.evaluation.rag.enabled:true}")
    private boolean enabled;

    @Value("${app.interview.evaluation.rag.top-k:2}")
    private int topK;

    @Value("${app.interview.evaluation.rag.min-score:0.32}")
    private double minScore;

    /**
     * 查询所有向量化完成的知识库 ID（在评估入口调一次，避免每批重复查询）。
     */
    public List<Long> getAvailableKnowledgeBaseIds() {
        return knowledgeBaseRepository.findAll().stream()
            .filter(item -> item.getVectorStatus() == VectorStatus.COMPLETED)
            .map(item -> item.getId())
            .toList();
    }

    /**
     * 为一批问题构建知识库依据文本。
     *
     * @param questions         本批问题列表
     * @param knowledgeBaseIds  可用知识库 ID（由调用方传入，避免每批重复查库）
     */
    public String buildEvidence(List<InterviewQuestionDTO> questions, List<Long> knowledgeBaseIds) {
        if (!enabled || questions == null || questions.isEmpty()) {
            return "无本地知识库依据。";
        }
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()) {
            return "无可用的本地知识库依据。";
        }
        try {
            StringBuilder evidence = new StringBuilder();
            for (InterviewQuestionDTO question : questions) {
                if (question.type() == null || UNSUITABLE_TYPES.contains(question.type())) {
                    // 项目/软技能题不检索知识库，显式说明以消除 AI 困惑
                    evidence.append("[问题 ").append(question.questionIndex() + 1)
                        .append(" 为项目/软技能题，未检索知识库依据]\n");
                    continue;
                }
                String query = question.question() + "\n候选人回答：" +
                    (question.userAnswer() == null ? "未回答" : question.userAnswer());
                List<Document> docs = vectorService.similaritySearch(query, knowledgeBaseIds, topK, minScore);
                if (docs.isEmpty()) {
                    continue;
                }
                evidence.append("[问题 ").append(question.questionIndex() + 1).append(" 的本地知识库依据]\n");
                for (Document doc : docs) {
                    String text = doc.getText();
                    if (text != null && !text.isBlank()) {
                        evidence.append("- ").append(truncate(text, FRAGMENT_MAX_LENGTH)).append("\n");
                    }
                }
                // 总长度超限时停止追加，避免 prompt 过大
                if (evidence.length() > MAX_EVIDENCE_LENGTH) {
                    evidence.append("（依据已达上限，后续问题略）\n");
                    break;
                }
            }
            return evidence.isEmpty()
                ? "无与本次问题直接相关的本地知识库依据。"
                : evidence.toString();
        } catch (Exception e) {
            log.warn("构建面试评分知识库依据失败，将降级为普通评分: {}", e.getMessage());
            return "本地知识库检索暂不可用，请仅根据题目和回答评分。";
        }
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "...";
    }
}
