package ai.interview.modules.userai.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * BYOK 地址校验：HTTPS 本身不阻止访问内网，必须把私网/回环/元数据地址挡在保存之前。
 */
class UserAiChatClientFactoryTest {

    private final UserAiChatClientFactory factory = new UserAiChatClientFactory(null, null, null, null);

    @Test
    @DisplayName("公网 HTTPS 放行")
    void allowsPublicHttps() {
        assertDoesNotThrow(() -> factory.validateBaseUrl("https://8.8.8.8/v1"));
    }

    @Test
    @DisplayName("私网 HTTPS 拒绝")
    void rejectsPrivateNetwork() {
        assertThrows(IllegalArgumentException.class, () -> factory.validateBaseUrl("https://10.0.0.5/v1"));
        assertThrows(IllegalArgumentException.class, () -> factory.validateBaseUrl("https://192.168.1.10/v1"));
        assertThrows(IllegalArgumentException.class, () -> factory.validateBaseUrl("https://172.16.0.9/v1"));
    }

    @Test
    @DisplayName("云元数据地址与回环地址拒绝")
    void rejectsMetadataAndLoopback() {
        assertThrows(IllegalArgumentException.class, () -> factory.validateBaseUrl("https://169.254.169.254/latest"));
        assertThrows(IllegalArgumentException.class, () -> factory.validateBaseUrl("https://127.0.0.1/v1"));
        assertThrows(IllegalArgumentException.class, () -> factory.validateBaseUrl("https://[::1]/v1"));
    }

    @Test
    @DisplayName("HTTP 只放行显式本机地址")
    void onlyAllowsHttpForLocalHosts() {
        assertDoesNotThrow(() -> factory.validateBaseUrl("http://localhost:11434/v1"));
        assertDoesNotThrow(() -> factory.validateBaseUrl("http://host.docker.internal:8000/v1"));
        assertThrows(IllegalArgumentException.class, () -> factory.validateBaseUrl("http://example.com/v1"));
    }

    @Test
    @DisplayName("非法地址直接拒绝")
    void rejectsMalformedAddress() {
        assertThrows(IllegalArgumentException.class, () -> factory.validateBaseUrl("not-a-url"));
        assertThrows(IllegalArgumentException.class, () -> factory.validateBaseUrl("ftp://example.com/v1"));
    }
}
