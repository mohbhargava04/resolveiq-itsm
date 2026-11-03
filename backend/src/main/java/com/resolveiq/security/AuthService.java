package com.resolveiq.security;

import com.resolveiq.audit.AuditAction;
import com.resolveiq.audit.AuditService;
import com.resolveiq.common.exception.AccountDisabledException;
import com.resolveiq.common.exception.AccountLockedException;
import com.resolveiq.common.exception.DuplicateResourceException;
import com.resolveiq.common.exception.InvalidCredentialsException;
import com.resolveiq.common.exception.InvalidMfaCodeException;
import com.resolveiq.common.exception.RegistrationDisabledException;
import com.resolveiq.common.exception.SessionExpiredException;
import com.resolveiq.security.mfa.MfaService;
import com.resolveiq.security.session.RequestMeta;
import com.resolveiq.security.session.SessionService;
import com.resolveiq.security.session.SignInMethod;
import com.resolveiq.settings.SettingKey;
import com.resolveiq.settings.SettingsService;
import com.resolveiq.user.PasswordPolicy;
import com.resolveiq.user.UserFactory;
import com.resolveiq.user.UserRepository;
import com.resolveiq.user.domain.Role;
import com.resolveiq.user.domain.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Sign-in, self-registration and password change.
 *
 * <p>Signing in is a two-step process when two-factor authentication is on: the first factor (a
 * password, or a verified Google account) yields a 5-minute <i>challenge</i>; the second factor (an
 * authenticator code or a recovery code) exchanges it for a session.</p>
 *
 * <p>Brute-force protection: after {@code maxFailedLogins} wrong passwords <i>or</i> wrong 2FA codes the
 * account is locked for {@code lockoutMinutes}. Unknown e-mails cost the same BCrypt time as known
 * ones, so timing does not reveal which accounts exist.</p>
 */
@Service
public class AuthService {

    static final Duration MFA_CHALLENGE_VALIDITY = Duration.ofMinutes(5);

    /** Outcome of the first sign-in step. */
    public sealed interface SignInResult permits SignedIn, MfaRequired {
    }

    /** Fully signed in: a session has been created. */
    public record SignedIn(SessionService.IssuedSession session) implements SignInResult {
    }

    /** Correct first factor; a 2FA code is now needed. */
    public record MfaRequired(String challenge, Instant expiresAt) implements SignInResult {
    }

    /** Hash compared against for unknown e-mails, so response time does not reveal which accounts exist. */
    private final String dummyHash;

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final UserFactory userFactory;
    private final AuditService audit;
    private final SessionService sessions;
    private final MfaService mfa;
    private final EmailDomainPolicy domains;
    private final SettingsService settings;
    private final SecurityProperties props;
    private final Clock clock;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt, UserFactory userFactory,
                       AuditService audit, SessionService sessions, MfaService mfa, EmailDomainPolicy domains,
                       SettingsService settings, SecurityProperties props, Clock clock) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.userFactory = userFactory;
        this.audit = audit;
        this.sessions = sessions;
        this.mfa = mfa;
        this.domains = domains;
        this.settings = settings;
        this.props = props;
        this.clock = clock;
        this.dummyHash = encoder.encode("timing-equaliser-not-a-real-password-1");
    }

    @Transactional(noRollbackFor = {InvalidCredentialsException.class, AccountLockedException.class})
    public SignInResult login(String email, String password, RequestMeta meta) {
        Instant now = clock.instant();
        String from = from(meta);
        Optional<User> found = email == null ? Optional.empty() : users.findByEmailIgnoreCase(email.trim());
        if (found.isEmpty()) {
            encoder.matches(password == null ? "" : password, dummyHash);
            audit.recordSecurityEvent(null, email, AuditAction.LOGIN_FAILED, "Login attempt for unknown account" + from);
            throw new InvalidCredentialsException();
        }
        User user = found.get();
        if (user.isLocked(now)) {
            throw new AccountLockedException(user.getLockedUntil());
        }
        if (password == null || !user.isPasswordEnabled() || !encoder.matches(password, user.getPasswordHash())) {
            if (!user.isPasswordEnabled()) {
                encoder.matches(password == null ? "" : password, dummyHash);
            }
            failedAttempt(user, email, user.isPasswordEnabled() ? "Wrong password" + from
                    : "Password sign-in attempted on a Google-only account" + from);
            throw new InvalidCredentialsException();
        }
        if (!user.isActive()) {
            throw new AccountDisabledException();
        }
        return afterFirstFactor(user, SignInMethod.PASSWORD, meta);
    }

    /**
     * Called after a verified first factor (password or company account). Either creates the session
     * or, when the user has 2FA, returns a challenge for the second step.
     */
    @Transactional
    public SignInResult afterFirstFactor(User user, SignInMethod method, RequestMeta meta) {
        if (user.isMfaEnabled()) {
            JwtService.IssuedToken challenge = jwt.issueChallenge(JwtService.TYPE_MFA, user.getEmail(),
                    Map.of(JwtService.CLAIM_USER_ID, user.getId(), JwtService.CLAIM_TOKEN_VERSION, user.getTokenVersion(),
                            "method", method.name()), MFA_CHALLENGE_VALIDITY);
            return new MfaRequired(challenge.token(), challenge.expiresAt());
        }
        return new SignedIn(startSession(user, method, meta));
    }

    /** Second step: a TOTP or recovery code. Wrong codes count towards the account lockout. */
    @Transactional(noRollbackFor = {InvalidMfaCodeException.class, AccountLockedException.class})
    public SignedIn completeMfa(String challengeToken, String code, RequestMeta meta) {
        Jwt challenge = jwt.readChallenge(challengeToken, JwtService.TYPE_MFA).orElseThrow(SessionExpiredException::new);
        Object uid = challenge.getClaim(JwtService.CLAIM_USER_ID);
        User user = uid instanceof Number id ? users.findById(id.longValue()).orElse(null) : null;
        Object tv = challenge.getClaim(JwtService.CLAIM_TOKEN_VERSION);
        if (user == null || !user.isActive() || !(tv instanceof Number v) || v.intValue() != user.getTokenVersion()) {
            throw new SessionExpiredException();
        }
        Instant now = clock.instant();
        if (user.isLocked(now)) {
            throw new AccountLockedException(user.getLockedUntil());
        }
        if (!mfa.verify(user, code)) {
            boolean locked = user.recordFailedLogin(now, props.maxFailedLogins(), Duration.ofMinutes(props.lockoutMinutes()));
            users.save(user);
            audit.recordSecurityEvent(user, user.getEmail(), locked ? AuditAction.ACCOUNT_LOCKED : AuditAction.MFA_FAILED,
                    (locked ? "Account locked after repeated wrong two-factor codes" : "Wrong two-factor code") + from(meta));
            if (locked) {
                throw new AccountLockedException(user.getLockedUntil());
            }
            throw new InvalidMfaCodeException();
        }
        SignInMethod method = SignInMethod.valueOf(challenge.getClaimAsString("method")).withMfa();
        return new SignedIn(startSession(user, method, meta));
    }

    /** Self-registration always creates an EMPLOYEE - staff accounts are created by administrators. */
    @Transactional
    public SessionService.IssuedSession register(String fullName, String email, String password, String phone,
                                                 String department, RequestMeta meta) {
        if (!settings.isEnabled(SettingKey.SELF_REGISTRATION)) {
            throw new RegistrationDisabledException("Accounts are created by your IT administrator. Sign in with your company account instead.");
        }
        domains.require(email);
        if (users.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException("An account with this e-mail already exists");
        }
        PasswordPolicy.validate(password, email);
        User user = users.save(userFactory.create(new UserFactory.NewUser(Role.EMPLOYEE, fullName, email, password,
                phone, department, null, null, null)));
        audit.recordAdminAction(user, AuditAction.USER_REGISTERED, null, Role.EMPLOYEE.name(),
                user.getFullName() + " registered as an employee");
        return startSession(user, SignInMethod.PASSWORD, meta);
    }

    /**
     * Changes the password, signs out every other device, and starts a fresh session for this one.
     * For a Google-only account this sets a first password, so password sign-in becomes available.
     */
    @Transactional
    public SessionService.IssuedSession changePassword(User caller, String currentPassword, String newPassword,
                                                       RequestMeta meta) {
        User user = users.findById(caller.getId()).orElseThrow(SessionExpiredException::new);
        if (user.isPasswordEnabled() && !encoder.matches(currentPassword == null ? "" : currentPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        PasswordPolicy.validate(newPassword, user.getEmail());
        user.changePasswordHash(encoder.encode(newPassword));
        users.save(user);
        sessions.revokeAll(user, "PASSWORD_CHANGED");
        audit.recordAdminAction(user, AuditAction.PASSWORD_CHANGED, null, null,
                "Password changed; other devices signed out");
        return sessions.start(user, SignInMethod.PASSWORD, meta);
    }

    private SessionService.IssuedSession startSession(User user, SignInMethod method, RequestMeta meta) {
        user.recordSuccessfulLogin(clock.instant());
        users.save(user);
        audit.recordSecurityEvent(user, user.getEmail(),
                method == SignInMethod.GOOGLE || method == SignInMethod.GOOGLE_MFA ? AuditAction.SSO_LOGIN_SUCCEEDED
                        : AuditAction.LOGIN_SUCCEEDED, "Signed in (" + method.label() + ")" + from(meta));
        return sessions.start(user, method, meta);
    }

    private void failedAttempt(User user, String email, String description) {
        boolean locked = user.recordFailedLogin(clock.instant(), props.maxFailedLogins(), Duration.ofMinutes(props.lockoutMinutes()));
        users.save(user);
        audit.recordSecurityEvent(user, email, locked ? AuditAction.ACCOUNT_LOCKED : AuditAction.LOGIN_FAILED,
                locked ? "Account locked after repeated failed logins" : description);
        if (locked) {
            throw new AccountLockedException(user.getLockedUntil());
        }
    }

    private static String from(RequestMeta meta) {
        return meta == null || meta.ipAddress() == null ? "" : " from " + meta.ipAddress();
    }
}
