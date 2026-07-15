package interview.guide.modules.user.service;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.infrastructure.redis.RedisService;
import interview.guide.modules.user.model.AuthDTO;
import interview.guide.modules.user.model.AuthDTO.UserListItem;
import interview.guide.modules.user.model.AuthDTO.UserPage;
import interview.guide.modules.user.model.UserEntity;
import interview.guide.modules.user.model.UserRole;
import interview.guide.modules.user.repository.UserRepository;
import interview.guide.modules.user.security.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 管理员用户管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final RedisService redisService;

    @Transactional(readOnly = true)
    public UserPage listUsers(String keyword, int page, int size) {
        PageRequest pageable = PageRequest.of(
            Math.max(0, page),
            Math.min(Math.max(1, size), 100),
            Sort.by(Sort.Direction.DESC, "createdAt")
        );
        Page<UserEntity> result;
        if (keyword != null && !keyword.isBlank()) {
            result = userRepository.findByUsernameContainingIgnoreCase(keyword.trim(), pageable);
        } else {
            result = userRepository.findAll(pageable);
        }
        List<UserListItem> items = result.getContent().stream().map(this::toListItem).toList();
        return new UserPage(items, result.getTotalElements(), result.getNumber(), result.getSize());
    }

    @Transactional(readOnly = true)
    public AuthDTO.UserInfo getUser(Long id) {
        return toUserInfo(findUserOrThrow(id));
    }

    @Transactional
    public AuthDTO.UserInfo updateRole(Long id, UserRole role) {
        Long currentUserId = UserContext.getCurrentUserIdOrThrow();
        if (id.equals(currentUserId)) {
            throw new BusinessException(ErrorCode.CANNOT_MODIFY_SELF_ROLE);
        }
        UserEntity user = findUserOrThrow(id);

        // 从 ADMIN 降级为 USER 时，确保至少保留一个 ADMIN
        if (user.getRole() == UserRole.ADMIN && role == UserRole.USER
            && userRepository.countByRole(UserRole.ADMIN) <= 1) {
            throw new BusinessException(ErrorCode.LAST_ADMIN_PROTECTED);
        }

        user.setRole(role);
        userRepository.save(user);

        // 吊销目标用户所有 refresh token，强制重新登录后生效新角色
        redisService.deleteByPattern("refresh_token:" + id + ":*");
        log.info("管理员修改用户角色: targetId={}, newRole={}, operatorId={}", id, role, currentUserId);
        return toUserInfo(user);
    }

    @Transactional
    public AuthDTO.UserInfo updateQuota(Long id, Long dailyTokenQuota) {
        UserEntity user = findUserOrThrow(id);
        user.setDailyTokenQuota(dailyTokenQuota);
        userRepository.save(user);
        log.info("管理员修改用户配额: targetId={}, newQuota={}", id, dailyTokenQuota);
        return toUserInfo(user);
    }

    @Transactional
    public void deleteUser(Long id) {
        Long currentUserId = UserContext.getCurrentUserIdOrThrow();
        if (id.equals(currentUserId)) {
            throw new BusinessException(ErrorCode.CANNOT_DELETE_SELF);
        }
        UserEntity user = findUserOrThrow(id);
        if (user.getRole() == UserRole.ADMIN && userRepository.countByRole(UserRole.ADMIN) <= 1) {
            throw new BusinessException(ErrorCode.LAST_ADMIN_PROTECTED);
        }

        // 吊销所有 refresh token
        redisService.deleteByPattern("refresh_token:" + id + ":*");
        userRepository.delete(user);
        log.info("管理员删除用户: targetId={}, operatorId={}", id, currentUserId);
    }

    private UserEntity findUserOrThrow(Long id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private AuthDTO.UserInfo toUserInfo(UserEntity user) {
        return new AuthDTO.UserInfo(
            user.getId(), user.getUsername(), user.getDisplayName(),
            user.getEmail(), user.getRole(), user.getDailyTokenQuota()
        );
    }

    private UserListItem toListItem(UserEntity user) {
        return new UserListItem(
            user.getId(), user.getUsername(), user.getDisplayName(),
            user.getEmail(), user.getRole(), user.getDailyTokenQuota(),
            user.getCreatedAt()
        );
    }
}
