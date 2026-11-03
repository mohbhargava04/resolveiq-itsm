package com.resolveiq.security.oauth;

import java.net.URI;
import java.util.List;

/**
 * <b>Adapter pattern</b>: one interface for every external sign-in provider. The sign-in flow only
 * knows this contract; Google is one adapter, and Microsoft Entra ID or Okta would be another.
 * Tests plug in a fake adapter so the whole flow can be exercised without contacting Google.
 */
public interface IdentityProvider {

    /** Short id used in URLs, e.g. {@code google}. */
    String id();

    String displayName();

    boolean isEnabled();

    /** Where to send the browser to sign in. {@code domainHints} pre-selects the company account. */
    URI authorizationUri(AuthorizationRequest request, List<String> domainHints);

    /**
     * Exchanges the authorization code for tokens and verifies the ID token (signature, issuer,
     * audience, expiry, nonce).
     *
     * @throws FederatedSignInException if anything does not check out
     */
    ExternalIdentity exchange(String code, AuthorizationRequest request);
}
