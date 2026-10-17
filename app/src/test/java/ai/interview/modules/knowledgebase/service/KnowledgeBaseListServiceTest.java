package ai.interview.modules.knowledgebase.service;

import ai.interview.common.exception.BusinessException;
import ai.interview.infrastructure.file.FileStorageService;
import ai.interview.infrastructure.mapper.KnowledgeBaseMapper;
import ai.interview.modules.knowledgebase.model.KnowledgeBaseEntity;
import ai.interview.modules.knowledgebase.repository.KnowledgeBaseRepository;
import ai.interview.modules.knowledgebase.repository.RagChatMessageRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseListServiceTest {

    @Mock private KnowledgeBaseRepository knowledgeBaseRepository;
    @Mock private RagChatMessageRepository ragChatMessageRepository;
    @Mock private KnowledgeBaseMapper knowledgeBaseMapper;
    @Mock private FileStorageService fileStorageService;

    private KnowledgeBaseListService service;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(7L, "owner", List.of()));
        service = new KnowledgeBaseListService(
            knowledgeBaseRepository, ragChatMessageRepository, knowledgeBaseMapper, fileStorageService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void requireOwnedKnowledgeBaseIds_rejectsIdsNotOwnedByCurrentUser() {
        when(knowledgeBaseRepository.findAllByIdInAndUserId(List.of(11L, 22L), 7L))
            .thenReturn(List.of(knowledgeBase(11L)));

        assertThrows(BusinessException.class,
            () -> service.requireOwnedKnowledgeBaseIds(List.of(11L, 22L)));
    }

    @Test
    void requireOwnedKnowledgeBaseIds_deduplicatesOwnedIds() {
        when(knowledgeBaseRepository.findAllByIdInAndUserId(List.of(11L, 22L), 7L))
            .thenReturn(List.of(knowledgeBase(11L), knowledgeBase(22L)));

        assertEquals(List.of(11L, 22L),
            service.requireOwnedKnowledgeBaseIds(List.of(11L, 22L, 11L)));
    }

    @Test
    void getKnowledgeBaseNames_usesOneBatchQueryAndKeepsInputOrder() {
        when(knowledgeBaseRepository.findAllByIdInAndUserId(List.of(11L, 22L), 7L))
            .thenReturn(List.of(knowledgeBaseNamed(22L, "后端题库"), knowledgeBaseNamed(11L, "Java 基础")));

        // 一次批量取回后按 id 映射：顺序跟随入参，重复 id 也照样出现（原语义）
        assertEquals(List.of("Java 基础", "后端题库", "Java 基础"),
            service.getKnowledgeBaseNames(List.of(11L, 22L, 11L)));
    }

    @Test
    void getKnowledgeBaseNames_fallsBackForIdsNotAccessibleToCurrentUser() {
        when(knowledgeBaseRepository.findAllByIdInAndUserId(List.of(11L, 99L), 7L))
            .thenReturn(List.of(knowledgeBaseNamed(11L, "Java 基础")));

        assertEquals(List.of("Java 基础", "未知知识库"),
            service.getKnowledgeBaseNames(List.of(11L, 99L)));
    }

    @Test
    void getKnowledgeBaseNames_returnsEmptyWithoutTouchingTheRepository() {
        assertEquals(List.of(), service.getKnowledgeBaseNames(List.of()));
    }

    private KnowledgeBaseEntity knowledgeBase(Long id) {
        KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
        entity.setId(id);
        return entity;
    }

    private KnowledgeBaseEntity knowledgeBaseNamed(Long id, String name) {
        KnowledgeBaseEntity entity = knowledgeBase(id);
        entity.setName(name);
        return entity;
    }
}
