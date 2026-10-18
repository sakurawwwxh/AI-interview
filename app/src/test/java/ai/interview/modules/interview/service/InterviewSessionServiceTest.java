package ai.interview.modules.interview.service;

import ai.interview.common.exception.BusinessException;
import ai.interview.common.model.AsyncTaskStatus;
import ai.interview.infrastructure.redis.InterviewSessionCache;
import ai.interview.infrastructure.redis.InterviewSessionCache.CachedSession;
import ai.interview.infrastructure.redis.RedisService;
import ai.interview.modules.interview.listener.EvaluateStreamProducer;
import ai.interview.modules.interview.model.CreateInterviewRequest;
import ai.interview.modules.interview.model.InterviewQuestionDTO;
import ai.interview.modules.interview.model.QuestionGenerationResult;
import ai.interview.modules.interview.model.SubmitAnswerRequest;
import ai.interview.modules.interview.model.SubmitAnswerResponse;
import ai.interview.modules.interview.model.InterviewSessionDTO.SessionStatus;
import ai.interview.modules.target.service.JobTargetService;
import org.mockito.InOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 面试会话管理服务单元测试
 */
class InterviewSessionServiceTest {

    private final InterviewQuestionService questionService = mock(InterviewQuestionService.class);
    private final AnswerEvaluationService evaluationService = mock(AnswerEvaluationService.class);
    private final InterviewPersistenceService persistenceService = mock(InterviewPersistenceService.class);
    private final InterviewSessionCache sessionCache = mock(InterviewSessionCache.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EvaluateStreamProducer evaluateStreamProducer = mock(EvaluateStreamProducer.class);
    private final RedisService redisService = mock(RedisService.class);
    private final JobTargetService jobTargetService = mock(JobTargetService.class);

    private final InterviewSessionService service = new InterviewSessionService(
        questionService, evaluationService, persistenceService,
        sessionCache, objectMapper, evaluateStreamProducer, redisService, jobTargetService);

    private static final String SESSION_ID = "test-session-123";
    private static final String LOCK_KEY = "interview:session:lock:" + SESSION_ID;

    /**
     * 构造一个包含 2 道题的 CachedSession
     */
    private CachedSession buildSession(int currentIndex, SessionStatus status) {
        List<InterviewQuestionDTO> questions = List.of(
            InterviewQuestionDTO.create(0, "Q1", InterviewQuestionDTO.QuestionType.JAVA_BASIC, "Java基础"),
            InterviewQuestionDTO.create(1, "Q2", InterviewQuestionDTO.QuestionType.MYSQL, "MySQL")
        );
        return new CachedSession(1L, SESSION_ID, "resume text", 1L,
            questions, currentIndex, status, objectMapper);
    }

    @BeforeEach
    void setUp() {
        // 设置 SecurityContext（模拟已登录用户 userId=1L）
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                1L, "testuser", java.util.List.of()));

        // mock 分布式锁：直接执行操作
        when(redisService.executeWithLock(eq(LOCK_KEY), anyLong(), anyLong(), eq(TimeUnit.SECONDS), any()))
            .thenAnswer(inv -> {
                RedisService.LockedOperation<?> op = inv.getArgument(4);
                return op.execute();
            });
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("submitAnswer 提交答案")
    class SubmitAnswer {

        @Test
        @DisplayName("正常提交无追问，前进到下一题")
        void shouldAdvanceToNextQuestion() {
            CachedSession cached = buildSession(0, SessionStatus.IN_PROGRESS);
            when(sessionCache.getSession(1L, SESSION_ID)).thenReturn(Optional.of(cached));
            doNothing().when(sessionCache).refreshSessionTTL(1L, SESSION_ID);
            when(questionService.generateDynamicFollowUp(any(), anyString(), anyInt(), anyInt(), anyInt(), anyInt()))
                .thenReturn(Optional.empty());

            SubmitAnswerRequest request = new SubmitAnswerRequest(SESSION_ID, 0, "my answer", 30);
            SubmitAnswerResponse response = service.submitAnswer(request);

            assertTrue(response.hasNextQuestion());
            assertEquals(1, response.currentIndex());
            verify(sessionCache).updateQuestions(eq(1L), eq(SESSION_ID), anyList());
            verify(sessionCache).updateCurrentIndex(1L, SESSION_ID, 1);
            verify(persistenceService).persistSubmittedAnswer(eq(SESSION_ID), eq(0), anyString(), anyString(),
                eq("my answer"), eq(30), anyList(), eq(1), eq(false));
            verify(redisService).executeWithLock(eq(LOCK_KEY), eq(3L), eq(-1L), eq(TimeUnit.SECONDS), any());
        }

        @Test
        @DisplayName("最后一题提交后触发异步评估")
        void shouldTriggerEvaluationOnLastQuestion() {
            // 构造两道题均已回答的会话（currentIndex=1，题0已有答案）
            List<InterviewQuestionDTO> questions = List.of(
                InterviewQuestionDTO.create(0, "Q1", InterviewQuestionDTO.QuestionType.JAVA_BASIC, "Java基础")
                    .withAnswer("answered Q1"),
                InterviewQuestionDTO.create(1, "Q2", InterviewQuestionDTO.QuestionType.MYSQL, "MySQL")
            );
            CachedSession cached = new CachedSession(1L, SESSION_ID, "resume text", 1L,
                questions, 1, SessionStatus.IN_PROGRESS, objectMapper);

            when(sessionCache.getSession(1L, SESSION_ID)).thenReturn(Optional.of(cached));
            doNothing().when(sessionCache).refreshSessionTTL(1L, SESSION_ID);
            when(questionService.generateDynamicFollowUp(any(), anyString(), anyInt(), anyInt(), anyInt(), anyInt()))
                .thenReturn(Optional.empty());

            SubmitAnswerRequest request = new SubmitAnswerRequest(SESSION_ID, 1, "final answer", 20);
            SubmitAnswerResponse response = service.submitAnswer(request);

            assertFalse(response.hasNextQuestion());
            verify(persistenceService).persistSubmittedAnswer(eq(SESSION_ID), eq(1), anyString(), anyString(),
                eq("final answer"), eq(20), anyList(), eq(-1), eq(true));
            verify(evaluateStreamProducer).sendEvaluateTask(1L, SESSION_ID);
        }

        @Test
        @DisplayName("追问生成后回写 questionsJson 到数据库")
        void shouldPersistQuestionsJsonWhenFollowUpGenerated() {
            CachedSession cached = buildSession(0, SessionStatus.IN_PROGRESS);
            when(sessionCache.getSession(1L, SESSION_ID)).thenReturn(Optional.of(cached));
            doNothing().when(sessionCache).refreshSessionTTL(1L, SESSION_ID);

            InterviewQuestionDTO followUp = InterviewQuestionDTO.create(
                2, "追问Q", InterviewQuestionDTO.QuestionType.JAVA_BASIC, "Java基础", true, 0);
            when(questionService.generateDynamicFollowUp(any(), anyString(), eq(2), eq(0), eq(0), anyInt()))
                .thenReturn(Optional.of(followUp));

            // mock loadTemplate 需要的 persistenceService.findBySessionId
            when(persistenceService.findBySessionId(SESSION_ID)).thenReturn(Optional.empty());

            SubmitAnswerRequest request = new SubmitAnswerRequest(SESSION_ID, 0, "answer", 10);
            SubmitAnswerResponse response = service.submitAnswer(request);

            // 验证追问被生成且 questionsJson 被回写
            assertTrue(response.hasNextQuestion());
            assertEquals(2, response.currentIndex());
            verify(persistenceService).persistSubmittedAnswer(eq(SESSION_ID), eq(0), anyString(), anyString(),
                eq("answer"), eq(10), anyList(), eq(2), eq(false));
        }

        @Test
        @DisplayName("无效问题索引抛出异常")
        void shouldThrowForInvalidIndex() {
            CachedSession cached = buildSession(0, SessionStatus.IN_PROGRESS);
            when(sessionCache.getSession(1L, SESSION_ID)).thenReturn(Optional.of(cached));
            doNothing().when(sessionCache).refreshSessionTTL(1L, SESSION_ID);

            SubmitAnswerRequest request = new SubmitAnswerRequest(SESSION_ID, 5, "answer", 10);
            assertThrows(BusinessException.class, () -> service.submitAnswer(request));
        }

        @Test
        @DisplayName("数据库保存答案失败时不更新 Redis，避免返回无法恢复的成功状态")
        void shouldNotUpdateCacheWhenAnswerPersistenceFails() {
            CachedSession cached = buildSession(0, SessionStatus.IN_PROGRESS);
            when(sessionCache.getSession(1L, SESSION_ID)).thenReturn(Optional.of(cached));
            doNothing().when(sessionCache).refreshSessionTTL(1L, SESSION_ID);
            when(questionService.generateDynamicFollowUp(any(), anyString(), anyInt(), anyInt(), anyInt(), anyInt()))
                .thenReturn(Optional.empty());
            doThrow(new RuntimeException("database unavailable"))
                .when(persistenceService)
                .persistSubmittedAnswer(eq(SESSION_ID), eq(0), anyString(), anyString(),
                    eq("my answer"), eq(30), anyList(), eq(1), eq(false));

            SubmitAnswerRequest request = new SubmitAnswerRequest(SESSION_ID, 0, "my answer", 30);

            assertThrows(RuntimeException.class, () -> service.submitAnswer(request));
            verify(sessionCache, never()).updateQuestions(eq(1L), eq(SESSION_ID), anyList());
            verify(sessionCache, never()).updateCurrentIndex(anyLong(), anyString(), anyInt());
        }
    }

    @Nested
    @DisplayName("createSession 创建会话")
    class CreateSession {

        @Test
        @DisplayName("forceCreate 仍校验简历归属，并在持久化成功后才写缓存")
        void shouldValidateResumeOwnershipBeforeForceCreating() {
            when(questionService.getDefaultFollowUpCount()).thenReturn(1);
            InterviewQuestionDTO question = InterviewQuestionDTO.create(
                0, "Q1", InterviewQuestionDTO.QuestionType.JAVA_BASIC, "Java基础");
            when(questionService.generateQuestionsWithSource(anyString(), anyInt(), anyList(), any(), isNull()))
                .thenReturn(QuestionGenerationResult.ai(List.of(question)));

            CreateInterviewRequest request = new CreateInterviewRequest(
                "resume text", 3, 99L, true, null, null);

            service.createSession(request);

            InOrder order = inOrder(persistenceService, sessionCache);
            order.verify(persistenceService).requireOwnedResume(99L);
            order.verify(persistenceService).saveSession(anyString(), eq(99L), eq(1), anyList(), any());
            order.verify(sessionCache).saveSession(eq(1L), anyString(), eq("resume text"), eq(99L),
                anyList(), eq(0), eq(SessionStatus.CREATED));
        }

        @Test
        @DisplayName("创建会话持久化失败时不写 Redis 缓存")
        void shouldNotCacheSessionWhenPersistenceFails() {
            when(questionService.getDefaultFollowUpCount()).thenReturn(1);
            InterviewQuestionDTO question = InterviewQuestionDTO.create(
                0, "Q1", InterviewQuestionDTO.QuestionType.JAVA_BASIC, "Java基础");
            when(questionService.generateQuestionsWithSource(anyString(), anyInt(), anyList(), any(), isNull()))
                .thenReturn(QuestionGenerationResult.ai(List.of(question)));
            when(persistenceService.saveSession(anyString(), eq(99L), eq(1), anyList(), any()))
                .thenThrow(new RuntimeException("database unavailable"));

            CreateInterviewRequest request = new CreateInterviewRequest(
                "resume text", 3, 99L, true, null, null);

            assertThrows(RuntimeException.class, () -> service.createSession(request));
            verify(sessionCache, never()).saveSession(anyLong(), anyString(), anyString(), anyLong(),
                anyList(), anyInt(), any());
        }
    }

    @Nested
    @DisplayName("completeInterview 提前交卷")
    class CompleteInterview {

        @Test
        @DisplayName("已完成面试抛出异常")
        void shouldThrowWhenAlreadyCompleted() {
            CachedSession cached = buildSession(0, SessionStatus.COMPLETED);
            when(sessionCache.getSession(1L, SESSION_ID)).thenReturn(Optional.of(cached));
            doNothing().when(sessionCache).refreshSessionTTL(1L, SESSION_ID);

            assertThrows(BusinessException.class, () -> service.completeInterview(SESSION_ID));
        }

        @Test
        @DisplayName("正常交卷触发评估")
        void shouldTriggerEvaluationOnComplete() {
            CachedSession cached = buildSession(0, SessionStatus.IN_PROGRESS);
            when(sessionCache.getSession(1L, SESSION_ID)).thenReturn(Optional.of(cached));
            doNothing().when(sessionCache).refreshSessionTTL(1L, SESSION_ID);

            service.completeInterview(SESSION_ID);

            verify(sessionCache).updateSessionStatus(1L, SESSION_ID, SessionStatus.COMPLETED);
            verify(persistenceService).markCompletedForEvaluation(SESSION_ID);
            verify(evaluateStreamProducer).sendEvaluateTask(1L, SESSION_ID);
        }
    }

    @Test
    @DisplayName("完成后的负索引返回已完成，而不是访问负下标")
    void shouldReturnCompletedWhenCurrentIndexIsNegative() {
        CachedSession cached = buildSession(-1, SessionStatus.COMPLETED);
        when(sessionCache.getSession(1L, SESSION_ID)).thenReturn(Optional.of(cached));

        var response = service.getCurrentQuestionResponse(SESSION_ID);

        assertEquals(true, response.get("completed"));
        verify(sessionCache, never()).updateSessionStatus(anyLong(), anyString(), any());
    }

    @Nested
    @DisplayName("generateReport 生成报告")
    class GenerateReport {

        @Test
        @DisplayName("异步评估进行中时拒绝同步再跑一遍，避免两路并发写同一份报告")
        void shouldRejectWhileAsyncEvaluationInFlight() {
            when(sessionCache.getSession(1L, SESSION_ID))
                .thenReturn(java.util.Optional.of(buildSession(1, SessionStatus.COMPLETED)));
            ai.interview.modules.interview.model.InterviewSessionEntity entity =
                new ai.interview.modules.interview.model.InterviewSessionEntity();
            entity.setEvaluateStatus(ai.interview.common.model.AsyncTaskStatus.PROCESSING);
            when(persistenceService.findBySessionIdForCurrentUser(SESSION_ID))
                .thenReturn(java.util.Optional.of(entity));

            ai.interview.common.exception.BusinessException error =
                assertThrows(ai.interview.common.exception.BusinessException.class,
                    () -> service.generateReport(SESSION_ID));

            assertEquals(ai.interview.common.exception.ErrorCode.INTERVIEW_EVALUATION_IN_PROGRESS.getCode(),
                error.getCode());
            verify(evaluationService, never())
                .evaluateInterview(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any());
        }

        @Test
        @DisplayName("已出报告时直接读库返回，不再调用模型")
        void shouldReturnStoredReportWithoutCallingModel() {
            when(sessionCache.getSession(1L, SESSION_ID))
                .thenReturn(java.util.Optional.of(buildSession(1, SessionStatus.EVALUATED)));
            ai.interview.modules.interview.model.InterviewSessionEntity entity =
                new ai.interview.modules.interview.model.InterviewSessionEntity();
            entity.setEvaluateStatus(ai.interview.common.model.AsyncTaskStatus.COMPLETED);
            when(persistenceService.findBySessionIdForCurrentUser(SESSION_ID))
                .thenReturn(java.util.Optional.of(entity));
            ai.interview.modules.interview.model.InterviewReportDTO stored =
                new ai.interview.modules.interview.model.InterviewReportDTO(
                    SESSION_ID, 2, 77, java.util.List.of(), java.util.List.of(), "总评",
                    java.util.List.of("优势"), java.util.List.of("改进"), java.util.List.of());
            when(persistenceService.restoreReport(org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.Optional.of(stored));

            assertEquals(stored, service.generateReport(SESSION_ID));
            verify(evaluationService, never())
                .evaluateInterview(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any());
        }

    }
}
