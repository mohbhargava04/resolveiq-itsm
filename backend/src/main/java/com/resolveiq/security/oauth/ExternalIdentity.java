package com.resolveiq.security.oauth;

/**
 * A person as vouched for by an identity provider, after the provider's signed ID token has been
 * verified.
 *
 * @param subject       the provider's permanent id for the account (never reused, unlike e-mail addresses)
 * @param hostedDomain  Google Workspace domain ({@code hd} claim); empty for personal Gmail accounts
 */
public record ExternalIdentity(String provider, String subject, String email, boolean emailVerified, String name,
                               String pictureUrl, String hostedDomain) {
}
