package com.resolveiq.security;

import com.resolveiq.audit.AuditAction;
import com.resolveiq.audit.AuditService;
import com.resolveiq.common.exception.AccountDisabledException;
import com.resolveiq.common.exception.AccountLockedException;
import com.resolveiq.common.exception.DuplicateResourceException;
import com.resolveiq.common.exception.InvalidCredentialsException;
import com.resolveiq.user.PasswordPolicy;
import com.resolveiq.user.UserFactory;
import com.resolveiq.user.UserRepository;
import com.resolveiq.user.domain.Role;
import com.resolveiq.user.domain.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Login, self-registration and password change. Implements brute-force protection: after
 * {@code maxFailedLogins} wrong passwords the account is locked for {@code lockoutMinutes}.
 */
@Service
public class AuthService {

    /** Hash compared against for unknown e-mails, so response time does not reveal which accounts exist. */
    private final String dummyHash;

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final UserFactory userFactory;
    private final AuditService audit;
    private final SecurityProperties props;
    private final Clock clock;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt, UserFactory userFactory,
                       AuditService audit, SecurityProperties props, Clock clock) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.userFactory = userFactory;
        this.audit = audit;
        this.props = props;
        this.clock = clock;
        this.dummyHash = encoder.encode("timing-equaliser-not-a-real-password-1");
    }

    public record LoginResult(User user, JwtService.IssuedToken token) {
    }

    @Transactional(noRollbackFor = {InvalidCredentialsException.class, AccountLockedException.class})
    public LoginResult login(String email, String password) {
        Instant now = clock.instant();
        Optional<User> found = email == null ? Optional.empty() : users.findByEmailIgnoreCase(email.trim());
        if (found.isEmpty()) {
            encoder.matches(password == null ? "" : password, dummyHash);
            audit.recordSecurityEvent(null, email, AuditAction.LOGIN_FAILED, "Login attempt for unknown account");
            throw new InvalidCredentialsException();
        }
        User user = found.get();
        if (user.isLocked(now)) {
            throw new AccountLockedException(user.getLockedUntil());
        }
        if (password == null || !encoder.matches(password, user.getPasswordHash())) {
            boolean locked = user.recordFailedLogin(now, props.maxFailedLogins(), Duration.ofMinutes(props.lockoutMinutes()));
            users.save(user);
            audit.recordSecurityEvent(user, email, locked ? AuditAction.ACCOUNT_LOCKED : AuditAction.LOGIN_FAILED,
                    locked ? "Account locked after repeated failed logins" : "Wrong password");
            if (locked) {
                throw new AccountLockedException(user.getLockedUntil());
            }
            throw new InvalidCredentialsException();
        }
        if (!user.isActive()) {
            throw new AccountDisabledException();
        }
        user.recordSuccessfulLogin(now);
        users.save(user);
        audit.recordSecurityEvent(user, email, AuditAction.LOGIN_SUCCEEDED, "Signed in");
        return new LoginResult(user, jwt.issue(user));
    }

    /** Self-registration always creates an EMPLOYEE - staff accounts are created by administrators. */
    @Transactional
    public LoginResult register(String fullName, String email, String password, String phone, String department) {
        if (users.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException("An account with this e-mail already exists");
        }
        User user = users.save(userFactory.create(new UserFactory.NewUser(Role.EMPLOYEE, fullName, email, password,
                phone, department, null, null, null)));
        audit.recordAdminAction(user, AuditAction.USER_REGISTERED, null, Role.EMPLOYEE.name(),
                user.getFullName() + " registered as an employee");
        return new LoginResult(user, jwt.issue(user));
    }

    @Transactional
    public void changePassword(User user, String currentPassword, String newPassword) {
        if (!encoder.matches(currentPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        PasswordPolicy.validate(newPassword);
        user.changePasswordHash(encoder.encode(newPassword));
        users.save(user);
        audit.recordAdminAction(user, AuditAction.PASSWORD_CHANGED, null, null, "Password changed");
    }
}
