package com.resolveiq.security.oauth;

import com.resolveiq.audit.AuditAction;
import com.resolveiq.audit.AuditService;
import com.resolveiq.security.EmailDomainPolicy;
import com.resolveiq.security.crypto.Tokens;
import com.resolveiq.settings.SettingKey;
import com.resolveiq.settings.SettingsService;
import com.resolveiq.user.UserFactory;
import com.resolveiq.user.UserRepository;
import com.resolveiq.user.domain.Role;
import com.resolveiq.user.domain.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Decides which ResolveIQ account a verified company identity signs in to. Google proves <i>who</i>
 * the person is (authentication); what they may do (authorisation) stays with ResolveIQ:
 *
 * <ol>
 *   <li>The e-mail must be verified by Google and belong to an allowed company domain.</li>
 *   <li>An account already linked to this Google id signs in.</li>
 *   <li>Otherwise an account pre-registered by an administrator with the same e-mail is linked on
 *       first sign-in and keeps the role the administrator gave it - this is how technicians, team
 *       leads and managers arrive at their own workspace.</li>
 *   <li>Otherwise, if allowed, a new <b>Employee</b> account is created (the lowest clearance) and the
 *       person is asked to confirm a few profile details. An administrator can promote them later.
 *       Nobody can choose their own role.</li>
 * </ol>
 */
@Service
@Transactional(noRollbackFor = FederatedSignInException.class)
public class FederatedSignInService {

    private final UserRepository users;
    private final UserFactory userFactory;
    private final PasswordEncoder encoder;
    private final EmailDomainPolicy domains;
    private final SettingsService settings;
    private final AuditService audit;

    public FederatedSignInService(UserRepository users, UserFactory userFactory, PasswordEncoder encoder,
                                  EmailDomainPolicy domains, SettingsService settings, AuditService audit) {
        this.users = users;
        this.userFactory = userFactory;
        this.encoder = encoder;
        this.domains = domains;
        this.settings = settings;
        this.audit = audit;
    }

    public User resolve(ExternalIdentity identity, String ip) {
        String email = identity.email() == null ? null : User.normaliseEmail(identity.email());
        String from = ip == null ? "" : " from " + ip;
        if (email == null || !identity.emailVerified()) {
            reject(null, email, "email_unverified", "Company account e-mail is not verified" + from);
        }
        if (!domains.isAllowed(email)) {
            reject(null, email, "domain_not_allowed", "Sign-in with a non-company account (" + email + ")" + from);
        }

        Optional<User> linked = identity.provider().equals("google")
                ? users.findByGoogleSubject(identity.subject()) : Optional.empty();
        User user;
        if (linked.isPresent()) {
            user = linked.get();
        } else {
            Optional<User> byEmail = users.findByEmailIgnoreCase(email);
            if (byEmail.isPresent()) {
                user = byEmail.get();
                if (user.getGoogleSubject() != null && !user.getGoogleSubject().equals(identity.subject())) {
                    reject(user, email, "account_conflict", "A different Google account tried to sign in as " + email + from);
                }
                user.linkGoogleAccount(identity.subject());
                audit.recordSecurityEvent(user, email, AuditAction.SSO_ACCOUNT_LINKED,
                        "Account linked to Google sign-in" + from);
            } else if (settings.isEnabled(SettingKey.AUTO_PROVISION_SSO)) {
                user = provision(identity, email);
                audit.recordSecurityEvent(user, email, AuditAction.SSO_ACCOUNT_PROVISIONED,
                        "Employee account created on first Google sign-in" + from);
            } else {
                reject(null, email, "not_provisioned", "No ResolveIQ account for " + email + from);
                return null; // unreachable
            }
        }
        if (!user.isActive()) {
            reject(user, email, "account_disabled", "Sign-in to a deactivated account" + from);
        }
        if (user.getAvatarUrl() == null && identity.pictureUrl() != null && identity.pictureUrl().startsWith("https://")) {
            user.changeAvatar(identity.pictureUrl());
        }
        return users.save(user);
    }

    private User provision(ExternalIdentity identity, String email) {
        String name = identity.name() == null || identity.name().isBlank() ? email.substring(0, email.indexOf('@')) : identity.name();
        // a strong random password nobody knows; password sign-in stays off until the person sets one
        String unusable = "Gx9" + Tokens.random(24);
        User user = userFactory.create(UserFactory.NewUser.of(Role.EMPLOYEE, name, email, unusable, null));
        user.disablePasswordLogin(encoder.encode(Tokens.random(32)));
        user.linkGoogleAccount(identity.subject());
        user.markProfileIncomplete();
        return users.save(user);
    }

    private void reject(User user, String email, String reason, String description) {
        audit.recordSecurityEvent(user, email, AuditAction.SSO_LOGIN_REJECTED, description);
        throw new FederatedSignInException(reason, description);
    }
}
