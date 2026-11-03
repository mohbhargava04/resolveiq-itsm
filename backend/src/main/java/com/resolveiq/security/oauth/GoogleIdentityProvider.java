package com.resolveiq.security.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Set;

/**
 * Google sign-in using OpenID Connect (authorization-code flow with PKCE).
 *
 * <ol>
 *   <li>The browser is sent to Google with our client id, {@code state}, {@code nonce} and PKCE challenge.</li>
 *   <li>Google sends it back with a one-time {@code code}.</li>
 *   <li>The server (not the browser) exchanges the code plus the client secret and PKCE verifier for
 *       an ID token, then verifies it: RS256 signature against Google's published keys, issuer,
 *       audience = our client id, not expired, and the nonce we generated.</li>
 * </ol>
 */
public class GoogleIdentityProvider implements IdentityProvider {

    private static final Logger log = LoggerFactory.getLogger(GoogleIdentityProvider.class);

    static final String AUTHORIZE = "https://accounts.google.com/o/oauth2/v2/auth";
    static final String TOKEN = "https://oauth2.googleapis.com/token";
    static final String JWKS = "https://www.googleapis.com/oauth2/v3/certs";
    static final Set<String> ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");

    private final OAuthProperties.Client client;
    private final RestClient http;
    private volatile NimbusJwtDecoder decoder;

    public GoogleIdentityProvider(OAuthProperties.Client client, RestClient.Builder http) {
        this.client = client;
        this.http = http.build();
    }

    @Override
    public String id() {
        return "google";
    }

    @Override
    public String displayName() {
        return "Google";
    }

    @Override
    public boolean isEnabled() {
        return client.configured();
    }

    @Override
    public URI authorizationUri(AuthorizationRequest r, List<String> domainHints) {
        UriComponentsBuilder b = UriComponentsBuilder.fromUriString(AUTHORIZE)
                .queryParam("client_id", client.clientId())
                .queryParam("redirect_uri", r.redirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", "openid email profile")
                .queryParam("state", r.state())
                .queryParam("nonce", r.nonce())
                .queryParam("code_challenge", r.codeChallenge())
                .queryParam("code_challenge_method", "S256")
                .queryParam("prompt", "select_account");
        if (domainHints.size() == 1) {
            b.queryParam("hd", domainHints.getFirst()); // pre-selects the company account; enforced again on return
        }
        return b.encode().build().toUri();
    }

    @Override
    public ExternalIdentity exchange(String code, AuthorizationRequest r) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", client.clientId());
        form.add("client_secret", client.clientSecret());
        form.add("redirect_uri", r.redirectUri());
        form.add("grant_type", "authorization_code");
        form.add("code_verifier", r.codeVerifier());
        JsonNode response;
        try {
            response = http.post().uri(TOKEN).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
                    .retrieve().body(JsonNode.class);
        } catch (RestClientException ex) {
            log.warn("Google token exchange failed: {}", ex.getMessage());
            throw new FederatedSignInException("provider_error", "Google did not accept the sign-in", ex);
        }
        String idToken = response == null || response.get("id_token") == null ? null : response.get("id_token").asText();
        if (idToken == null) {
            throw new FederatedSignInException("provider_error", "Google returned no ID token");
        }
        Jwt jwt;
        try {
            jwt = decoder().decode(idToken);
        } catch (JwtException ex) {
            throw new FederatedSignInException("invalid_token", "The ID token from Google could not be verified", ex);
        }
        if (!r.nonce().equals(jwt.getClaimAsString("nonce"))) {
            throw new FederatedSignInException("invalid_token", "ID token nonce mismatch");
        }
        Boolean verified = jwt.getClaimAsBoolean("email_verified");
        return new ExternalIdentity(id(), jwt.getSubject(), jwt.getClaimAsString("email"), Boolean.TRUE.equals(verified),
                jwt.getClaimAsString("name"), jwt.getClaimAsString("picture"), jwt.getClaimAsString("hd"));
    }

    private NimbusJwtDecoder decoder() {
        NimbusJwtDecoder d = decoder;
        if (d == null) {
            d = NimbusJwtDecoder.withJwkSetUri(JWKS).build();
            OAuth2TokenValidator<Jwt> issuer = jwt -> jwt.getIssuer() != null && ISSUERS.contains(jwt.getIssuer().toString())
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Wrong issuer", null));
            OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience() != null && jwt.getAudience().contains(client.clientId())
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Token is for another app", null));
            d.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(), issuer, audience));
            decoder = d;
        }
        return d;
    }
}
