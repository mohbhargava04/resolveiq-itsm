package com.resolveiq.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** {@code itsm.security.*} settings. */
@ConfigurationProperties(prefix = "itsm.security")
public record SecurityProperties(String jwtSecret, int tokenValidityMinutes, int maxFailedLogins, int lockoutMinutes,
                                 List<String> allowedOrigins, int bcryptStrength) {

    public SecurityProperties {
        if (jwtSecret == null || jwtSecret.length() < 32) {
            throw new IllegalStateException("itsm.security.jwt-secret must be at least 32 characters");
        }
        if (tokenValidityMinutes <= 0) {
            tokenValidityMinutes = 480;
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
    }
}
