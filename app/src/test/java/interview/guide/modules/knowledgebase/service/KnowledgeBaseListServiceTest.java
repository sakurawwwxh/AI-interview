package interview.guide.modules.knowledgebase.service;

import interview.guide.common.exception.BusinessException;
import interview.guide.infrastructure.file.FileStorageService;
import interview.guide.infrastructure.mapper.KnowledgeBaseMapper;
import interview.guide.modules.knowledgebase.model.KnowledgeBaseEntity;
import interview.guide.modules.knowledgebase.repository.KnowledgeBaseRepository;
import interview.guide.modules.knowledgebase.repository.RagChatMessageRepository;
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

    private KnowledgeBaseEntity knowledgeBase(Long id) {
        KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
        entity.setId(id);
        return entity;
    }
}
