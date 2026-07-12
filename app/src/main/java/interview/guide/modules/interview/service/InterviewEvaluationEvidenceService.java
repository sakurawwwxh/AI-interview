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

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KnowledgeBaseVectorService vectorService;

    @Value("${app.interview.evaluation.rag.enabled:true}")
    private boolean enabled;

    @Value("${app.interview.evaluation.rag.top-k:2}")
    private int topK;

    @Value("${app.interview.evaluation.rag.min-score:0.32}")
    private double minScore;

    public String buildEvidence(List<InterviewQuestionDTO> questions) {
        if (!enabled || questions == null || questions.isEmpty()) {
            return "无本地知识库依据。";
        }
        try {
            List<Long> knowledgeBaseIds = knowledgeBaseRepository.findAll().stream()
                .filter(item -> item.getVectorStatus() == VectorStatus.COMPLETED)
                .map(item -> item.getId())
                .toList();
            if (knowledgeBaseIds.isEmpty()) {
                return "无可用的本地知识库依据。";
            }

            StringBuilder evidence = new StringBuilder();
            for (InterviewQuestionDTO question : questions) {
                if (question.type() == null || UNSUITABLE_TYPES.contains(question.type())) {
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
                        evidence.append("- ").append(truncate(text, 600)).append("\n");
                    }
                }
            }
            return evidence.isEmpty() ? "无与本次问题直接相关的本地知识库依据。" : evidence.toString();
        } catch (Exception e) {
            log.warn("构建面试评分知识库依据失败，将降级为普通评分: {}", e.getMessage());
            return "本地知识库检索暂不可用，请仅根据题目和回答评分。";
        }
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "...";
    }
}
