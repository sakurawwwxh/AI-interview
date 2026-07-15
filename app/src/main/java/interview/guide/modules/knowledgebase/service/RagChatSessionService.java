package interview.guide.modules.knowledgebase.service;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.infrastructure.mapper.KnowledgeBaseMapper;
import interview.guide.infrastructure.mapper.RagChatMapper;
import interview.guide.modules.dify.client.DifyApiClient;
import interview.guide.modules.knowledgebase.model.KnowledgeBaseEntity;
import interview.guide.modules.knowledgebase.model.KnowledgeBaseListItemDTO;
import interview.guide.modules.knowledgebase.model.RagChatDTO.CreateSessionRequest;
import interview.guide.modules.knowledgebase.model.RagChatDTO.SessionDTO;
import interview.guide.modules.knowledgebase.model.RagChatDTO.SessionDetailDTO;
import interview.guide.modules.knowledgebase.model.RagChatDTO.SessionListItemDTO;
import interview.guide.modules.knowledgebase.model.RagChatMessageEntity;
import interview.guide.modules.knowledgebase.model.RagChatSessionEntity;
import interview.guide.modules.knowledgebase.repository.KnowledgeBaseRepository;
import interview.guide.modules.knowledgebase.repository.RagChatMessageRepository;
import interview.guide.modules.knowledgebase.repository.RagChatSessionRepository;
import interview.guide.modules.user.security.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * RAG 聊天会话服务
 * 提供RAG聊天会话的创建、获取、更新、删除等操作
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagChatSessionService {

    private final RagChatSessionRepository sessionRepository;
    private final RagChatMessageRepository messageRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KnowledgeBaseQueryService queryService;
    private final RagChatMapper ragChatMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final DifyApiClient difyApiClient;

    /**
     * 创建新会话
     */
    @Transactional
    public SessionDTO createSession(CreateSessionRequest request) {
        // 知识库列表可为空（Dify 模式不需要选知识库）
        List<Long> kbIds = request.knowledgeBaseIds() != null ? request.knowledgeBaseIds() : List.of();
        List<KnowledgeBaseEntity> knowledgeBases = kbIds.isEmpty()
            ? List.of()
            : knowledgeBaseRepository.findAllByIdInAndUserId(kbIds, UserContext.getCurrentUserIdOrThrow());

        if (knowledgeBases.size() != kbIds.size()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "部分知识库不存在");
        }

        // 创建会话
        RagChatSessionEntity session = new RagChatSessionEntity();
        session.setUserId(UserContext.getCurrentUserIdOrThrow());
        session.setTitle(request.title() != null && !request.title().isBlank()
            ? request.title()
            : generateTitle(knowledgeBases));
        session.setKnowledgeBases(new HashSet<>(knowledgeBases));

        session = sessionRepository.save(session);

        log.info("创建 RAG 聊天会话: id={}, title={}", session.getId(), session.getTitle());

        return ragChatMapper.toSessionDTO(session);
    }

    /**
     * 获取会话列表
     */
    public List<SessionListItemDTO> listSessions() {
        return sessionRepository.findAllByUserIdOrderByPinnedAndUpdatedAtDesc(
                UserContext.getCurrentUserIdOrThrow())
            .stream()
            .map(ragChatMapper::toSessionListItemDTO)
            .toList();
    }

    /**
     * 获取会话详情（包含消息）
     * 分两次查询避免笛卡尔积问题
     */
    public SessionDetailDTO getSessionDetail(Long sessionId) {
        // 先加载会话和知识库（含归属校验）
        RagChatSessionEntity session = requireOwnedSessionWithKnowledgeBases(sessionId);

        // 再单独加载消息（避免笛卡尔积）
        List<RagChatMessageEntity> messages = messageRepository
            .findBySessionIdOrderByMessageOrderAsc(sessionId);

        // 转换知识库列表
        List<KnowledgeBaseListItemDTO> kbDTOs = knowledgeBaseMapper.toListItemDTOList(
            new java.util.ArrayList<>(session.getKnowledgeBases())
        );

        return ragChatMapper.toSessionDetailDTO(session, messages, kbDTOs);
    }

    /**
     * 准备流式消息（保存用户消息，创建 AI 消息占位）
     *
     * @return AI 消息的 ID
     */
    @Transactional
    public Long prepareStreamMessage(Long sessionId, String question) {
        RagChatSessionEntity session = requireOwnedSessionWithKnowledgeBases(sessionId);

        // 获取当前消息数量作为起始顺序
        int nextOrder = session.getMessageCount();

        // 保存用户消息
        RagChatMessageEntity userMessage = new RagChatMessageEntity();
        userMessage.setSession(session);
        userMessage.setType(RagChatMessageEntity.MessageType.USER);
        userMessage.setContent(question);
        userMessage.setMessageOrder(nextOrder);
        userMessage.setCompleted(true);
        messageRepository.save(userMessage);

        // 创建 AI 消息占位（未完成）
        RagChatMessageEntity assistantMessage = new RagChatMessageEntity();
        assistantMessage.setSession(session);
        assistantMessage.setType(RagChatMessageEntity.MessageType.ASSISTANT);
        assistantMessage.setContent("");
        assistantMessage.setMessageOrder(nextOrder + 1);
        assistantMessage.setCompleted(false);
        assistantMessage = messageRepository.save(assistantMessage);

        // 更新会话消息数量
        session.setMessageCount(nextOrder + 2);
        sessionRepository.save(session);

        log.info("准备流式消息: sessionId={}, messageId={}", sessionId, assistantMessage.getId());

        return assistantMessage.getId();
    }

    /**
     * 流式响应完成后更新消息
     */
    @Transactional
    public void completeStreamMessage(Long messageId, String content) {
        RagChatMessageEntity message = messageRepository.findById(messageId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "消息不存在"));

        message.setContent(content);
        message.setCompleted(true);
        messageRepository.save(message);

        log.info("完成流式消息: messageId={}, contentLength={}", messageId, content.length());
    }

    /**
     * 获取流式回答（注入多轮对话历史）
     */
    public Flux<String> getStreamAnswer(Long sessionId, String question) {
        RagChatSessionEntity session = requireOwnedSessionWithKnowledgeBases(sessionId);

        List<Long> kbIds = session.getKnowledgeBaseIds();

        // 查询最近的历史消息用于多轮对话上下文
        // prepareStreamMessage 已保存当前用户消息和 AI 占位，这里排除它们（取倒数第 3 条往前）
        List<Message> history = getRecentHistory(sessionId);

        return queryService.answerQuestionStream(kbIds, question, history);
    }

    /**
     * 获取 Dify 流式回答（通过 Dify 聊天助手 API）
     *
     * <p>与 {@link #getStreamAnswer} 结构一致，但不走本地 RAG，而是调 Dify chat-messages API。
     * 对话历史通过 Dify 的 conversation_id 管理：首次为空，后续从会话实体读取 Dify 返回的
     * conversation_id 传入，从而在 Dify 侧恢复多轮对话上下文。本地 session 仍用于保存
     * 消息记录，便于前端展示历史。
     *
     * @param sessionId           本地会话 ID
     * @param question            用户问题
     * @param conversationIdSink 回调，Dify 返回 conversation_id 时调用以便持久化
     * @return 流式回答 Flux
     */
    public Flux<String> getDifyStreamAnswer(Long sessionId, String question,
                                            java.util.function.Consumer<String> conversationIdSink) {
        RagChatSessionEntity session = requireOwnedSessionWithKnowledgeBases(sessionId);

        // 从会话实体读取已持久化的 Dify conversation_id（恢复多轮上下文）
        String existingConvId = session.getDifyConversationId();

        return difyApiClient.chatStream(question, existingConvId, conversationIdSink);
    }

    /**
     * 持久化 Dify 返回的 conversation_id 到会话实体
     *
     * <p>首次 Dify 对话结束后由 Dify 返回 conversation_id，后续请求需携带此 ID 才能
     * 在 Dify 侧恢复多轮上下文。此方法将其写回本地会话实体。
     *
     * @param sessionId       本地会话 ID
     * @param conversationId  Dify 返回的 conversation_id
     */
    @Transactional
    public void updateDifyConversationId(Long sessionId, String conversationId) {
        if (sessionId == null || conversationId == null || conversationId.isBlank()) {
            return;
        }
        try {
            sessionRepository.findById(sessionId).ifPresent(session -> {
                session.setDifyConversationId(conversationId);
                sessionRepository.save(session);
                log.info("持久化 Dify conversation_id: sessionId={}, conversationId={}",
                    sessionId, conversationId);
            });
        } catch (Exception e) {
            log.warn("持久化 Dify conversation_id 失败: sessionId={}, error={}",
                sessionId, e.getMessage());
        }
    }

    /**
     * 获取会话最近的历史对话消息（排除当前轮次的用户消息和 AI 占位）
     *
     * <p>取最近 MAX_HISTORY_MESSAGES 条已完成的消息，按时间正序返回。
     */
    private List<Message> getRecentHistory(Long sessionId) {
        try {
            // 多取 2 条来排除当前轮次的 user + assistant 占位
            int fetchCount = 8;
            List<RagChatMessageEntity> recent = messageRepository
                .findBySessionIdOrderByMessageOrderDesc(sessionId, PageRequest.of(0, fetchCount));

            // 过滤掉未完成的 AI 占位消息，反转成正序，最多取 6 条
            List<Message> history = new ArrayList<>();
            for (RagChatMessageEntity msg : recent) {
                if (!msg.getCompleted()) {
                    continue; // 跳过未完成（当前轮次的 AI 占位）
                }
                if (msg.getContent() == null || msg.getContent().isBlank()) {
                    continue;
                }
                Message chatMsg = msg.getType() == RagChatMessageEntity.MessageType.USER
                    ? new UserMessage(msg.getContent())
                    : new AssistantMessage(msg.getContent());
                history.add(0, chatMsg); // 反转为正序
            }

            // 最多取 6 条
            if (history.size() > 6) {
                history = history.subList(history.size() - 6, history.size());
            }

            log.debug("多轮对话历史: sessionId={}, historySize={}", sessionId, history.size());
            return history;
        } catch (Exception e) {
            log.warn("获取历史消息失败，降级为无历史: sessionId={}, error={}", sessionId, e.getMessage());
            return List.of();
        }
    }

    /**
     * 更新会话标题
     */
    @Transactional
    public void updateSessionTitle(Long sessionId, String title) {
        RagChatSessionEntity session = requireOwnedSession(sessionId);

        session.setTitle(title);
        sessionRepository.save(session);

        log.info("更新会话标题: sessionId={}, title={}", sessionId, title);
    }

    /**
     * 切换会话置顶状态
     */
    @Transactional
    public void togglePin(Long sessionId) {
        RagChatSessionEntity session = requireOwnedSession(sessionId);

        // 处理 null 值（兼容旧数据）
        Boolean currentPinned = session.getIsPinned() != null ? session.getIsPinned() : false;
        session.setIsPinned(!currentPinned);
        sessionRepository.save(session);

        log.info("切换会话置顶状态: sessionId={}, isPinned={}", sessionId, session.getIsPinned());
    }

    /**
     * 更新会话的知识库关联
     */
    @Transactional
    public void updateSessionKnowledgeBases(Long sessionId, List<Long> knowledgeBaseIds) {
        RagChatSessionEntity session = requireOwnedSession(sessionId);

        List<KnowledgeBaseEntity> knowledgeBases = knowledgeBaseRepository
            .findAllByIdInAndUserId(knowledgeBaseIds, UserContext.getCurrentUserIdOrThrow());

        if (knowledgeBases.size() != knowledgeBaseIds.size()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "部分知识库不存在或无权访问");
        }

        session.setKnowledgeBases(new HashSet<>(knowledgeBases));
        sessionRepository.save(session);

        log.info("更新会话知识库: sessionId={}, kbIds={}", sessionId, knowledgeBaseIds);
    }

    /**
     * 删除会话
     */
    @Transactional
    public void deleteSession(Long sessionId) {
        // 鉴权：仅会话所有者可删除
        RagChatSessionEntity session = requireOwnedSession(sessionId);
        sessionRepository.delete(session);

        log.info("删除会话: sessionId={}", sessionId);
    }

    // ========== 私有方法 ==========

    /**
     * 加载会话并校验归属（不加载知识库关联）
     */
    private RagChatSessionEntity requireOwnedSession(Long sessionId) {
        Long userId = UserContext.getCurrentUserIdOrThrow();
        return sessionRepository.findByIdAndUserId(sessionId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "会话不存在"));
    }

    /**
     * 加载会话（带知识库关联）并校验归属
     */
    private RagChatSessionEntity requireOwnedSessionWithKnowledgeBases(Long sessionId) {
        Long userId = UserContext.getCurrentUserIdOrThrow();
        return sessionRepository.findByIdWithKnowledgeBasesAndUserId(sessionId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "会话不存在"));
    }

    private String generateTitle(List<KnowledgeBaseEntity> knowledgeBases) {
        if (knowledgeBases.isEmpty()) {
            return "新对话";
        }
        if (knowledgeBases.size() == 1) {
            return knowledgeBases.getFirst().getName();
        }
        return knowledgeBases.size() + " 个知识库对话";
    }
}
