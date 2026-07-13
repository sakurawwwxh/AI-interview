package interview.guide.modules.user.service;

import interview.guide.common.exception.BusinessException;
import interview.guide.infrastructure.redis.RedisService;
import interview.guide.modules.user.model.AuthDTO;
import interview.guide.modules.user.model.UserEntity;
import interview.guide.modules.user.model.UserRole;
import interview.guide.modules.user.repository.UserRepository;
import interview.guide.modules.user.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminUserServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final RedisService redisService = mock(RedisService.class);
    private final AdminUserService service = new AdminUserService(userRepository, redisService);

    private static final Long ADMIN_ID = 1L;

    @BeforeEach
    void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(ADMIN_ID, "admin", List.of()));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void cannotModifyOwnRole() {
        assertThrows(BusinessException.class,
            () -> service.updateRole(ADMIN_ID, UserRole.USER));
        verify(userRepository, never()).save(any());
    }

    @Test
    void cannotDemoteLastAdmin() {
        UserEntity target = user(2L, UserRole.ADMIN);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(userRepository.countByRole(UserRole.ADMIN)).thenReturn(1L);

        assertThrows(BusinessException.class,
            () -> service.updateRole(2L, UserRole.USER));
        verify(userRepository, never()).save(any());
    }

    @Test
    void demotingAdminRevokesRefreshTokens() {
        UserEntity target = user(2L, UserRole.ADMIN);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(userRepository.countByRole(UserRole.ADMIN)).thenReturn(2L);
        when(userRepository.save(target)).thenReturn(target);

        AuthDTO.UserInfo result = service.updateRole(2L, UserRole.USER);

        assertEquals(UserRole.USER, result.role());
        verify(redisService).deleteByPattern("refresh_token:2:*");
    }

    @Test
    void promotingUserToAdminRevokesRefreshTokens() {
        UserEntity target = user(2L, UserRole.USER);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(userRepository.save(target)).thenReturn(target);

        service.updateRole(2L, UserRole.ADMIN);

        verify(redisService).deleteByPattern("refresh_token:2:*");
    }

    @Test
    void cannotDeleteSelf() {
        assertThrows(BusinessException.class,
            () -> service.deleteUser(ADMIN_ID));
        verify(userRepository, never()).delete(any());
    }

    @Test
    void cannotDeleteLastAdmin() {
        UserEntity target = user(2L, UserRole.ADMIN);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(userRepository.countByRole(UserRole.ADMIN)).thenReturn(1L);

        assertThrows(BusinessException.class,
            () -> service.deleteUser(2L));
        verify(userRepository, never()).delete(any());
    }

    @Test
    void deletingUserRevokesRefreshTokens() {
        UserEntity target = user(2L, UserRole.USER);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(userRepository.countByRole(UserRole.ADMIN)).thenReturn(10L);

        service.deleteUser(2L);

        verify(redisService).deleteByPattern("refresh_token:2:*");
        verify(userRepository).delete(target);
    }

    @Test
    void updateQuotaPersistsNewValue() {
        UserEntity target = user(2L, UserRole.USER);
        target.setDailyTokenQuota(500000L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(userRepository.save(target)).thenReturn(target);

        AuthDTO.UserInfo result = service.updateQuota(2L, 1000000L);

        assertEquals(1000000L, result.dailyTokenQuota());
        verify(userRepository).save(target);
    }

    @Test
    void getNonExistentUserThrows() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () -> service.getUser(99L));
    }

    private UserEntity user(Long id, UserRole role) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setUsername("user" + id);
        user.setEmail("user" + id + "@example.com");
        user.setRole(role);
        user.setDailyTokenQuota(500000L);
        return user;
    }
}
