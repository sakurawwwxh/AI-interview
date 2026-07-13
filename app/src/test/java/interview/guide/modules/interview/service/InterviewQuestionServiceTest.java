package interview.guide.modules.interview.service;

import interview.guide.common.ai.StructuredOutputInvoker;
import interview.guide.common.exception.BusinessException;
import interview.guide.modules.interview.model.InterviewQuestionDTO;
import interview.guide.modules.interview.model.InterviewTemplateConfig;
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

    private ChatClient.Builder chatClientBuilder;
    private ChatClient chatClient;
    private StructuredOutputInvoker structuredOutputInvoker;
    private InterviewQuestionService service;

    @BeforeEach
    void setUp() throws Exception {
        chatClient = mock(ChatClient.class);
        chatClientBuilder = mock(ChatClient.Builder.class);
        when(chatClientBuilder.build()).thenReturn(chatClient);
        structuredOutputInvoker = mock(StructuredOutputInvoker.class);

        service = new InterviewQuestionService(
            chatClientBuilder,
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
        @DisplayName("AI 调用失败时抛出 BusinessException（不降级）")
        void shouldThrowBusinessExceptionWhenAIFails() {
            when(structuredOutputInvoker.invoke(
                any(), anyString(), anyString(), any(BeanOutputConverter.class),
                any(), anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("AI 不可用"));

            // invoke 内部 catch 会包装为 BusinessException，外层 catch 不再降级
            assertThrows(BusinessException.class, () ->
                service.generateQuestions("简历内容", 6, null));
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
