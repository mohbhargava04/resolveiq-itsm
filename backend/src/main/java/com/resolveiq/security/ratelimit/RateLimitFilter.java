package com.resolveiq.security.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resolveiq.common.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Applies per-client-address limits before a request reaches Spring Security. The strictest limits
 * protect the endpoints that guess or create secrets (sign-in, 2FA, password reset, registration);
 * a generous general limit protects the rest of the API from scripted abuse. Refused requests get
 * HTTP 429 with a {@code Retry-After} header.
 *
 * <p>Together with the per-account lockout this blocks both attacks on one account (lockout) and
 * attacks that try one common password against many accounts (this filter).</p>
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    /** One limit: requests matching {@code method} and {@code pathPrefix} share a bucket per address. */
    public record Rule(String name, String method, String pathPrefix, int capacity, Duration window) {
        boolean matches(HttpServletRequest r) {
            return (method == null || method.equalsIgnoreCase(r.getMethod())) && r.getRequestURI().startsWith(pathPrefix);
        }
    }

    public static final List<Rule> RULES = List.of(
            new Rule("login", "POST", "/api/auth/login", 10, Duration.ofMinutes(1)),
            new Rule("mfa", "POST", "/api/auth/mfa/verify", 10, Duration.ofMinutes(5)),
            new Rule("register", "POST", "/api/auth/register", 5, Duration.ofMinutes(10)),
            new Rule("password-reset", "POST", "/api/auth/password/", 5, Duration.ofMinutes(15)),
            new Rule("refresh", "POST", "/api/auth/refresh", 60, Duration.ofMinutes(1)),
            new Rule("oauth", "GET", "/api/auth/oauth/", 20, Duration.ofMinutes(1)),
            new Rule("inbound-email", "POST", "/api/inbound/", 60, Duration.ofMinutes(1)),
            new Rule("api", null, "/api/", 600, Duration.ofMinutes(1)));

    private final TokenBucketRateLimiter limiter;
    private final RateLimitProperties props;
    private final ObjectMapper mapper;

    public RateLimitFilter(TokenBucketRateLimiter limiter, RateLimitProperties props, ObjectMapper mapper) {
        this.limiter = limiter;
        this.props = props;
        this.mapper = mapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !props.enabled() || !request.getRequestURI().startsWith("/api/")
                || "OPTIONS".equalsIgnoreCase(request.getMethod())
                || props.exemptAddresses().contains(request.getRemoteAddr());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String client = request.getRemoteAddr();
        for (Rule rule : RULES) {
            if (!rule.matches(request)) {
                continue;
            }
            TokenBucketRateLimiter.Decision d = limiter.tryAcquire(rule.name() + "|" + client, rule.capacity(), rule.window());
            if (!d.allowed()) {
                log.warn("Rate limit '{}' exceeded by {} on {} {}", rule.name(), client, request.getMethod(),
                        request.getRequestURI());
                reject(request, response, d.retryAfterSeconds());
                return;
            }
            // only the first (most specific) matching rule applies
            break;
        }
        chain.doFilter(request, response);
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, long retryAfter) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                "Too many attempts. Please wait " + retryAfter + " seconds and try again.");
        problem.setTitle(ErrorCode.RATE_LIMITED.title());
        problem.setProperty("code", ErrorCode.RATE_LIMITED.name());
        problem.setProperty("retryAfterSeconds", retryAfter);
        problem.setProperty("path", request.getRequestURI());
        problem.setProperty("timestamp", Instant.now().toString());
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(retryAfter));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), problem);
    }
}
