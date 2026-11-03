package com.resolveiq.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Deployment-level settings.
 *
 * @param publicUrl where users reach the app, used in e-mailed links and as the base of the Google
 *                  sign-in redirect address (e.g. {@code https://resolveiq.onrender.com})
 * @param demoMode  shows the demo accounts on the sign-in page; switch off for a real deployment
 */
@ConfigurationProperties(prefix = "itsm")
public record AppProperties(String publicUrl, Boolean demoMode) {

    public AppProperties {
        if (publicUrl == null || publicUrl.isBlank()) {
            publicUrl = "http://localhost:8080";
        }
        publicUrl = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
        if (demoMode == null) {
            demoMode = true;
        }
    }

    public boolean isDemo() {
        return demoMode;
    }
}
