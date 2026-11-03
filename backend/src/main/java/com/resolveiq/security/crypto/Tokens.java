package com.resolveiq.security.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Random tokens and one-way hashes for secrets the server hands out (refresh tokens, reset links,
 * recovery codes, live-update tickets).
 *
 * <p>These tokens are 256-bit random values, so a fast SHA-256 hash is enough to store them safely:
 * unlike passwords they cannot be guessed, so a slow hash such as BCrypt adds nothing. Storing only
 * the hash means a copy of the database does not let anyone sign in as a user.</p>
 */
public final class Tokens {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray(); // no 0/O, 1/I

    private Tokens() {
    }

    /** URL-safe random token with {@code bytes} bytes of entropy. */
    public static String random(int bytes) {
        byte[] buf = new byte[bytes];
        RANDOM.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    public static byte[] randomBytes(int bytes) {
        byte[] buf = new byte[bytes];
        RANDOM.nextBytes(buf);
        return buf;
    }

    /** Human-friendly code such as {@code K7QM-2XPA}, used for 2FA recovery codes. */
    public static String humanCode(int groups, int groupLength) {
        StringBuilder sb = new StringBuilder();
        for (int g = 0; g < groups; g++) {
            if (g > 0) {
                sb.append('-');
            }
            for (int i = 0; i < groupLength; i++) {
                sb.append(CODE_ALPHABET[RANDOM.nextInt(CODE_ALPHABET.length)]);
            }
        }
        return sb.toString();
    }

    public static String sha256(String value) {
        return HexFormat.of().formatHex(sha256Bytes(value.getBytes(StandardCharsets.UTF_8)));
    }

    public static byte[] sha256Bytes(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    /** Compares secrets in constant time, so response timing does not reveal how much matched. */
    public static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
