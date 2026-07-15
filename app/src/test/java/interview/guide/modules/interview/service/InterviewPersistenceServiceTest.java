package interview.guide.modules.interview.service;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.model.AsyncTaskStatus;
import interview.guide.modules.interview.model.InterviewAnswerEntity;
import interview.guide.modules.interview.model.InterviewQuestionDTO;
import interview.guide.modules.interview.model.InterviewSessionEntity;
import interview.guide.modules.interview.model.InterviewTemplateConfig;
import interview.guide.modules.interview.repository.InterviewAnswerRepository;
import interview.guide.modules.interview.repository.InterviewSessionRepository;
import interview.guide.modules.resume.model.ResumeEntity;
import interview.guide.modules.resume.repository.ResumeRepository;
import interview.guide.modules.practice.service.PracticeTaskService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 面试持久化服务单元测试
 */
class InterviewPersistenceServiceTest {

    private final InterviewSessionRepository sessionRepository = mock(InterviewSessionRepository.class);
    private final InterviewAnswerRepository answerRepository = mock(InterviewAnswerRepository.class);
    private final ResumeRepository resumeRepository = mock(ResumeRepository.class);
    private final PracticeTaskService practiceTaskService = mock(PracticeTaskService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final InterviewPersistenceService service = new InterviewPersistenceService(
        sessionRepository, answerRepository, resumeRepository, objectMapper, practiceTaskService);

    @BeforeEach
    void setUpSecurityContext() {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(1L, "testuser", List.of()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("saveSession 保存会话")
    class SaveSession {

        @Test
        @DisplayName("简历存在时正常保存，序列化 questionsJson 和 templateJson")
        void shouldSaveSessionWithSerializedJson() {
            Long resumeId = 1L;
            ResumeEntity resume = new ResumeEntity();
            resume.setId(resumeId);
            when(resumeRepository.findByIdAndUserId(resumeId, 1L)).thenReturn(Optional.of(resume));
            when(sessionRepository.save(any(InterviewSessionEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

            List<InterviewQuestionDTO> questions = List.of(
                InterviewQuestionDTO.create(0, "Q1", InterviewQuestionDTO.QuestionType.JAVA_BASIC, "Java基础")
            );
            InterviewTemplateConfig template = InterviewTemplateConfig.defaultBackend(1);

            InterviewSessionEntity saved = service.saveSession("session-1", resumeId, 1, questions, template);

            assertNotNull(saved);
            assertNotNull(saved.getQuestionsJson());
            assertNotNull(saved.getTemplateJson());
            assertTrue(saved.getQuestionsJson().contains("Q1"));
            verify(sessionRepository).save(any(InterviewSessionEntity.class));
        }

        @Test
        @DisplayName("简历不存在时抛出 RESUME_NOT_FOUND")
        void shouldThrowWhenResumeNotFound() {
            when(resumeRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

            assertThrows(BusinessException.class, () ->
                service.saveSession("session-x", 99L, 1, List.of(),
                    InterviewTemplateConfig.defaultBackend(1)));
        }
    }

    @Nested
    @DisplayName("updateQuestionsJson 更新问题列表（追问持久化核心）")
    class UpdateQuestionsJson {

        @Test
        @DisplayName("追问生成后回写 questionsJson 和 totalQuestions")
        void shouldUpdateQuestionsJsonAndTotalQuestions() {
            InterviewSessionEntity session = new InterviewSessionEntity();
            session.setSessionId("session-1");
            when(sessionRepository.findBySessionIdAndUserId("session-1", 1L))
                .thenReturn(Optional.of(session));
            when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            List<InterviewQuestionDTO> questions = List.of(
                InterviewQuestionDTO.create(0, "Q1", InterviewQuestionDTO.QuestionType.JAVA_BASIC, "Java基础"),
                InterviewQuestionDTO.create(1, "追问1", InterviewQuestionDTO.QuestionType.JAVA_BASIC, "Java基础", true, 0)
            );

            service.updateQuestionsJson("session-1", questions);

            assertNotNull(session.getQuestionsJson());
            assertTrue(session.getQuestionsJson().contains("追问1"));
            assertEquals(2, session.getTotalQuestions());
            verify(sessionRepository).save(session);
        }

        @Test
        @DisplayName("会话不存在时不抛异常")
        void shouldNotThrowWhenSessionNotFound() {
            when(sessionRepository.findBySessionIdAndUserId("missing", 1L)).thenReturn(Optional.empty());

            // 鉴权后未找到会话会抛出异常
            assertThrows(BusinessException.class, () ->
                service.updateQuestionsJson("missing", List.of()));
            verify(sessionRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("saveAnswer 保存答案")
    class SaveAnswer {

        @Test
        @DisplayName("已有答案时执行 upsert 更新")
        void shouldUpsertExistingAnswer() {
            InterviewSessionEntity session = new InterviewSessionEntity();
            session.setSessionId("session-1");
            InterviewAnswerEntity existing = new InterviewAnswerEntity();
            existing.setSession(session);
            existing.setQuestionIndex(0);
            when(sessionRepository.findBySessionIdAndUserId("session-1", 1L))
                .thenReturn(Optional.of(session));
            when(answerRepository.findBySession_SessionIdAndQuestionIndex("session-1", 0))
                .thenReturn(Optional.of(existing));
            when(answerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            InterviewAnswerEntity result = service.saveAnswer(
                "session-1", 0, "Q1", "Java基础", "my answer", 80, "good", 30);

            assertEquals("my answer", result.getUserAnswer());
            assertEquals(80, result.getScore());
            assertEquals(30, result.getAnswerDurationSeconds());
            verify(answerRepository).save(existing);
        }

        @Test
        @DisplayName("无已有答案时创建新记录")
        void shouldCreateNewAnswer() {
            InterviewSessionEntity session = new InterviewSessionEntity();
            session.setSessionId("session-1");
            when(sessionRepository.findBySessionIdAndUserId("session-1", 1L))
                .thenReturn(Optional.of(session));
            when(answerRepository.findBySession_SessionIdAndQuestionIndex("session-1", 1))
                .thenReturn(Optional.empty());
            when(answerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            InterviewAnswerEntity result = service.saveAnswer(
                "session-1", 1, "Q2", "MySQL", "answer", 0, null, 10);

            assertNotNull(result);
            assertEquals(1, result.getQuestionIndex());
            verify(answerRepository).save(any(InterviewAnswerEntity.class));
        }

        @Test
        @DisplayName("会话不存在时抛出 INTERVIEW_SESSION_NOT_FOUND")
        void shouldThrowWhenSessionNotFound() {
            when(sessionRepository.findBySessionIdAndUserId("missing", 1L)).thenReturn(Optional.empty());

            assertThrows(BusinessException.class, () ->
                service.saveAnswer("missing", 0, "Q", "cat", "ans", 0, null, 5));
        }
    }

    @Nested
    @DisplayName("updateEvaluateStatus 评估状态更新")
    class UpdateEvaluateStatus {

        @Test
        @DisplayName("错误信息超过 500 字符时截断")
        void shouldTruncateLongError() {
            InterviewSessionEntity session = new InterviewSessionEntity();
            session.setSessionId("session-1");
            when(sessionRepository.findBySessionIdAndUserId("session-1", 1L))
                .thenReturn(Optional.of(session));
            when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            String longError = "x".repeat(600);
            service.updateEvaluateStatus("session-1", AsyncTaskStatus.FAILED, longError);

            assertEquals(500, session.getEvaluateError().length());
            assertEquals(AsyncTaskStatus.FAILED, session.getEvaluateStatus());
        }

        @Test
        @DisplayName("错误为 null 时清除 evaluateError")
        void shouldClearErrorWhenNull() {
            InterviewSessionEntity session = new InterviewSessionEntity();
            session.setSessionId("session-1");
            session.setEvaluateError("old error");
            when(sessionRepository.findBySessionIdAndUserId("session-1", 1L))
                .thenReturn(Optional.of(session));
            when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.updateEvaluateStatus("session-1", AsyncTaskStatus.PENDING, null);

            assertNull(session.getEvaluateError());
        }
    }

    @Nested
    @DisplayName("getHistoricalQuestionsByResumeId 历史问题去重")
    class GetHistoricalQuestions {

        @Test
        @DisplayName("过滤追问、去重、限制 30 条")
        void shouldFilterFollowUpsAndDeduplicate() throws Exception {
            // 构造两个会话，每个含主问题和追问
            InterviewSessionEntity session1 = new InterviewSessionEntity();
            session1.setSessionId("s1");
            session1.setQuestionsJson(objectMapper.writeValueAsString(List.of(
                InterviewQuestionDTO.create(0, "主问题A", InterviewQuestionDTO.QuestionType.JAVA_BASIC, "Java基础"),
                InterviewQuestionDTO.create(1, "追问A1", InterviewQuestionDTO.QuestionType.JAVA_BASIC, "Java基础", true, 0)
            )));

            InterviewSessionEntity session2 = new InterviewSessionEntity();
            session2.setSessionId("s2");
            session2.setQuestionsJson(objectMapper.writeValueAsString(List.of(
                InterviewQuestionDTO.create(0, "主问题A", InterviewQuestionDTO.QuestionType.JAVA_BASIC, "Java基础"), // 重复
                InterviewQuestionDTO.create(1, "主问题B", InterviewQuestionDTO.QuestionType.MYSQL, "MySQL")
            )));

            when(sessionRepository.findTop10ByResumeIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(session2, session1));

            List<String> result = service.getHistoricalQuestionsByResumeId(1L);

            // 去重后只有 "主问题A" 和 "主问题B"，追问被过滤
            assertEquals(2, result.size());
            assertTrue(result.contains("主问题A"));
            assertTrue(result.contains("主问题B"));
            assertFalse(result.contains("追问A1"));
        }
    }
}
