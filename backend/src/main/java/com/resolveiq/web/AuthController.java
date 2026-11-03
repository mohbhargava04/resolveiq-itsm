package com.resolveiq.web;

import com.resolveiq.common.exception.BusinessRuleViolationException;
import com.resolveiq.common.exception.SessionExpiredException;
import com.resolveiq.config.AppProperties;
import com.resolveiq.security.AuthCookies;
import com.resolveiq.security.AuthService;
import com.resolveiq.security.CurrentUserService;
import com.resolveiq.security.EmailDomainPolicy;
import com.resolveiq.security.JwtService;
import com.resolveiq.security.mfa.MfaService;
import com.resolveiq.security.oauth.IdentityProviders;
import com.resolveiq.security.reset.PasswordResetService;
import com.resolveiq.security.session.RequestMeta;
import com.resolveiq.security.session.SessionService;
import com.resolveiq.settings.SettingKey;
import com.resolveiq.settings.SettingsService;
import com.resolveiq.user.ProfileService;
import com.resolveiq.user.UserRepository;
import com.resolveiq.user.domain.User;
import com.resolveiq.web.dto.CommonDtos.Message;
import com.resolveiq.web.dto.UserDtos.AuthResponse;
import com.resolveiq.web.dto.UserDtos.ChangePasswordRequest;
import com.resolveiq.web.dto.UserDtos.ForgotPasswordRequest;
import com.resolveiq.web.dto.UserDtos.LoginRequest;
import com.resolveiq.web.dto.UserDtos.MfaCodeRequest;
import com.resolveiq.web.dto.UserDtos.MfaDisableRequest;
import com.resolveiq.web.dto.UserDtos.MfaVerifyRequest;
import com.resolveiq.web.dto.UserDtos.ProfileRequest;
import com.resolveiq.web.dto.UserDtos.RegisterRequest;
import com.resolveiq.web.dto.UserDtos.ResetPasswordRequest;
import com.resolveiq.web.dto.UserDtos.UserView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sign-in, sessions, two-factor authentication, password reset and the signed-in user's profile.
 *
 * <p>Every successful sign-in returns the access token in the body and sets the refresh token as an
 * HttpOnly cookie. The cookie endpoints ({@code /refresh}, {@code /logout}) additionally require the
 * {@code X-Requested-With: ResolveIQ} header: browsers refuse to add custom headers to cross-site
 * requests without CORS permission, so another website cannot trigger them.</p>
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication")
public class AuthController {

    static final String CSRF_HEADER = "X-Requested-With";
    static final String CSRF_VALUE = "ResolveIQ";

    /** Shown on the sign-in page in demo mode only. */
    record DemoAccount(String role, String label, String email, String lands) {
    }

    static final List<DemoAccount> DEMO_ACCOUNTS = List.of(
            new DemoAccount("EMPLOYEE", "Employee", "ava@resolveiq.test", "Employee portal: report and track own incidents"),
            new DemoAccount("TECHNICIAN", "Technician", "rahul@resolveiq.test", "My queue: an unacknowledged P1 that is escalating"),
            new DemoAccount("TEAM_LEAD", "Team lead", "neha@resolveiq.test", "Command centre: assignment, correlation, major incidents"),
            new DemoAccount("ADMIN", "System admin", "admin@resolveiq.test", "Users, catalogue, SLA policies, settings, audit log"),
            new DemoAccount("MANAGER", "IT manager", "manager@resolveiq.test", "Everything, top of the escalation chain"));

    private final AuthService auth;
    private final SessionService sessions;
    private final MfaService mfa;
    private final PasswordResetService passwordReset;
    private final CurrentUserService currentUser;
    private final ProfileService profiles;
    private final UserRepository users;
    private final AuthCookies cookies;
    private final IdentityProviders providers;
    private final EmailDomainPolicy domains;
    private final SettingsService settings;
    private final AppProperties app;

    public AuthController(AuthService auth, SessionService sessions, MfaService mfa, PasswordResetService passwordReset,
                          CurrentUserService currentUser, ProfileService profiles, UserRepository users,
                          AuthCookies cookies, IdentityProviders providers, EmailDomainPolicy domains,
                          SettingsService settings, AppProperties app) {
        this.auth = auth;
        this.sessions = sessions;
        this.mfa = mfa;
        this.passwordReset = passwordReset;
        this.currentUser = currentUser;
        this.profiles = profiles;
        this.users = users;
        this.cookies = cookies;
        this.providers = providers;
        this.domains = domains;
        this.settings = settings;
        this.app = app;
    }

    // ---- sign-in -----------------------------------------------------------------------------------

    @GetMapping("/options")
    @Operation(summary = "What the sign-in page should offer (company sign-in, registration, demo accounts)")
    public Map<String, Object> options() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("providers", providers.enabledProviders().stream()
                .map(p -> Map.of("id", p.id(), "name", p.displayName())).toList());
        m.put("selfRegistration", settings.isEnabled(SettingKey.SELF_REGISTRATION));
        m.put("allowedDomains", domains.allowedDomains());
        m.put("demoMode", app.isDemo());
        if (app.isDemo()) {
            m.put("demoPassword", "Password@123");
            m.put("demoAccounts", DEMO_ACCOUNTS.stream().filter(a -> users.existsByEmailIgnoreCase(a.email())).toList());
        }
        return m;
    }

    @PostMapping("/login")
    @Operation(summary = "Sign in with e-mail and password; returns a session, or a 2FA challenge")
    public AuthResponse login(@Valid @RequestBody LoginRequest r, HttpServletRequest req, HttpServletResponse res) {
        return respond(auth.login(r.email(), r.password(), RequestMeta.of(req)), req, res);
    }

    @PostMapping("/mfa/verify")
    @Operation(summary = "Second sign-in step: authenticator code or recovery code")
    public AuthResponse verifyMfa(@Valid @RequestBody MfaVerifyRequest r, HttpServletRequest req, HttpServletResponse res) {
        String challenge = r.challenge() != null && !r.challenge().isBlank() ? r.challenge()
                : cookies.read(req, AuthCookies.MFA_CHALLENGE).orElse(null);
        AuthService.SignedIn done = auth.completeMfa(challenge, r.code(), RequestMeta.of(req));
        cookies.clear(req, res, AuthCookies.MFA_CHALLENGE);
        return respond(done, req, res);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Self-register as an employee (company e-mail domains only)")
    public AuthResponse register(@Valid @RequestBody RegisterRequest r, HttpServletRequest req, HttpServletResponse res) {
        return session(auth.register(r.fullName(), r.email(), r.password(), r.phone(), r.department(),
                RequestMeta.of(req)), req, res);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange the refresh cookie for a new access token (rotates the cookie)")
    public AuthResponse refresh(HttpServletRequest req, HttpServletResponse res) {
        requireCsrfHeader(req);
        String raw = cookies.read(req, AuthCookies.REFRESH).orElseThrow(SessionExpiredException::new);
        try {
            return session(sessions.refresh(raw, RequestMeta.of(req)), req, res);
        } catch (SessionExpiredException ex) {
            cookies.clear(req, res, AuthCookies.REFRESH);
            throw ex;
        }
    }

    @PostMapping("/logout")
    @Operation(summary = "Sign out this browser (revokes the session and clears the cookie)")
    public ResponseEntity<Void> logout(HttpServletRequest req, HttpServletResponse res) {
        requireCsrfHeader(req);
        cookies.read(req, AuthCookies.REFRESH).ifPresent(raw -> sessions.signOut(raw, RequestMeta.of(req)));
        cookies.clear(req, res, AuthCookies.REFRESH);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @Transactional(readOnly = true)
    public UserView me() {
        return UserView.of(currentUser.require());
    }

    // ---- password ----------------------------------------------------------------------------------

    @PostMapping("/change-password")
    @Operation(summary = "Change (or, for Google-only accounts, set) the password; other devices are signed out")
    public AuthResponse changePassword(@Valid @RequestBody ChangePasswordRequest r, HttpServletRequest req,
                                       HttpServletResponse res) {
        User me = currentUser.require();
        return session(auth.changePassword(me, r.currentPassword(), r.newPassword(), RequestMeta.of(req)), req, res);
    }

    @PostMapping("/password/forgot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "E-mail a password reset link (same answer whether or not the account exists)")
    public Message forgotPassword(@Valid @RequestBody ForgotPasswordRequest r, HttpServletRequest req) {
        passwordReset.requestReset(r.email(), req.getRemoteAddr());
        return new Message("If an account exists for that address, a reset link is on its way.");
    }

    @PostMapping("/password/reset")
    @Operation(summary = "Set a new password using the e-mailed link")
    public Message resetPassword(@Valid @RequestBody ResetPasswordRequest r) {
        passwordReset.reset(r.token(), r.password());
        return new Message("Password changed. You can sign in now.");
    }

    // ---- sessions ----------------------------------------------------------------------------------

    @GetMapping("/sessions")
    @Operation(summary = "Devices where you are signed in")
    public List<SessionService.SessionView> mySessions() {
        return sessions.list(currentUser.require(), currentSessionId());
    }

    @DeleteMapping("/sessions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeSession(@PathVariable String id) {
        sessions.revoke(currentUser.require(), id);
    }

    @PostMapping("/sessions/revoke-others")
    public Map<String, Integer> revokeOtherSessions() {
        return Map.of("signedOut", sessions.revokeOthers(currentUser.require(), currentSessionId()));
    }

    // ---- two-factor authentication ------------------------------------------------------------------

    @GetMapping("/mfa")
    public Map<String, Object> mfaStatus() {
        User me = currentUser.require();
        return Map.of("enabled", me.isMfaEnabled(), "recoveryCodesLeft", me.isMfaEnabled() ? mfa.remainingRecoveryCodes(me) : 0,
                "required", me.isPrivileged() && settings.isEnabled(SettingKey.REQUIRE_MFA_FOR_PRIVILEGED));
    }

    @PostMapping("/mfa/setup")
    @Operation(summary = "Start two-factor set-up: returns the secret and otpauth:// URI for the QR code")
    public MfaService.Setup beginMfaSetup() {
        return mfa.beginSetup(currentUser.require());
    }

    @PostMapping("/mfa/enable")
    @Operation(summary = "Confirm set-up with a code from the app; returns ten one-time recovery codes")
    public Map<String, Object> enableMfa(@Valid @RequestBody MfaCodeRequest r) {
        return Map.of("recoveryCodes", mfa.confirmSetup(currentUser.require(), r.code()));
    }

    @PostMapping("/mfa/disable")
    public Message disableMfa(@Valid @RequestBody MfaDisableRequest r) {
        User me = currentUser.require();
        if (me.isPrivileged() && settings.isEnabled(SettingKey.REQUIRE_MFA_FOR_PRIVILEGED)) {
            throw new BusinessRuleViolationException("Two-factor authentication is required for your role");
        }
        mfa.disable(me, r.password(), r.code());
        return new Message("Two-factor authentication is off");
    }

    @PostMapping("/mfa/recovery-codes")
    @Operation(summary = "Replace the recovery codes (the old ones stop working)")
    public Map<String, Object> newRecoveryCodes(@Valid @RequestBody MfaCodeRequest r) {
        User me = currentUser.require();
        if (!mfa.verify(users.findById(me.getId()).orElseThrow(), r.code())) {
            throw new com.resolveiq.common.exception.InvalidMfaCodeException();
        }
        return Map.of("recoveryCodes", mfa.regenerateRecoveryCodes(me));
    }

    // ---- profile -----------------------------------------------------------------------------------

    @PutMapping("/profile")
    @Transactional
    @Operation(summary = "Update your own contact details (and skills, for technicians)")
    public UserView updateProfile(@Valid @RequestBody ProfileRequest r) {
        return UserView.of(profiles.update(currentUser.require(), r.fullName(), r.phone(), r.department(), r.location(),
                r.skills(), false));
    }

    @PostMapping("/profile/complete")
    @Transactional
    @Operation(summary = "First sign-in: confirm details and finish setting up the account")
    public UserView completeProfile(@Valid @RequestBody ProfileRequest r) {
        return UserView.of(profiles.update(currentUser.require(), r.fullName(), r.phone(), r.department(), r.location(),
                r.skills(), true));
    }

    // ---- helpers -----------------------------------------------------------------------------------

    private AuthResponse respond(AuthService.SignInResult result, HttpServletRequest req, HttpServletResponse res) {
        return switch (result) {
            case AuthService.SignedIn s -> session(s.session(), req, res);
            case AuthService.MfaRequired m -> {
                cookies.setMfaChallenge(req, res, m.challenge(), Duration.ofMinutes(5));
                yield AuthResponse.mfa(m.challenge(), m.expiresAt());
            }
        };
    }

    private AuthResponse session(SessionService.IssuedSession s, HttpServletRequest req, HttpServletResponse res) {
        cookies.setRefresh(req, res, s.refreshToken(), s.refreshExpiresAt());
        User u = s.user();
        boolean mfaSetupRequired = u.isPrivileged() && !u.isMfaEnabled() && settings.isEnabled(SettingKey.REQUIRE_MFA_FOR_PRIVILEGED);
        return AuthResponse.session(s.access().token(), s.access().expiresAt(), UserView.of(u), mfaSetupRequired);
    }

    private static void requireCsrfHeader(HttpServletRequest req) {
        if (!CSRF_VALUE.equals(req.getHeader(CSRF_HEADER))) {
            throw new BusinessRuleViolationException("Missing " + CSRF_HEADER + " header");
        }
    }

    private static String currentSessionId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof Jwt jwt ? jwt.getClaimAsString(JwtService.CLAIM_SESSION) : null;
    }
}
