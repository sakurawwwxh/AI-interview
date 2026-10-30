package ai.interview.modules.interview.service;

import ai.interview.modules.knowledgebase.model.VectorStatus;
import ai.interview.modules.knowledgebase.repository.KnowledgeBaseRepository;
import ai.interview.modules.knowledgebase.service.KnowledgeBaseVectorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewEvaluationEvidenceServiceTest {

    @Mock private KnowledgeBaseRepository knowledgeBaseRepository;
    @Mock private KnowledgeBaseVectorService vectorService;

    private InterviewEvaluationEvidenceService service;

    @BeforeEach
    void setUp() {
        service = new InterviewEvaluationEvidenceService(knowledgeBaseRepository, vectorService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("只返回当前用户的知识库，不再全表加载")
    void scopesEvidenceToCurrentUser() {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(7L, "tester", List.of()));
        when(knowledgeBaseRepository.findIdsByUserIdAndVectorStatus(7L, VectorStatus.COMPLETED))
            .thenReturn(List.of(11L));

        assertEquals(List.of(11L), service.getAvailableKnowledgeBaseIds());
        verify(knowledgeBaseRepository).findIdsByUserIdAndVectorStatus(7L, VectorStatus.COMPLETED);
        verify(knowledgeBaseRepository, org.mockito.Mockito.never()).findAll();
    }

    @Test
    @DisplayName("没有用户上下文时不取任何知识库")
    void returnsEmptyWithoutUserContext() {
        assertTrue(service.getAvailableKnowledgeBaseIds().isEmpty());
        verifyNoInteractions(knowledgeBaseRepository);
    }
}
