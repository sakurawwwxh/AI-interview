package interview.guide.modules.interview.service;

import interview.guide.common.ai.StructuredOutputInvoker;
import interview.guide.modules.interview.model.InterviewQuestionDTO;
import interview.guide.modules.interview.model.InterviewTemplateConfig;
import interview.guide.modules.userai.service.UserAiChatClientFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.io.ClassPathResource;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 面试问题生成服务单元测试
 */
class InterviewQuestionServiceTest {

    private UserAiChatClientFactory chatClientFactory;
    private ChatClient chatClient;
    private StructuredOutputInvoker structuredOutputInvoker;
    private InterviewQuestionService service;

    @BeforeEach
    void setUp() throws Exception {
        chatClient = mock(ChatClient.class);
        chatClientFactory = mock(UserAiChatClientFactory.class);
        when(chatClientFactory.forCurrentUser()).thenReturn(chatClient);
        when(chatClientFactory.fallbackForCurrentUser()).thenReturn(null);
        structuredOutputInvoker = mock(StructuredOutputInvoker.class);

        service = new InterviewQuestionService(
            chatClientFactory,
            structuredOutputInvoker,
            new ClassPathResource("prompts/interview-question-system.st"),
            new ClassPathResource("prompts/interview-question-user.st"),
            new ClassPathResource("prompts/interview-followup-system.st"),
            1  // followUpCount
        );
    }

    @Nested
    @DisplayName("generateQuestions 生成面试问题")
    class GenerateQuestions {

        @Test
        @DisplayName("AI 调用失败时降级为默认题库并标记 DEFAULT")
        void shouldFallbackToDefaultWhenAIFails() {
            when(structuredOutputInvoker.invoke(
                any(), any(), anyString(), anyString(), any(BeanOutputConverter.class),
                any(), anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("AI 不可用"));

            var result = service.generateQuestionsWithSource(
                "简历内容", 6, null, InterviewTemplateConfig.defaultBackend(1), null);

            assertEquals("DEFAULT", result.source());
            assertEquals(6, result.questions().size());
            assertFalse(result.questions().getFirst().question().isBlank());
        }

        @Test
        @DisplayName("JD 关键词提升相关题型权重")
        void shouldBoostWeightsByJobDescription() {
            InterviewTemplateConfig base = InterviewTemplateConfig.defaultBackend(1);
            InterviewTemplateConfig boosted = service.boostTemplateByJobDescription(
                base, "岗位：后端\nJD：精通 Redis 缓存与 MySQL 索引优化");

            int redisBase = base.questionTypes().stream()
                .filter(w -> w.type() == InterviewQuestionDTO.QuestionType.REDIS)
                .mapToInt(InterviewTemplateConfig.QuestionTypeWeight::weight).findFirst().orElse(0);
            int redisBoosted = boosted.questionTypes().stream()
                .filter(w -> w.type() == InterviewQuestionDTO.QuestionType.REDIS)
                .mapToInt(InterviewTemplateConfig.QuestionTypeWeight::weight).findFirst().orElse(0);
            assertTrue(redisBoosted > redisBase);
        }

        @Test
        @DisplayName("历史题去重过滤高度相似题目")
        void shouldDedupeAgainstHistory() {
            List<InterviewQuestionDTO> generated = List.of(
                InterviewQuestionDTO.create(0, "MySQL的索引有哪些类型？B+树索引的原理是什么？",
                    InterviewQuestionDTO.QuestionType.MYSQL, "MySQL"),
                InterviewQuestionDTO.create(1, "请介绍你最有挑战的项目",
                    InterviewQuestionDTO.QuestionType.PROJECT, "项目经历")
            );
            List<String> history = List.of("MySQL的索引有哪些类型？B+树索引的原理是什么？");

            List<InterviewQuestionDTO> result = service.dedupeAgainstHistory(generated, history, 2, 1);

            assertEquals(2, result.size());
            assertTrue(result.stream().noneMatch(q ->
                q.question().contains("MySQL的索引有哪些类型")));
        }

        @Test
        @DisplayName("getDefaultFollowUpCount 返回配置值")
        void shouldReturnConfiguredFollowUpCount() {
            assertEquals(1, service.getDefaultFollowUpCount());
        }
    }

    @Nested
    @DisplayName("generateDynamicFollowUp 动态追问")
    class GenerateDynamicFollowUp {

        private final InterviewQuestionDTO mainQuestion = InterviewQuestionDTO.create(
            0, "Java 集合的底层原理", InterviewQuestionDTO.QuestionType.JAVA_COLLECTION, "Java集合");

        @Test
        @DisplayName("追问数已达上限时返回空")
        void shouldReturnEmptyWhenMaxReached() {
            Optional<InterviewQuestionDTO> result = service.generateDynamicFollowUp(
                mainQuestion, "回答内容", 1, 0, 1, 1);  // existing=1, max=1

            assertTrue(result.isEmpty());
            verifyNoInteractions(structuredOutputInvoker);
        }

        @Test
        @DisplayName("空答案时返回空")
        void shouldReturnEmptyWhenAnswerBlank() {
            Optional<InterviewQuestionDTO> result = service.generateDynamicFollowUp(
                mainQuestion, "", 1, 0, 0, 1);

            assertTrue(result.isEmpty());
            verifyNoInteractions(structuredOutputInvoker);
        }

        @Test
        @DisplayName("null 答案时返回空")
        void shouldReturnEmptyWhenAnswerNull() {
            Optional<InterviewQuestionDTO> result = service.generateDynamicFollowUp(
                mainQuestion, null, 1, 0, 0, 1);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("AI 决策不追问时返回空")
        void shouldReturnEmptyWhenAIDecidesNoFollowUp() {
            // FollowUpDecisionDTO 是私有 record，无法直接构造
            // invoke 返回 null 模拟 AI 决策无追问
            when(structuredOutputInvoker.invoke(
                any(), anyString(), anyString(), any(BeanOutputConverter.class),
                any(), anyString(), anyString(), any()))
                .thenReturn(null);

            Optional<InterviewQuestionDTO> result = service.generateDynamicFollowUp(
                mainQuestion, "回答很好", 1, 0, 0, 1);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("AI 异常时返回空，不阻断主流程")
        void shouldReturnEmptyOnAIException() {
            when(structuredOutputInvoker.invoke(
                any(), anyString(), anyString(), any(BeanOutputConverter.class),
                any(), anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("AI 超时"));

            Optional<InterviewQuestionDTO> result = service.generateDynamicFollowUp(
                mainQuestion, "回答", 1, 0, 0, 1);

            assertTrue(result.isEmpty());
        }
    }
}
