package interview.guide.modules.userai.repository;
import interview.guide.modules.userai.model.UserAiConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface UserAiConfigRepository extends JpaRepository<UserAiConfigEntity, Long> { Optional<UserAiConfigEntity> findByUserId(Long userId); }
