package interview.guide.modules.interview.service;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.model.AsyncTaskStatus;
import interview.guide.infrastructure.redis.InterviewSessionCache;
import interview.guide.infrastructure.redis.InterviewSessionCache.CachedSession;
import interview.guide.infrastructure.redis.RedisService;
import interview.guide.modules.interview.listener.EvaluateStreamProducer;
import interview.guide.modules.interview.model.InterviewQuestionDTO;
import interview.guide.modules.interview.model.SubmitAnswerRequest;
import interview.guide.modules.interview.model.SubmitAnswerResponse;
import interview.guide.modules.interview.model.InterviewSessionDTO.SessionStatus;
import interview.guide.modules.target.service.JobTargetService;
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
            verify(persistenceService).saveAnswer(eq(SESSION_ID), eq(0), anyString(), anyString(),
                eq("my answer"), eq(0), isNull(), eq(30));
            verify(persistenceService).updateCurrentQuestionIndex(SESSION_ID, 1);
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
            verify(persistenceService).updateEvaluateStatus(SESSION_ID, AsyncTaskStatus.PENDING, null);
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
            verify(persistenceService).updateQuestionsJson(eq(SESSION_ID), anyList());
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
            verify(persistenceService).updateSessionStatus(eq(SESSION_ID), any());
            verify(persistenceService).updateEvaluateStatus(SESSION_ID, AsyncTaskStatus.PENDING, null);
            verify(evaluateStreamProducer).sendEvaluateTask(1L, SESSION_ID);
        }
    }
}
