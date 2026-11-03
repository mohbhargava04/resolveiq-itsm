package com.resolveiq.security.oauth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code itsm.oauth.*} - credentials of the OAuth clients registered with each identity provider.
 * Google sign-in switches on automatically once a client id and secret are configured.
 */
@ConfigurationProperties(prefix = "itsm.oauth")
public record OAuthProperties(Client google) {

    public record Client(String clientId, String clientSecret) {
        public boolean configured() {
            return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
        }
    }

    public OAuthProperties {
        if (google == null) {
            google = new Client(null, null);
        }
    }
}
