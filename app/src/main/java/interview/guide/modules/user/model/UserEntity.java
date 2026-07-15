package interview.guide.modules.user.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 用户实体
 */
@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_user_username", columnList = "username", unique = true),
    @Index(name = "idx_user_email", columnList = "email", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 用户名（唯一）
     */
    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(length = 50)
    private String displayName;

    /**
     * 密码（BCrypt 加密）
     */
    @Column(nullable = false, length = 100)
    private String password;

    /**
     * 邮箱（唯一）
     */
    @Column(nullable = false, unique = true, length = 100)
    private String email;

    /**
     * 角色
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role = UserRole.USER;

    /**
     * 每日 token 配额
     */
    @Column(nullable = false)
    private Long dailyTokenQuota = 500000L;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (displayName == null || displayName.isBlank()) {
            displayName = username;
        }
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
