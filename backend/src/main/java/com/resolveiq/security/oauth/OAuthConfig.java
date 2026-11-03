package com.resolveiq.security.oauth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class OAuthConfig {

    @Bean
    IdentityProvider googleIdentityProvider(OAuthProperties props, RestClient.Builder http) {
        return new GoogleIdentityProvider(props.google(), http);
    }
}
