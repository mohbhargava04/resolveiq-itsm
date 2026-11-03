package com.resolveiq.security.session;

import com.resolveiq.audit.AuditAction;
import com.resolveiq.audit.AuditService;
import com.resolveiq.common.exception.ResourceNotFoundException;
import com.resolveiq.common.exception.SessionExpiredException;
import com.resolveiq.security.JwtService;
import com.resolveiq.security.SecurityProperties;
import com.resolveiq.security.crypto.Tokens;
import com.resolveiq.user.UserRepository;
import com.resolveiq.user.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Signed-in sessions.
 *
 * <p>Signing in creates a session: a short-lived <b>access token</b> (kept in the page's memory) and a
 * long-lived <b>refresh token</b> (kept in an HttpOnly cookie that JavaScript cannot read). When the
 * access token expires the browser silently exchanges the refresh token for a new pair.</p>
 *
 * <p><b>Rotation with reuse detection</b>: each refresh token works once. If an already-used token is
 * presented again, someone has a copy of it - the whole session is revoked and the event is logged.
 * Two tabs refreshing within a few seconds of each other is normal and is tolerated.</p>
 */
@Service
@Transactional
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    /** A new session: the access token for the response body and the refresh token for the cookie. */
    public record IssuedSession(User user, JwtService.IssuedToken access, String refreshToken,
                                Instant refreshExpiresAt, String sessionId) {
    }

    /** One device/browser where the user is signed in. */
    public record SessionView(String id, String device, String ipAddress, String method, Instant signedInAt,
                              Instant lastActiveAt, Instant expiresAt, boolean current) {
    }

    private final RefreshTokenRepository tokens;
    private final UserRepository users;
    private final JwtService jwt;
    private final AuditService audit;
    private final SecurityProperties props;
    private final Clock clock;

    public SessionService(RefreshTokenRepository tokens, UserRepository users, JwtService jwt, AuditService audit,
                          SecurityProperties props, Clock clock) {
        this.tokens = tokens;
        this.users = users;
        this.jwt = jwt;
        this.audit = audit;
        this.props = props;
        this.clock = clock;
    }

    public IssuedSession start(User user, SignInMethod method, RequestMeta meta) {
        Instant now = clock.instant();
        String family = UUID.randomUUID().toString();
        return issue(user, family, now, method, meta, now);
    }

    /**
     * Exchanges a refresh token for a new access token and a new refresh token.
     * {@code noRollbackFor}: revoking a session on reuse must be saved even though the call fails.
     */
    @Transactional(noRollbackFor = SessionExpiredException.class)
    public IssuedSession refresh(String rawToken, RequestMeta meta) {
        Instant now = clock.instant();
        RefreshToken token = find(rawToken).orElseThrow(SessionExpiredException::new);
        User user = token.getUser();
        String email = user.getEmail(); // initialises the user before any bulk update clears the context

        if (token.getRevokedAt() != null) {
            boolean benignRace = token.wasRotated() && token.getRotatedAt() != null
                    && Duration.between(token.getRotatedAt(), now).getSeconds() <= props.refreshReuseGraceSeconds()
                    && tokens.isFamilyAlive(token.getFamilyId());
            if (!benignRace) {
                if (token.wasRotated()) {
                    int revoked = tokens.revokeFamily(token.getFamilyId(), "REUSE_DETECTED", now);
                    log.warn("Refresh token reuse for {} - session {} revoked ({} tokens)", email,
                            token.getFamilyId(), revoked);
                    audit.recordSecurityEvent(user, email, AuditAction.SESSION_REUSE_DETECTED,
                            "A used sign-in token was presented again" + from(meta)
                                    + ". The session was signed out to protect the account.");
                }
                throw new SessionExpiredException();
            }
            // two tabs refreshed at the same moment: hand out another token in the same session
        } else {
            token.rotate(now);
        }
        boolean familyTooOld = token.getFamilyStarted().plus(Duration.ofDays(props.sessionMaxDays())).isBefore(now);
        if (!token.getExpiresAt().isAfter(now) || familyTooOld || !user.isActive()) {
            tokens.revokeFamily(token.getFamilyId(), familyTooOld ? "MAX_AGE" : "EXPIRED", now);
            throw new SessionExpiredException();
        }
        tokens.saveAndFlush(token);
        return issue(user, token.getFamilyId(), token.getFamilyStarted(), token.getSignInMethod(), meta, now);
    }

    /** Signs out the session that owns this refresh token (the current browser). */
    public Optional<User> signOut(String rawToken, RequestMeta meta) {
        return find(rawToken).map(token -> {
            User user = token.getUser();
            String email = user.getEmail();
            tokens.revokeFamily(token.getFamilyId(), "SIGNED_OUT", clock.instant());
            audit.recordSecurityEvent(user, email, AuditAction.SIGNED_OUT, "Signed out" + from(meta));
            return user;
        });
    }

    /** Used on every request: an access token stops working as soon as its session is signed out. */
    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public boolean isAlive(String sessionId) {
        return sessionId != null && tokens.isFamilyAlive(sessionId);
    }

    @Transactional(readOnly = true)
    public List<SessionView> list(User user, String currentSessionId) {
        Instant now = clock.instant();
        Map<String, RefreshToken> newestPerFamily = new LinkedHashMap<>();
        for (RefreshToken t : tokens.findActiveForUser(user.getId(), now)) {
            newestPerFamily.putIfAbsent(t.getFamilyId(), t);
        }
        return newestPerFamily.values().stream()
                .map(t -> new SessionView(t.getFamilyId(), describeDevice(t.getUserAgent()), t.getIpAddress(),
                        t.getSignInMethod().label(), t.getFamilyStarted(), t.getIssuedAt(), t.getExpiresAt(),
                        t.getFamilyId().equals(currentSessionId)))
                .sorted(Comparator.comparing(SessionView::current).reversed()
                        .thenComparing(SessionView::lastActiveAt, Comparator.reverseOrder()))
                .toList();
    }

    public void revoke(User user, String sessionId) {
        boolean owned = tokens.findByFamilyId(sessionId).stream().anyMatch(t -> t.getUser().getId().equals(user.getId()));
        if (!owned) {
            throw new ResourceNotFoundException("Session", sessionId);
        }
        tokens.revokeFamily(sessionId, "REVOKED_BY_USER", clock.instant());
        audit.recordSecurityEvent(user, user.getEmail(), AuditAction.SESSIONS_REVOKED, "Signed out one other device");
    }

    public int revokeOthers(User user, String keepSessionId) {
        int n = tokens.revokeOthersForUser(user.getId(), keepSessionId == null ? "" : keepSessionId,
                "REVOKED_BY_USER", clock.instant());
        audit.recordSecurityEvent(user, user.getEmail(), AuditAction.SESSIONS_REVOKED, "Signed out all other devices");
        return n;
    }

    /**
     * Ends every session at once and invalidates all access tokens already issued
     * (deactivation, password change or reset, role change, admin "sign out everywhere").
     */
    public void revokeAll(User user, String reason) {
        tokens.revokeAllForUser(user.getId(), reason, clock.instant());
        // bump the version on the caller's own object too, so a session started next carries the new version
        user.revokeAllSessions();
        users.save(user);
    }

    @Transactional(readOnly = true)
    public long activeSessionCount() {
        return tokens.countActiveSessions(clock.instant());
    }

    /** Housekeeping: drop tokens that expired more than a day ago. */
    @Scheduled(cron = "0 17 3 * * *")
    public void purgeExpired() {
        int n = tokens.deleteExpiredBefore(clock.instant().minus(Duration.ofDays(1)));
        if (n > 0) {
            log.info("Purged {} expired refresh tokens", n);
        }
    }

    // ---- helpers ---------------------------------------------------------------------------------

    private IssuedSession issue(User user, String family, Instant familyStarted, SignInMethod method,
                                RequestMeta meta, Instant now) {
        String raw = Tokens.random(32);
        Instant idleExpiry = now.plus(Duration.ofDays(props.refreshTokenDays()));
        Instant absoluteExpiry = familyStarted.plus(Duration.ofDays(props.sessionMaxDays()));
        Instant expires = idleExpiry.isBefore(absoluteExpiry) ? idleExpiry : absoluteExpiry;
        tokens.save(new RefreshToken(user, Tokens.sha256(raw), family, familyStarted, now, expires, method,
                meta == null ? RequestMeta.UNKNOWN : meta));
        return new IssuedSession(user, jwt.issue(user, family), raw, expires, family);
    }

    private Optional<RefreshToken> find(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > 200) {
            return Optional.empty();
        }
        return tokens.findByTokenHash(Tokens.sha256(raw));
    }

    private static String from(RequestMeta meta) {
        return meta == null || meta.ipAddress() == null ? "" : " from " + meta.ipAddress();
    }

    /** "Chrome on macOS" from a User-Agent header - good enough for a sessions list. */
    static String describeDevice(String ua) {
        if (ua == null || ua.isBlank()) {
            return "Unknown device";
        }
        String browser = ua.contains("Edg/") ? "Edge" : ua.contains("OPR/") ? "Opera"
                : ua.contains("Firefox/") ? "Firefox" : ua.contains("Chrome/") ? "Chrome"
                : ua.contains("Safari/") ? "Safari" : ua.contains("curl") ? "curl" : "Browser";
        String os = ua.contains("iPhone") || ua.contains("iPad") ? "iOS" : ua.contains("Android") ? "Android"
                : ua.contains("Mac OS X") || ua.contains("Macintosh") ? "macOS" : ua.contains("Windows") ? "Windows"
                : ua.contains("Linux") ? "Linux" : null;
        return os == null ? browser : browser + " on " + os;
    }
}
