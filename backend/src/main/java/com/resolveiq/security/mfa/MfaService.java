package com.resolveiq.security.mfa;

import com.resolveiq.audit.AuditAction;
import com.resolveiq.audit.AuditService;
import com.resolveiq.common.exception.BusinessRuleViolationException;
import com.resolveiq.common.exception.InvalidCredentialsException;
import com.resolveiq.common.exception.InvalidMfaCodeException;
import com.resolveiq.security.crypto.SecretCipher;
import com.resolveiq.security.crypto.Tokens;
import com.resolveiq.user.UserRepository;
import com.resolveiq.user.domain.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;

/**
 * Two-factor authentication with an authenticator app (TOTP).
 *
 * <ol>
 *   <li>{@link #beginSetup}: a new secret is generated, encrypted and kept as "pending"; the user scans
 *       a QR code made from it.</li>
 *   <li>{@link #confirmSetup}: the user types the code the app shows. Only a correct code turns 2FA on,
 *       so nobody can lock themselves out with a mis-scanned QR code. Ten recovery codes are issued,
 *       shown once and stored hashed.</li>
 *   <li>{@link #verify}: at sign-in, a TOTP code (never the same one twice) or an unused recovery code.</li>
 * </ol>
 */
@Service
@Transactional
public class MfaService {

    public static final String ISSUER = "ResolveIQ";
    static final int RECOVERY_CODES = 10;

    public record Setup(String secret, String otpauthUri) {
    }

    private final UserRepository users;
    private final RecoveryCodeRepository recoveryCodes;
    private final SecretCipher cipher;
    private final PasswordEncoder passwords;
    private final AuditService audit;
    private final Clock clock;

    public MfaService(UserRepository users, RecoveryCodeRepository recoveryCodes, SecretCipher cipher,
                      PasswordEncoder passwords, AuditService audit, Clock clock) {
        this.users = users;
        this.recoveryCodes = recoveryCodes;
        this.cipher = cipher;
        this.passwords = passwords;
        this.audit = audit;
        this.clock = clock;
    }

    public Setup beginSetup(User caller) {
        User user = managed(caller);
        if (user.isMfaEnabled()) {
            throw new BusinessRuleViolationException("Two-factor authentication is already on");
        }
        String secret = Totp.newSecret();
        user.beginMfaEnrolment(cipher.encrypt(secret));
        users.save(user);
        return new Setup(secret, Totp.provisioningUri(ISSUER, user.getEmail(), secret));
    }

    /** @return the recovery codes, in plain text, for the user to save; they are never shown again */
    public List<String> confirmSetup(User caller, String code) {
        User user = managed(caller);
        if (user.getMfaPendingSecret() == null) {
            throw new BusinessRuleViolationException("Start two-factor set-up first");
        }
        OptionalLong step = Totp.verify(cipher.decrypt(user.getMfaPendingSecret()), code, clock.instant());
        if (step.isEmpty()) {
            throw new InvalidMfaCodeException();
        }
        user.confirmMfaEnrolment(step.getAsLong());
        users.save(user);
        List<String> codes = regenerateRecoveryCodes(user);
        audit.recordSecurityEvent(user, user.getEmail(), AuditAction.MFA_ENABLED, "Two-factor authentication turned on");
        return codes;
    }

    /** Turning 2FA off needs the password (or, for Google-only accounts, a current code). */
    public void disable(User caller, String password, String code) {
        User user = managed(caller);
        if (!user.isMfaEnabled()) {
            return;
        }
        boolean passwordOk = user.isPasswordEnabled() && password != null && passwords.matches(password, user.getPasswordHash());
        boolean codeOk = !passwordOk && code != null && verify(user, code);
        if (!passwordOk && !codeOk) {
            throw user.isPasswordEnabled() ? new InvalidCredentialsException() : new InvalidMfaCodeException();
        }
        clear(user);
        audit.recordSecurityEvent(user, user.getEmail(), AuditAction.MFA_DISABLED, "Two-factor authentication turned off");
    }

    public void resetByAdmin(User target, User admin) {
        User user = managed(target);
        clear(user);
        audit.recordAdminAction(admin, AuditAction.MFA_RESET_BY_ADMIN, null, null,
                "Two-factor authentication reset for " + user.getFullName());
    }

    /** Checks a sign-in code: a TOTP code (each accepted once) or a recovery code (each usable once). */
    public boolean verify(User user, String code) {
        if (!user.isMfaEnabled() || code == null) {
            return false;
        }
        String trimmed = code.trim();
        if (trimmed.replaceAll("\\s", "").matches("\\d{6}")) {
            OptionalLong step = Totp.verify(cipher.decrypt(user.getMfaSecret()), trimmed, clock.instant());
            if (step.isPresent() && user.acceptMfaStep(step.getAsLong())) {
                users.save(user);
                return true;
            }
            return false;
        }
        String normalised = trimmed.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        if (normalised.length() != 10) {
            return false;
        }
        String formatted = normalised.substring(0, 5) + "-" + normalised.substring(5);
        return recoveryCodes.findByUserIdAndCodeHashAndUsedAtIsNull(user.getId(), Tokens.sha256(formatted))
                .map(rc -> {
                    rc.use(clock.instant());
                    recoveryCodes.save(rc);
                    return true;
                }).orElse(false);
    }

    @Transactional(readOnly = true)
    public long remainingRecoveryCodes(User user) {
        return recoveryCodes.countUnused(user.getId());
    }

    public List<String> regenerateRecoveryCodes(User user) {
        if (!user.isMfaEnabled()) {
            throw new BusinessRuleViolationException("Turn on two-factor authentication first");
        }
        recoveryCodes.deleteByUserId(user.getId());
        List<String> plain = new ArrayList<>();
        for (int i = 0; i < RECOVERY_CODES; i++) {
            String code = Tokens.humanCode(2, 5);
            plain.add(code);
            recoveryCodes.save(new RecoveryCode(user.getId(), Tokens.sha256(code)));
        }
        return plain;
    }

    private User managed(User user) {
        return users.findById(user.getId()).orElseThrow(() -> new IllegalArgumentException("Unknown user"));
    }

    private void clear(User user) {
        user.disableMfa();
        users.save(user);
        recoveryCodes.deleteByUserId(user.getId());
    }
}
