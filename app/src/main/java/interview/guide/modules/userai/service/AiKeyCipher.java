package interview.guide.modules.userai.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class AiKeyCipher {
    private final SecretKeySpec key;
    public AiKeyCipher(@Value("${app.jwt.secret}") String applicationSecret) {
        try { key = new SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(applicationSecret.getBytes(StandardCharsets.UTF_8)), "AES"); }
        catch (Exception e) { throw new IllegalStateException("Unable to initialize credential encryption", e); }
    }
    public String encrypt(String value) {
        try { byte[] iv = new byte[12]; new SecureRandom().nextBytes(iv); Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv)); byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)); byte[] payload = new byte[iv.length + encrypted.length]; System.arraycopy(iv, 0, payload, 0, iv.length); System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length); return Base64.getEncoder().encodeToString(payload); }
        catch (Exception e) { throw new IllegalStateException("Unable to encrypt credential", e); }
    }
    public String decrypt(String value) {
        try { byte[] payload = Base64.getDecoder().decode(value); byte[] iv = java.util.Arrays.copyOfRange(payload, 0, 12); byte[] encrypted = java.util.Arrays.copyOfRange(payload, 12, payload.length); Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv)); return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8); }
        catch (Exception e) { throw new IllegalStateException("Unable to decrypt credential", e); }
    }
}
