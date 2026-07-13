package interview.guide.modules.resume.service;

import interview.guide.infrastructure.file.FileHashService;
import interview.guide.infrastructure.mapper.ResumeMapper;
import interview.guide.modules.resume.model.ResumeEntity;
import interview.guide.modules.resume.repository.ResumeAnalysisRepository;
import interview.guide.modules.resume.repository.ResumeRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResumePersistenceServiceTest {

    private final ResumeRepository resumeRepository = mock(ResumeRepository.class);
    private final FileHashService fileHashService = new FileHashService();
    private final ResumePersistenceService service = new ResumePersistenceService(
        resumeRepository,
        mock(ResumeAnalysisRepository.class),
        new ObjectMapper(),
        mock(ResumeMapper.class),
        fileHashService
    );

    @BeforeEach
    void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(42L, "tester", List.of())
        );
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void savesUserScopedHashSoLegacyGlobalHashConstraintsDoNotBlockAnotherUser() {
        MockMultipartFile file = file("resume.txt", "same resume content");
        when(resumeRepository.save(any(ResumeEntity.class))).thenAnswer(invocation -> {
            ResumeEntity saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        service.saveResume(file, "parsed resume", "resumes/key", "http://storage/key");

        ArgumentCaptor<ResumeEntity> captor = ArgumentCaptor.forClass(ResumeEntity.class);
        verify(resumeRepository).save(captor.capture());
        String rawHash = fileHashService.calculateHash(file);
        assertEquals(42L, captor.getValue().getUserId());
        assertEquals(fileHashService.calculateUserScopedHash(rawHash, 42L), captor.getValue().getFileHash());
    }

    @Test
    void findsBothLegacyRawHashesAndNewUserScopedHashesForTheSameUser() {
        MockMultipartFile file = file("resume.txt", "same resume content");
        String rawHash = fileHashService.calculateHash(file);
        String scopedHash = fileHashService.calculateUserScopedHash(rawHash, 42L);
        ResumeEntity existing = new ResumeEntity();
        when(resumeRepository.findByFileHashAndUserId(rawHash, 42L)).thenReturn(Optional.empty());
        when(resumeRepository.findByFileHashAndUserId(scopedHash, 42L)).thenReturn(Optional.of(existing));

        assertSame(existing, service.findExistingResume(file).orElseThrow());
    }

    private MockMultipartFile file(String name, String content) {
        return new MockMultipartFile("file", name, "text/plain", content.getBytes(StandardCharsets.UTF_8));
    }
}
