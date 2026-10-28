package com.resolveiq.user;

import com.resolveiq.common.exception.BusinessRuleViolationException;

/** Minimum password strength: 8+ characters with at least one letter and one digit. */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;

    private PasswordPolicy() {
    }

    public static void validate(String raw) {
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
    }
}
