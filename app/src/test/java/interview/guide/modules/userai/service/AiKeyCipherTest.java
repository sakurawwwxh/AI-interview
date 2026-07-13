package interview.guide.modules.userai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class AiKeyCipherTest {

    @Test
    void encryptsWithRandomIvAndDecryptsWithTheSameApplicationSecret() {
        AiKeyCipher cipher = new AiKeyCipher("local-test-application-secret");

        String firstCiphertext = cipher.encrypt("test-user-api-key");
        String secondCiphertext = cipher.encrypt("test-user-api-key");

        assertNotEquals("test-user-api-key", firstCiphertext);
        assertNotEquals(firstCiphertext, secondCiphertext);
        assertEquals("test-user-api-key", cipher.decrypt(firstCiphertext));
        assertEquals("test-user-api-key", cipher.decrypt(secondCiphertext));
    }
}
