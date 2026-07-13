package interview.guide.modules.user.repository;

import interview.guide.modules.user.model.UserEntity;
import interview.guide.modules.user.model.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 用户 Repository
 */
@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {

    Optional<UserEntity> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);

    Page<UserEntity> findByUsernameContainingIgnoreCase(String keyword, Pageable pageable);

    long countByRole(UserRole role);
}
