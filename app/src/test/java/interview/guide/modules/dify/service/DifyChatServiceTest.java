package interview.guide.modules.dify.service;

import interview.guide.modules.dify.client.DifyApiClient;
import interview.guide.modules.dify.config.DifyConfig;
import interview.guide.modules.dify.model.DifyChatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DifyChatServiceTest {

    @Mock
    private DifyApiClient difyApiClient;

    @Mock
    private DifyConfig config;

    @InjectMocks
    private DifyChatService difyChatService;

    @BeforeEach
    void setUp() {
        when(config.getDatasetId()).thenReturn("test-dataset-id");
    }

    @Test
    void chat_shouldReturnResponse() {
        // Given
        String query = "什么是 Java?";
        String conversationId = null;
        List<Long> knowledgeBaseIds = List.of(1L, 2L);

        DifyChatResponse expectedResponse = DifyChatResponse.builder()
            .answer("Java 是一种编程语言")
            .conversationId("conv-123")
            .retrievalSources(List.of())
            .build();

        when(difyApiClient.chat(anyString(), any(), anyMap()))
            .thenReturn(expectedResponse);

        // When
        DifyChatResponse response = difyChatService.chat(query, conversationId, knowledgeBaseIds);

        // Then
        assertNotNull(response);
        assertEquals("Java 是一种编程语言", response.getAnswer());
        assertEquals("conv-123", response.getConversationId());
    }

    @Test
    void chat_withConversationId_shouldPassToApi() {
        // Given
        String query = "什么是 Java?";
        String conversationId = "conv-456";
        List<Long> knowledgeBaseIds = List.of(1L);

        DifyChatResponse expectedResponse = DifyChatResponse.builder()
            .answer("Java 是一种编程语言")
            .conversationId("conv-456")
            .build();

        when(difyApiClient.chat(eq(query), eq(conversationId), anyMap()))
            .thenReturn(expectedResponse);

        // When
        DifyChatResponse response = difyChatService.chat(query, conversationId, knowledgeBaseIds);

        // Then
        assertNotNull(response);
        verify(difyApiClient).chat(eq(query), eq(conversationId), anyMap());
    }

    @Test
    void chat_withNullQuery_shouldThrowException() {
        // Given
        String query = null;
        String conversationId = null;
        List<Long> knowledgeBaseIds = List.of(1L);

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            difyChatService.chat(query, conversationId, knowledgeBaseIds);
        });
    }

    @Test
    void chat_withEmptyQuery_shouldThrowException() {
        // Given
        String query = "   ";
        String conversationId = null;
        List<Long> knowledgeBaseIds = List.of(1L);

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            difyChatService.chat(query, conversationId, knowledgeBaseIds);
        });
    }

    @Test
    void chat_withEmptyKnowledgeBaseIds_shouldPassEmptyInputs() {
        // Given
        String query = "什么是 Java?";
        String conversationId = null;
        List<Long> knowledgeBaseIds = List.of();

        DifyChatResponse expectedResponse = DifyChatResponse.builder()
            .answer("Java 是一种编程语言")
            .conversationId("conv-789")
            .build();

        when(difyApiClient.chat(eq(query), eq(conversationId), anyMap()))
            .thenReturn(expectedResponse);

        // When
        DifyChatResponse response = difyChatService.chat(query, conversationId, knowledgeBaseIds);

        // Then
        assertNotNull(response);
        verify(difyApiClient).chat(eq(query), eq(conversationId), argThat(inputs -> inputs.isEmpty()));
    }
}
