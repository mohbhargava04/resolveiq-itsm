package com.resolveiq.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.resolveiq.security.ratelimit.RateLimitFilter;
import com.resolveiq.security.ratelimit.RateLimitProperties;
import com.resolveiq.security.ratelimit.TokenBucketRateLimiter;
import com.resolveiq.user.domain.Role;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

/**
 * Security architecture (see docs/SECURITY.md for the full OWASP Top 10 mapping):
 * <ul>
 *   <li><b>Authentication</b>: password (BCrypt) or Google (OpenID Connect + PKCE), optionally followed
 *       by a TOTP two-factor code. The result is a session: a 15-minute access token sent as
 *       {@code Authorization: Bearer} plus a rotating refresh token in an HttpOnly cookie.</li>
 *   <li><b>Stateless API</b>: no server-side HTTP session. CSRF protection is unnecessary for the API
 *       because the access token is never sent automatically by the browser; the cookie endpoints
 *       (refresh, sign-out) are {@code SameSite=Strict} and also require a custom header.</li>
 *   <li><b>Every request is re-checked</b> against the database ({@link TokenAuthenticationConverter}):
 *       active account, token version, live session, current role.</li>
 *   <li><b>Role-based authorisation</b> with a hierarchy, per URL here and per method with
 *       {@code @PreAuthorize}; row-level rules (employees see only their incidents) in the domain.</li>
 *   <li>Per-address <b>rate limiting</b>, strict security headers, restricted CORS, problem-JSON errors.</li>
 * </ul>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * Content Security Policy: only this site's own scripts may run (no inline scripts, no third-party
     * code), so an injected {@code <script>} is refused by the browser. Google profile pictures are the
     * only external images. {@code style-src 'unsafe-inline'} is kept because UI components set
     * inline styles; styles cannot run code.
     */
    static final String CSP = "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
            + "img-src 'self' data: blob: https://lh3.googleusercontent.com; font-src 'self' data:; "
            + "connect-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'self'";

    static final String[] PUBLIC_AUTH = {"/api/auth/options", "/api/auth/login", "/api/auth/register",
            "/api/auth/refresh", "/api/auth/logout", "/api/auth/mfa/verify", "/api/auth/password/**",
            "/api/auth/oauth/**"};

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityProperties props, ObjectMapper mapper,
                                            TokenAuthenticationConverter converter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(c -> c.configurationSource(corsConfigurationSource(props)))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> h
                        .frameOptions(f -> f.sameOrigin())
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CSP))
                        .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy",
                                "camera=(), microphone=(), geolocation=(), payment=(), usb=()"))
                        .addHeaderWriter(new StaticHeadersWriter("Cross-Origin-Opener-Policy", "same-origin"))
                        .addHeaderWriter(new StaticHeadersWriter("Cross-Origin-Resource-Policy", "same-origin")))
                .authorizeHttpRequests(auth -> {
                    // async dispatches complete an already-authorised SSE stream; error dispatches render errors
                    auth.dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR, DispatcherType.FORWARD).permitAll();
                    auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
                    auth.requestMatchers(PUBLIC_AUTH).permitAll();
                    // authenticated by a one-time ticket (EventSource cannot send headers)
                    auth.requestMatchers(HttpMethod.GET, "/api/notifications/stream").permitAll();
                    // authenticated by a shared secret header from the mail provider
                    auth.requestMatchers(HttpMethod.POST, "/api/inbound/email").permitAll();
                    auth.requestMatchers("/actuator/health", "/actuator/info").permitAll();
                    if (props.exposeDevTools()) {
                        auth.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/h2-console/**").permitAll();
                    } else {
                        auth.requestMatchers("/h2-console/**").denyAll();
                        auth.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").hasRole(Role.ADMIN.name());
                    }
                    auth.requestMatchers("/api/admin/**").hasRole(Role.ADMIN.name());
                    auth.requestMatchers("/api/audit/**").hasRole(Role.ADMIN.name());
                    auth.requestMatchers("/api/reports/**").hasRole(Role.TEAM_LEAD.name());
                    auth.requestMatchers("/api/**").authenticated();
                    auth.anyRequest().permitAll();
                })
                .oauth2ResourceServer(o -> o
                        .jwt(j -> j.jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint((req, res, ex) -> writeProblem(res, mapper, HttpStatus.UNAUTHORIZED,
                                "UNAUTHENTICATED", "Authentication required: missing, invalid or expired token",
                                req.getRequestURI()))
                        .accessDeniedHandler((req, res, ex) -> writeProblem(res, mapper, HttpStatus.FORBIDDEN,
                                "UNAUTHORIZED_OPERATION", "Your role is not allowed to perform this operation",
                                req.getRequestURI())))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> writeProblem(res, mapper, HttpStatus.UNAUTHORIZED,
                                "UNAUTHENTICATED", "Authentication required", req.getRequestURI()))
                        .accessDeniedHandler((req, res, ex) -> writeProblem(res, mapper, HttpStatus.FORBIDDEN,
                                "UNAUTHORIZED_OPERATION", "Your role is not allowed to perform this operation",
                                req.getRequestURI())));
        return http.build();
    }

    /** MANAGER > ADMIN > TEAM_LEAD > TECHNICIAN. Employees are a separate branch. */
    @Bean
    static RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.fromHierarchy("""
                ROLE_MANAGER > ROLE_ADMIN
                ROLE_ADMIN > ROLE_TEAM_LEAD
                ROLE_TEAM_LEAD > ROLE_TECHNICIAN
                """);
    }

    /** BCrypt with a per-password salt; work factor 12 in production (lowered only in tests for speed). */
    @Bean
    PasswordEncoder passwordEncoder(SecurityProperties props) {
        return new BCryptPasswordEncoder(props.bcryptStrength());
    }

    @Bean
    SecretKey jwtSigningKey(SecurityProperties props) {
        return new SecretKeySpec(props.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey key) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    /** Decoder for API requests: only {@code typ=access} tokens are accepted. */
    @Bean
    @Primary
    JwtDecoder jwtDecoder(SecretKey key) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> accessOnly = jwt -> JwtService.TYPE_ACCESS.equals(jwt.getClaimAsString(JwtService.CLAIM_TYPE))
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Not an access token", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(JwtService.ISSUER), accessOnly));
        return decoder;
    }

    /** Decoder for sign-in challenge tokens (2FA step, Google sign-in state). */
    @Bean
    JwtDecoder challengeJwtDecoder(SecretKey key) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(JwtService.ISSUER));
        return decoder;
    }

    @Bean
    TokenBucketRateLimiter rateLimiter() {
        return new TokenBucketRateLimiter();
    }

    /** Runs before Spring Security, so floods are refused before any token or password work. */
    @Bean
    FilterRegistrationBean<RateLimitFilter> rateLimitFilter(TokenBucketRateLimiter limiter, RateLimitProperties props,
                                                            ObjectMapper mapper) {
        FilterRegistrationBean<RateLimitFilter> bean = new FilterRegistrationBean<>(new RateLimitFilter(limiter, props, mapper));
        bean.setOrder(-110); // Spring Security's filter chain runs at -100
        bean.addUrlPatterns("/api/*");
        return bean;
    }

    private CorsConfigurationSource corsConfigurationSource(SecurityProperties props) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(props.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private static void writeProblem(jakarta.servlet.http.HttpServletResponse res, ObjectMapper mapper,
                                     HttpStatus status, String code, String detail, String path)
            throws java.io.IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("code", code);
        problem.setProperty("path", path);
        problem.setProperty("timestamp", Instant.now().toString());
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        mapper.writeValue(res.getOutputStream(), problem);
    }
}
