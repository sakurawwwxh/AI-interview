package interview.guide.modules.userai.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_ai_configs", indexes = @Index(name = "idx_user_ai_config_user", columnList = "user_id", unique = true))
public class UserAiConfigEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(nullable = false, length = 40) private String provider;
    @Column(nullable = false, length = 500) private String baseUrl;
    @Column(nullable = false, length = 160) private String model;
    @Column(nullable = false, columnDefinition = "TEXT") private String encryptedApiKey;
    @Column(nullable = false) private boolean fallbackToPlatform = true;
    @Column(nullable = false) private LocalDateTime updatedAt;
    @PrePersist @PreUpdate void touch() { updatedAt = LocalDateTime.now(); }
    public Long getId(){return id;} public Long getUserId(){return userId;} public void setUserId(Long v){userId=v;}
    public String getProvider(){return provider;} public void setProvider(String v){provider=v;}
    public String getBaseUrl(){return baseUrl;} public void setBaseUrl(String v){baseUrl=v;}
    public String getModel(){return model;} public void setModel(String v){model=v;}
    public String getEncryptedApiKey(){return encryptedApiKey;} public void setEncryptedApiKey(String v){encryptedApiKey=v;}
    public boolean isFallbackToPlatform(){return fallbackToPlatform;} public void setFallbackToPlatform(boolean v){fallbackToPlatform=v;}
    public LocalDateTime getUpdatedAt(){return updatedAt;}
}
