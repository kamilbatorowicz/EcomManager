package pl.ecommanager.integrations.allegro.security;

import pl.ecommanager.integrations.allegro.config.AllegroProperties;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class TokenCipher {
    private final AllegroProperties properties;
    private final SecureRandom random = new SecureRandom();

    TokenCipher(AllegroProperties properties) {
        this.properties = properties;
    }

    public String encrypt(String value) {
        try {
            byte[] nonce = new byte[12];
            random.nextBytes(nonce);
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, nonce);
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(nonce.length + encrypted.length)
                    .put(nonce).put(encrypted).array());
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Nie można zaszyfrować tokena Allegro.");
        }
    }

    public String decrypt(String value) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(Base64.getDecoder().decode(value));
            byte[] nonce = new byte[12];
            buffer.get(nonce);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            return new String(cipher(Cipher.DECRYPT_MODE, nonce).doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | RuntimeException exception) {
            throw new IllegalStateException("Nie można odszyfrować tokena Allegro.");
        }
    }

    private Cipher cipher(int mode, byte[] nonce) throws GeneralSecurityException {
        byte[] key = Base64.getDecoder().decode(properties.tokenEncryptionKey());
        if (key.length != 32) {
            throw new IllegalArgumentException("Nieprawidłowy klucz szyfrowania.");
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        return cipher;
    }
}
