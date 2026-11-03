package com.resolveiq.security.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * {@code itsm.security.rate-limit.*}
 *
 * @param enabled         switch the limiter off entirely (never in production)
 * @param exemptAddresses client addresses that are never limited (the test suite's loopback address)
 */
@ConfigurationProperties(prefix = "itsm.security.rate-limit")
public record RateLimitProperties(Boolean enabled, List<String> exemptAddresses) {

    public RateLimitProperties {
        enabled = enabled == null || enabled;
        exemptAddresses = exemptAddresses == null ? List.of() : List.copyOf(exemptAddresses);
    }
}
