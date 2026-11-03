package com.resolveiq.security.oauth;

import com.resolveiq.security.crypto.Tokens;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Per-attempt secrets of the OpenID Connect authorization-code flow:
 * <ul>
 *   <li>{@code state} ties Google's redirect back to the browser that started it (stops login CSRF);</li>
 *   <li>{@code nonce} is echoed inside the ID token (stops token replay);</li>
 *   <li>{@code codeVerifier} is PKCE (RFC 7636): even an intercepted authorization code is useless
 *       without it.</li>
 * </ul>
 * They are kept in a signed, HttpOnly, 10-minute cookie between the two legs of the flow.
 */
public record AuthorizationRequest(String state, String nonce, String codeVerifier, String redirectUri,
                                   String returnTo) {

    public static AuthorizationRequest create(String redirectUri, String returnTo) {
        return new AuthorizationRequest(Tokens.random(24), Tokens.random(24), Tokens.random(48), redirectUri, returnTo);
    }

    /** S256 code challenge sent to the provider. */
    public String codeChallenge() {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(Tokens.sha256Bytes(codeVerifier.getBytes(StandardCharsets.US_ASCII)));
    }
}
