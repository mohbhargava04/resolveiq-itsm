package com.resolveiq.security.crypto;

import com.resolveiq.security.SecurityProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

/**
 * Encrypts small secrets that the server must be able to read back (two-factor secrets) with
 * <b>AES-256-GCM</b>. GCM is authenticated encryption: a tampered ciphertext fails to decrypt instead
 * of producing garbage. Each value gets a fresh random 96-bit nonce, stored in front of the ciphertext.
 *
 * <p>Format: {@code v1:<base64(nonce || ciphertext+tag)>}. The version prefix allows key rotation later.</p>
 */
@Component
public class SecretCipher {

    private static final String PREFIX = "v1:";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;

    public SecretCipher(SecurityProperties properties) {
        // derive a 256-bit key from the configured passphrase
        byte[] derived = Tokens.sha256Bytes(("resolveiq-data-key:" + properties.dataKey()).getBytes(StandardCharsets.UTF_8));
        this.key = new SecretKeySpec(derived, "AES");
    }

    public String encrypt(String plain) {
        try {
            byte[] nonce = Tokens.randomBytes(NONCE_BYTES);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted).array();
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    public String decrypt(String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Unrecognised encrypted value");
        }
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, NONCE_BYTES));
            byte[] plain = cipher.doFinal(all, NONCE_BYTES, all.length - NONCE_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Decryption failed - wrong key or tampered value", e);
        }
    }
}
