package ai.interview.modules.knowledgebase.service;

import ai.interview.common.exception.BusinessException;
import ai.interview.infrastructure.mapper.KnowledgeBaseMapper;
import ai.interview.infrastructure.mapper.RagChatMapper;
import ai.interview.modules.dify.client.DifyApiClient;
import ai.interview.modules.knowledgebase.model.RagChatMessageEntity;
import ai.interview.modules.knowledgebase.model.RagChatSessionEntity;
import ai.interview.modules.knowledgebase.repository.KnowledgeBaseRepository;
import ai.interview.modules.knowledgebase.repository.RagChatMessageRepository;
import ai.interview.modules.knowledgebase.repository.RagChatSessionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RagChatSessionServiceTest {

    private final RagChatSessionRepository sessionRepository = mock(RagChatSessionRepository.class);
    private final RagChatMessageRepository messageRepository = mock(RagChatMessageRepository.class);
    private final KnowledgeBaseRepository knowledgeBaseRepository = mock(KnowledgeBaseRepository.class);
    private final KnowledgeBaseQueryService queryService = mock(KnowledgeBaseQueryService.class);
    private final RagChatMapper ragChatMapper = mock(RagChatMapper.class);
    private final KnowledgeBaseMapper knowledgeBaseMapper = mock(KnowledgeBaseMapper.class);
    private final DifyApiClient difyApiClient = mock(DifyApiClient.class);

    private final RagChatSessionService service = new RagChatSessionService(
        sessionRepository, messageRepository, knowledgeBaseRepository, queryService,
        ragChatMapper, knowledgeBaseMapper, difyApiClient);

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(1L, "test-user", List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("会话存在未完成回答时拒绝第二个流式请求")
    void shouldRejectConcurrentStreamForTheSameSession() {
        RagChatSessionEntity session = session(7L, 2);
        RagChatMessageEntity pending = new RagChatMessageEntity();
        pending.setCompleted(false);
        when(sessionRepository.findByIdAndUserIdForUpdate(7L, 1L)).thenReturn(Optional.of(session));
        when(messageRepository.findBySessionIdAndCompletedFalse(7L)).thenReturn(List.of(pending));

        assertThrows(BusinessException.class, () -> service.prepareStreamMessage(7L, "question"));

        verify(messageRepository, never()).save(any());
        verify(sessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("流式写入通过锁定查询分配连续消息序号")
    void shouldAllocateMessageOrdersUnderLockedSession() {
        RagChatSessionEntity session = session(7L, 2);
        when(sessionRepository.findByIdAndUserIdForUpdate(7L, 1L)).thenReturn(Optional.of(session));
        when(messageRepository.findBySessionIdAndCompletedFalse(7L)).thenReturn(List.of());
        when(messageRepository.save(any(RagChatMessageEntity.class))).thenAnswer(invocation -> {
            RagChatMessageEntity message = invocation.getArgument(0);
            if (message.getType() == RagChatMessageEntity.MessageType.ASSISTANT) {
                message.setId(42L);
            }
            return message;
        });

        Long messageId = service.prepareStreamMessage(7L, "question");

        ArgumentCaptor<RagChatMessageEntity> messageCaptor = ArgumentCaptor.forClass(RagChatMessageEntity.class);
        verify(messageRepository, times(2)).save(messageCaptor.capture());
        assertEquals(List.of(2, 3), messageCaptor.getAllValues().stream()
            .map(RagChatMessageEntity::getMessageOrder)
            .toList());
        assertEquals(4, session.getMessageCount());
        assertEquals(42L, messageId);
        verify(sessionRepository).findByIdAndUserIdForUpdate(7L, 1L);
    }

    @Test
    @DisplayName("服务异常留下的超时占位消息会被回收")
    void shouldRecoverStalePendingMessage() {
        RagChatSessionEntity session = session(7L, 2);
        RagChatMessageEntity stale = new RagChatMessageEntity();
        stale.setId(41L);
        stale.setCompleted(false);
        stale.setCreatedAt(LocalDateTime.now().minusMinutes(11));
        stale.setUpdatedAt(LocalDateTime.now().minusMinutes(11));
        when(sessionRepository.findByIdAndUserIdForUpdate(7L, 1L)).thenReturn(Optional.of(session));
        when(messageRepository.findBySessionIdAndCompletedFalse(7L)).thenReturn(List.of(stale));
        when(messageRepository.save(any(RagChatMessageEntity.class))).thenAnswer(invocation -> {
            RagChatMessageEntity message = invocation.getArgument(0);
            if (message.getType() == RagChatMessageEntity.MessageType.ASSISTANT) {
                message.setId(42L);
            }
            return message;
        });

        Long messageId = service.prepareStreamMessage(7L, "new question");

        assertEquals(42L, messageId);
        assertEquals(true, stale.getCompleted());
        verify(messageRepository).save(stale);
    }

    @Test
    @DisplayName("完成消息时同时保存 Dify 会话 ID")
    void shouldPersistDifyConversationIdWithMessageCompletion() {
        RagChatSessionEntity session = session(7L, 2);
        RagChatMessageEntity message = new RagChatMessageEntity();
        message.setId(42L);
        message.setSession(session);
        message.setCompleted(false);
        when(messageRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(message));
        when(messageRepository.save(any(RagChatMessageEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.completeStreamMessage(42L, "answer", "dify-conversation-1");

        assertEquals(true, message.getCompleted());
        assertEquals("dify-conversation-1", session.getDifyConversationId());
        verify(sessionRepository).save(session);
    }

    @Test
    @DisplayName("旧流式回调不能覆盖已经回收的消息")
    void shouldIgnoreCompletionForAlreadyRecoveredMessage() {
        RagChatMessageEntity message = new RagChatMessageEntity();
        message.setId(42L);
        message.setContent("new answer");
        message.setCompleted(true);
        when(messageRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(message));

        service.completeStreamMessage(42L, "old answer", "old-conversation");

        assertEquals("new answer", message.getContent());
        verify(messageRepository, never()).save(any());
        verify(sessionRepository, never()).save(any());
    }

    private RagChatSessionEntity session(Long id, int messageCount) {
        RagChatSessionEntity session = new RagChatSessionEntity();
        session.setId(id);
        session.setUserId(1L);
        session.setMessageCount(messageCount);
        return session;
    }
}
