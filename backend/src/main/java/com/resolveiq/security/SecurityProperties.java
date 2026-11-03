package com.resolveiq.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * {@code itsm.security.*} settings.
 *
 * @param jwtSecret               HMAC-SHA256 key for access tokens (at least 32 characters)
 * @param dataKey                 key for encrypting secrets at rest, e.g. two-factor secrets (at least 32 characters)
 * @param tokenValidityMinutes    lifetime of an access token; short, because it cannot be recalled once stolen
 * @param refreshTokenDays        a session stays signed in this long without activity
 * @param sessionMaxDays          absolute lifetime of a session, however active
 * @param refreshReuseGraceSeconds two tabs refreshing at the same moment is not an attack; reuse inside this window is tolerated
 * @param maxFailedLogins         wrong passwords (or 2FA codes) before the account is locked
 * @param lockoutMinutes          how long a locked account stays locked
 * @param allowedOrigins          CORS origins allowed to call the API from a browser (the dev server)
 * @param bcryptStrength          BCrypt work factor
 * @param exposeDevTools          whether Swagger UI and the H2 console are reachable without signing in
 */
@ConfigurationProperties(prefix = "itsm.security")
public record SecurityProperties(String jwtSecret, String dataKey, int tokenValidityMinutes, int refreshTokenDays,
                                 int sessionMaxDays, int refreshReuseGraceSeconds, int maxFailedLogins,
                                 int lockoutMinutes, List<String> allowedOrigins, int bcryptStrength,
                                 Boolean exposeDevTools) {

    public SecurityProperties {
        if (jwtSecret == null || jwtSecret.length() < 32) {
            throw new IllegalStateException("itsm.security.jwt-secret must be at least 32 characters");
        }
        if (dataKey == null || dataKey.isBlank()) {
            dataKey = "data:" + jwtSecret;
        }
        if (dataKey.length() < 32) {
            throw new IllegalStateException("itsm.security.data-key must be at least 32 characters");
        }
        if (tokenValidityMinutes <= 0) {
            tokenValidityMinutes = 15;
        }
        if (refreshTokenDays <= 0) {
            refreshTokenDays = 7;
        }
        if (sessionMaxDays <= 0) {
            sessionMaxDays = 30;
        }
        if (refreshReuseGraceSeconds < 0) {
            refreshReuseGraceSeconds = 20;
        }
        if (maxFailedLogins <= 0) {
            maxFailedLogins = 5;
        }
        if (lockoutMinutes <= 0) {
            lockoutMinutes = 15;
        }
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
        if (bcryptStrength < 4 || bcryptStrength > 16) {
            bcryptStrength = 12;
        }
        if (exposeDevTools == null) {
            exposeDevTools = false;
        }
    }
}
