package com.resolveiq.security.reset;

import com.resolveiq.audit.AuditAction;
import com.resolveiq.audit.AuditService;
import com.resolveiq.common.exception.InvalidTokenException;
import com.resolveiq.config.AppProperties;
import com.resolveiq.notification.ChannelType;
import com.resolveiq.notification.NotificationChannelFactory;
import com.resolveiq.notification.NotificationMessage;
import com.resolveiq.security.crypto.Tokens;
import com.resolveiq.security.session.SessionService;
import com.resolveiq.user.PasswordPolicy;
import com.resolveiq.user.UserRepository;
import com.resolveiq.user.domain.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * "Forgot password" by e-mail link.
 *
 * <ul>
 *   <li>The response is identical whether or not the address has an account, so the form cannot be
 *       used to discover who works at the company.</li>
 *   <li>The link holds a 256-bit random token, valid for 30 minutes and only once; only its hash is
 *       stored. Asking again invalidates older links.</li>
 *   <li>Setting the new password signs the account out everywhere and clears any lockout.</li>
 * </ul>
 */
@Service
@Transactional
public class PasswordResetService {

    static final Duration VALIDITY = Duration.ofMinutes(30);

    private final UserRepository users;
    private final OneTimeTokenRepository tokens;
    private final PasswordEncoder encoder;
    private final NotificationChannelFactory channels;
    private final SessionService sessions;
    private final AuditService audit;
    private final AppProperties app;
    private final Clock clock;

    public PasswordResetService(UserRepository users, OneTimeTokenRepository tokens, PasswordEncoder encoder,
                                NotificationChannelFactory channels, SessionService sessions, AuditService audit,
                                AppProperties app, Clock clock) {
        this.users = users;
        this.tokens = tokens;
        this.encoder = encoder;
        this.channels = channels;
        this.sessions = sessions;
        this.audit = audit;
        this.app = app;
        this.clock = clock;
    }

    public void requestReset(String email, String ip) {
        Instant now = clock.instant();
        users.findByEmailIgnoreCase(email == null ? "" : email.trim()).filter(User::isActive).ifPresentOrElse(user -> {
            tokens.invalidateAll(user.getId(), OneTimeToken.PASSWORD_RESET, now);
            String raw = Tokens.random(32);
            tokens.save(new OneTimeToken(user.getId(), OneTimeToken.PASSWORD_RESET, Tokens.sha256(raw), now, now.plus(VALIDITY)));
            String link = app.publicUrl() + "/reset-password?token=" + raw;
            String intro = (user.isPasswordEnabled() ? "Someone asked to reset the password for your ResolveIQ account."
                    : "Someone asked to set a password for your ResolveIQ account (you currently sign in with Google).");
            String body = intro + "\n\nChoose a new password here (the link works once, for 30 minutes):\n" + link
                    + "\n\nIf this wasn't you, ignore this e-mail - your password has not changed.";
            String logged = app.isDemo() ? body + "\n\n(Demo mode: the link is kept in this log because no real e-mail is sent.)"
                    : intro + "\n\n[reset link withheld from the log]";
            channels.channelFor(ChannelType.EMAIL).ifPresent(c -> c.deliver(new NotificationMessage(user,
                    "Reset your ResolveIQ password", body, null, "PASSWORD_RESET", false, logged)));
            audit.recordSecurityEvent(user, user.getEmail(), AuditAction.PASSWORD_RESET_REQUESTED,
                    "Password reset link sent" + (ip == null ? "" : " (requested from " + ip + ")"));
        }, () -> audit.recordSecurityEvent(null, email, AuditAction.PASSWORD_RESET_REQUESTED,
                "Password reset requested for an unknown address" + (ip == null ? "" : " from " + ip)));
    }

    public User reset(String rawToken, String newPassword) {
        Instant now = clock.instant();
        OneTimeToken token = rawToken == null ? null
                : tokens.findByTokenHashAndPurpose(Tokens.sha256(rawToken), OneTimeToken.PASSWORD_RESET).orElse(null);
        if (token == null || !token.isUsable(now)) {
            throw new InvalidTokenException("This reset link is invalid or has expired. Ask for a new one.");
        }
        User user = users.findById(token.getUserId()).filter(User::isActive)
                .orElseThrow(() -> new InvalidTokenException("This reset link is no longer valid."));
        PasswordPolicy.validate(newPassword, user.getEmail());
        token.use(now);
        user.changePasswordHash(encoder.encode(newPassword));
        users.save(user);
        sessions.revokeAll(user, "PASSWORD_RESET");
        audit.recordSecurityEvent(user, user.getEmail(), AuditAction.PASSWORD_RESET,
                "Password reset with an e-mailed link; all sessions signed out");
        return user;
    }
}
