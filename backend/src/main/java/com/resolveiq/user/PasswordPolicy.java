package com.resolveiq.user;

import com.resolveiq.common.exception.BusinessRuleViolationException;

import java.util.Locale;
import java.util.Set;

/**
 * Password rules, following NIST SP 800-63B: length matters more than symbols, so the rules are
 * 8-72 characters with a letter and a digit, not one of the passwords attackers try first, and not
 * built from the person's own e-mail name.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;

    /** The most common passwords in breach corpora (lower-cased); attackers try these first. */
    private static final Set<String> COMMON = Set.of(
            "password", "password1", "password12", "password123", "password1234", "passw0rd", "p@ssw0rd",
            "p@ssword1", "12345678", "123456789", "1234567890", "qwerty123", "qwertyuiop", "qwerty12",
            "abc12345", "abcd1234", "1q2w3e4r", "1qaz2wsx", "zaq12wsx", "iloveyou1", "welcome1", "welcome123",
            "letmein1", "admin123", "admin1234", "administrator1", "changeme1", "changeme123", "monkey123",
            "dragon123", "football1", "baseball1", "sunshine1", "princess1", "trustno1", "master123",
            "superman1", "batman123", "starwars1", "computer1", "internet1", "secret123", "test1234",
            "testing123", "default1", "guest1234", "login123", "user1234", "india123", "india@123",
            "pass1234", "company1", "company123", "summer2024", "winter2024", "spring2025", "summer2025",
            "autumn2025", "winter2025", "summer2026", "winter2026", "resolveiq1", "resolveiq123");

    private PasswordPolicy() {
    }

    public static void validate(String raw) {
        validate(raw, null);
    }

    public static void validate(String raw, String email) {
        if (raw == null || raw.length() < MIN_LENGTH) {
            throw new BusinessRuleViolationException("Password must be at least " + MIN_LENGTH + " characters");
        }
        if (raw.length() > 72) {
            // BCrypt only uses the first 72 bytes
            throw new BusinessRuleViolationException("Password must be at most 72 characters");
        }
        boolean hasLetter = raw.chars().anyMatch(Character::isLetter);
        boolean hasDigit = raw.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new BusinessRuleViolationException("Password must contain at least one letter and one digit");
        }
        String lower = raw.toLowerCase(Locale.ROOT);
        if (COMMON.contains(lower)) {
            throw new BusinessRuleViolationException("That password is too common - attackers try it first. Choose another.");
        }
        if (email != null && email.contains("@")) {
            String name = email.substring(0, email.indexOf('@')).toLowerCase(Locale.ROOT);
            if (name.length() >= 4 && lower.contains(name)) {
                throw new BusinessRuleViolationException("Your password must not contain your e-mail name");
            }
        }
    }
}
