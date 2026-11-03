package com.resolveiq.security;

import com.resolveiq.security.ratelimit.TokenBucketRateLimiter;
import com.resolveiq.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitAndHeadersIntegrationTest extends IntegrationTestBase {

    @Autowired
    private TokenBucketRateLimiter limiter;

    @BeforeEach
    void clearBuckets() {
        limiter.reset();
    }

    private MvcResult loginFrom(String ip, String email) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post("/api/auth/login").with(r -> {
            r.setRemoteAddr(ip);
            return r;
        }).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("email", email, "password", "wrong-1234"))))
                .andReturn();
    }

    @Test
    @DisplayName("SEC-16 More than 10 sign-in attempts a minute from one address get HTTP 429 with Retry-After")
    void loginRateLimited() throws Exception {
        for (int i = 0; i < 10; i++) {
            // spread over many accounts, so the per-account lockout never triggers: this is password spraying
            assertThat(loginFrom("203.0.113.7", "nobody" + i + "@resolveiq.test").getResponse().getStatus()).isEqualTo(401);
        }
        MvcResult blocked = loginFrom("203.0.113.7", "nobody11@resolveiq.test");
        assertThat(blocked.getResponse().getStatus()).isEqualTo(429);
        assertThat(blocked.getResponse().getHeader("Retry-After")).isNotBlank();
        assertThat(blocked.getResponse().getContentAsString()).contains("RATE_LIMITED");
        assertThat(loginFrom("198.51.100.4", "nobody@resolveiq.test").getResponse().getStatus())
                .as("other addresses are unaffected").isEqualTo(401);
    }

    @Test
    @DisplayName("SEC-17 Responses carry a strict Content-Security-Policy (no inline scripts) and other security headers")
    void securityHeaders() throws Exception {
        MvcResult r = mvc.perform(MockMvcRequestBuilders.get("/api/auth/options")).andReturn();
        String csp = r.getResponse().getHeader("Content-Security-Policy");
        assertThat(csp).contains("script-src 'self';").doesNotContain("script-src 'self' 'unsafe-inline'")
                .contains("object-src 'none'").contains("frame-ancestors 'self'");
        assertThat(r.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(r.getResponse().getHeader("Permissions-Policy")).contains("camera=()");
        assertThat(r.getResponse().getHeader("Referrer-Policy")).isEqualTo("strict-origin-when-cross-origin");
    }

    @Test
    @DisplayName("SEC-18 The live-update stream needs a one-time ticket; tokens are never put in URLs")
    void liveTickets() throws Exception {
        assertThat(mvc.perform(MockMvcRequestBuilders.get("/api/notifications/stream").param("access_token", token("ava"))).andReturn()
                .getResponse().getStatus()).isIn(400, 401);
        String ticket = post("ava", "/api/notifications/stream-ticket", null).body().get("ticket").asText();
        MvcResult first = mvc.perform(MockMvcRequestBuilders.get("/api/notifications/stream").param("ticket", ticket)).andReturn();
        assertThat(first.getResponse().getStatus()).isEqualTo(200);
        MvcResult reuse = mvc.perform(MockMvcRequestBuilders.get("/api/notifications/stream").param("ticket", ticket)).andReturn();
        assertThat(reuse.getResponse().getStatus()).as("tickets work once").isEqualTo(401);
    }

    @Test
    @DisplayName("SEC-19 Demo mode lists demo accounts; the options endpoint is public")
    void options() throws Exception {
        Response r = get(null, "/api/auth/options");
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.body().get("demoMode").asBoolean()).isTrue();
        assertThat(r.body().get("demoAccounts")).hasSize(5);
        assertThat(r.body().get("providers")).isEmpty();
    }
}
