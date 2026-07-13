package interview.guide.modules.user.service;

import interview.guide.common.exception.BusinessException;
import interview.guide.infrastructure.redis.RedisService;
import interview.guide.modules.user.model.AuthDTO;
import interview.guide.modules.user.model.UserEntity;
import interview.guide.modules.user.repository.UserRepository;
import interview.guide.modules.user.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    @Test
    void updatesDisplayNameAndEmailForCurrentUser() {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        RedisService redisService = mock(RedisService.class);
        UserEntity user = user(1L, "login-name", "old@example.com", "encoded-old");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmailAndIdNot("new@example.com", 1L)).thenReturn(false);
        when(userRepository.save(user)).thenReturn(user);

        AuthDTO.UserInfo updated = service(userRepository, passwordEncoder, redisService)
            .updateProfile(1L, new AuthDTO.UpdateProfileRequest("  Interviewer  ", " new@example.com "));

        assertEquals("Interviewer", user.getDisplayName());
        assertEquals("new@example.com", user.getEmail());
        assertEquals("Interviewer", updated.displayName());
        verify(userRepository).save(user);
    }

    @Test
    void rejectsEmailOwnedByAnotherUser() {
        UserRepository userRepository = mock(UserRepository.class);
        UserEntity user = user(1L, "login-name", "old@example.com", "encoded-old");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmailAndIdNot("taken@example.com", 1L)).thenReturn(true);

        assertThrows(BusinessException.class, () -> service(userRepository, mock(PasswordEncoder.class), mock(RedisService.class))
            .updateProfile(1L, new AuthDTO.UpdateProfileRequest("Name", "taken@example.com")));
        verify(userRepository, never()).save(user);
    }

    @Test
    void changesPasswordAndRevokesAllRefreshTokens() {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        RedisService redisService = mock(RedisService.class);
        UserEntity user = user(1L, "login-name", "old@example.com", "encoded-old");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("current-password", "encoded-old")).thenReturn(true);
        when(passwordEncoder.encode("new-password")).thenReturn("encoded-new");

        service(userRepository, passwordEncoder, redisService)
            .changePassword(1L, new AuthDTO.ChangePasswordRequest("current-password", "new-password"));

        assertEquals("encoded-new", user.getPassword());
        verify(userRepository).save(user);
        verify(redisService).deleteByPattern("refresh_token:1:*");
    }

    @Test
    void refusesPasswordChangeWhenCurrentPasswordIsWrong() {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        RedisService redisService = mock(RedisService.class);
        UserEntity user = user(1L, "login-name", "old@example.com", "encoded-old");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "encoded-old")).thenReturn(false);

        assertThrows(BusinessException.class, () -> service(userRepository, passwordEncoder, redisService)
            .changePassword(1L, new AuthDTO.ChangePasswordRequest("wrong", "new-password")));
        verify(userRepository, never()).save(user);
        verify(redisService, never()).deleteByPattern("refresh_token:1:*");
    }

    private AuthService service(UserRepository userRepository, PasswordEncoder passwordEncoder, RedisService redisService) {
        return new AuthService(userRepository, passwordEncoder, mock(JwtUtil.class), redisService, 604800);
    }

    private UserEntity user(Long id, String username, String email, String password) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(password);
        return user;
    }
}
