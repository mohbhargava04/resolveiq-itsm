package com.resolveiq.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.resolveiq.user.domain.Role;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Security architecture:
 * <ul>
 *   <li><b>Authentication</b>: e-mail + password (BCrypt-hashed, strength 12) exchanged for a signed JWT.</li>
 *   <li><b>Stateless</b>: every request carries {@code Authorization: Bearer <token>}; no sessions, so CSRF
 *       protection is unnecessary (tokens are not sent automatically by the browser).</li>
 *   <li><b>Role-based authorisation</b> with a hierarchy - a manager can do everything an admin can,
 *       an admin everything a team lead can, a team lead everything a technician can. Enforced both
 *       per-URL here and per-method with {@code @PreAuthorize}.</li>
 *   <li><b>Row-level rules</b> (employees see only their incidents) live in the domain/service layer.</li>
 *   <li>Security headers (CSP, frame options, referrer policy), restricted CORS, JSON error responses.</li>
 * </ul>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityProperties props, ObjectMapper mapper)
            throws Exception {
        DefaultBearerTokenResolver tokenResolver = new DefaultBearerTokenResolver();
        // EventSource (server-sent events) cannot set headers, so the live stream may pass ?access_token=
        tokenResolver.setAllowUriQueryParameter(true);

        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(c -> c.configurationSource(corsConfigurationSource(props)))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> h
                        .frameOptions(f -> f.sameOrigin())
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; "
                                        + "img-src 'self' data:; font-src 'self' data:; connect-src 'self'; frame-ancestors 'self'")))
                .authorizeHttpRequests(auth -> auth
                        // async dispatches complete an already-authorised SSE stream; error dispatches render errors
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR, DispatcherType.FORWARD).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/auth/login", "/api/auth/register").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/h2-console/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole(Role.ADMIN.name())
                        .requestMatchers("/api/audit/**").hasRole(Role.ADMIN.name())
                        .requestMatchers("/api/reports/**").hasRole(Role.TEAM_LEAD.name())
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .oauth2ResourceServer(o -> o
                        .bearerTokenResolver(tokenResolver)
                        .jwt(j -> j.jwtAuthenticationConverter(jwtAuthenticationConverter()))
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

    @Bean
    JwtDecoder jwtDecoder(SecretKey key) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(JwtService.ISSUER));
        return decoder;
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String role = jwt.getClaimAsString(JwtService.CLAIM_ROLE);
            Collection<GrantedAuthority> authorities = role == null ? List.of()
                    : List.of(new SimpleGrantedAuthority("ROLE_" + role));
            return authorities;
        });
        return converter;
    }

    private CorsConfigurationSource corsConfigurationSource(SecurityProperties props) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(props.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
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
