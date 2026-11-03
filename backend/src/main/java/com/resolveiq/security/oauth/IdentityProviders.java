package com.resolveiq.security.oauth;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** Registry of the configured external sign-in providers (Google today). */
@Component
public class IdentityProviders {

    private final List<IdentityProvider> providers;

    public IdentityProviders(List<IdentityProvider> providers) {
        this.providers = List.copyOf(providers);
    }

    public Optional<IdentityProvider> enabled(String id) {
        return providers.stream().filter(p -> p.id().equalsIgnoreCase(id) && p.isEnabled()).findFirst();
    }

    public List<IdentityProvider> enabledProviders() {
        return providers.stream().filter(IdentityProvider::isEnabled).toList();
    }
}
