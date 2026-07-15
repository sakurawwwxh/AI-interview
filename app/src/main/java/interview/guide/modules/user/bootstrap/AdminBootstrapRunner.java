package interview.guide.modules.user.bootstrap;

import interview.guide.modules.user.model.UserEntity;
import interview.guide.modules.user.model.UserRole;
import interview.guide.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 启动时引导第一个管理员账号。
 * 若配置的用户名不存在则创建，已存在则跳过。
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class AdminBootstrapRunner implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.bootstrap.enabled:true}")
    private boolean enabled;

    @Value("${app.admin.bootstrap.username:admin}")
    private String username;

    @Value("${app.admin.bootstrap.password:admin123456}")
    private String password;

    @Value("${app.admin.bootstrap.email:admin@example.com}")
    private String email;

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.debug("管理员引导已禁用 (app.admin.bootstrap.enabled=false)");
            return;
        }

        // 已创建过管理员时属正常启动路径，勿用 INFO 刷屏
        if (userRepository.existsByUsername(username)) {
            log.debug("管理员账号已存在，跳过引导: username={}", username);
            return;
        }

        UserEntity admin = new UserEntity();
        admin.setUsername(username);
        admin.setPassword(passwordEncoder.encode(password));
        admin.setEmail(email);
        admin.setDisplayName("管理员");
        admin.setRole(UserRole.ADMIN);
        admin.setDailyTokenQuota(500000L);
        userRepository.save(admin);

        // 仅首次创建时打 INFO，便于确认引导成功
        log.info("管理员账号已创建: username={}, email={}", username, email);
    }
}
