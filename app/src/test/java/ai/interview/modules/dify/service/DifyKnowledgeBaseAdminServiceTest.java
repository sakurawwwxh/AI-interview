package ai.interview.modules.dify.service;

import ai.interview.modules.knowledgebase.model.KnowledgeBaseEntity;
import ai.interview.modules.knowledgebase.repository.KnowledgeBaseRepository;
import ai.interview.modules.knowledgebase.service.KnowledgeBaseParseService;
import ai.interview.modules.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DifyKnowledgeBaseAdminServiceTest {

    @Mock private KnowledgeBaseRepository knowledgeBaseRepository;
    @Mock private UserRepository userRepository;
    @Mock private KnowledgeBaseParseService parseService;
    @Mock private DifySyncService difySyncService;

    @InjectMocks private DifyKnowledgeBaseAdminService service;

    private KnowledgeBaseEntity knowledgeBase;

    @BeforeEach
    void setUp() {
        knowledgeBase = new KnowledgeBaseEntity();
        knowledgeBase.setId(7L);
        knowledgeBase.setUserId(1L);
        knowledgeBase.setName("Java 并发");
        knowledgeBase.setOriginalFilename("java-concurrency.md");
        knowledgeBase.setCategory("Java");
        knowledgeBase.setStorageKey("knowledgebases/java-concurrency.md");
        knowledgeBase.setDifyDocumentId("dify-doc-7");
    }

    @Test
    void transferOwnerChangesOnlyTheLocalKnowledgeBaseOwner() {
        when(knowledgeBaseRepository.findById(7L)).thenReturn(Optional.of(knowledgeBase));
        when(userRepository.existsById(2L)).thenReturn(true);

        service.transferKnowledgeBaseOwner(7L, 2L);

        assertEquals(2L, knowledgeBase.getUserId());
        verify(knowledgeBaseRepository).save(knowledgeBase);
        verifyNoInteractions(difySyncService, parseService);
    }

    @Test
    void syncKnowledgeBaseReadsStoredContentAndDelegatesToAsyncSync() {
        when(knowledgeBaseRepository.findById(7L)).thenReturn(Optional.of(knowledgeBase));
        when(parseService.downloadAndParseContent("knowledgebases/java-concurrency.md", "java-concurrency.md"))
            .thenReturn("线程池与锁");

        service.syncKnowledgeBaseToDify(7L);

        verify(difySyncService).syncToDify(
            eq(7L), eq("Java 并发"), eq("java-concurrency.md"), eq("Java"),
            eq("线程池与锁"), eq("dify-doc-7")
        );
    }

    @Test
    void transferOwnerRejectsMissingTargetUser() {
        when(knowledgeBaseRepository.findById(7L)).thenReturn(Optional.of(knowledgeBase));
        when(userRepository.existsById(99L)).thenReturn(false);

        assertThrows(RuntimeException.class, () -> service.transferKnowledgeBaseOwner(7L, 99L));

        verify(knowledgeBaseRepository, never()).save(any());
    }
}
