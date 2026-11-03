package com.resolveiq.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.resolveiq.audit.AuditAction;
import com.resolveiq.audit.AuditRepository;
import com.resolveiq.support.IntegrationTestBase;
import com.resolveiq.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Sessions: short access tokens, rotating refresh cookies, reuse detection and immediate revocation. */
class SessionSecurityIntegrationTest extends IntegrationTestBase {

    @Autowired
    private AuditRepository audit;
    @Autowired
    private UserRepository users;

    @Test
    @DisplayName("SEC-01 Sign-in sets an HttpOnly, SameSite=Strict refresh cookie scoped to /api/auth; access token lives 15 minutes")
    void refreshCookieAttributes() throws Exception {
        MvcResult r = signIn("ava@resolveiq.test", PASSWORD);
        String setCookie = r.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).contains("riq_refresh=").contains("HttpOnly").contains("SameSite=Strict").contains("Path=/api/auth");
        JsonNode b = body(r);
        Instant exp = Instant.parse(b.get("expiresAt").asText());
        assertThat(Duration.between(clock.instant(), exp)).isLessThanOrEqualTo(Duration.ofMinutes(15));
        assertThat(b.get("token").asText()).isNotBlank();
    }

    @Test
    @DisplayName("SEC-02 Refresh rotates the token; replaying a used token signs the whole session out")
    void rotationAndReuseDetection() throws Exception {
        String first = refreshCookie(signIn("priya@resolveiq.test", PASSWORD));
        MvcResult r1 = refresh(first);
        assertThat(r1.getResponse().getStatus()).isEqualTo(200);
        String second = refreshCookie(r1);
        assertThat(second).isNotEqualTo(first);
        String accessAfterRotation = body(r1).get("token").asText();

        // within the grace period a duplicate refresh (two tabs) is tolerated
        assertThat(refresh(first).getResponse().getStatus()).isEqualTo(200);

        // later, the old token is a stolen copy: the session is revoked
        clock.advance(Duration.ofSeconds(60));
        MvcResult replay = refresh(first);
        assertThat(replay.getResponse().getStatus()).isEqualTo(401);
        assertThat(body(replay).get("code").asText()).isEqualTo("SESSION_EXPIRED");
        assertThat(refresh(second).getResponse().getStatus()).as("legitimate token revoked too").isEqualTo(401);
        assertThat(getWithToken(accessAfterRotation, "/api/auth/me").status()).as("access token dies with its session").isEqualTo(401);
        assertThat(audit.countByActionAndOccurredAtAfter(AuditAction.SESSION_REUSE_DETECTED, Instant.EPOCH)).isPositive();
    }

    @Test
    @DisplayName("SEC-03 Refresh and sign-out need the X-Requested-With header (CSRF defence in depth)")
    void csrfHeaderRequired() throws Exception {
        String rt = refreshCookie(signIn("ava@resolveiq.test", PASSWORD));
        MvcResult noHeader = mvc.perform(MockMvcRequestBuilders.post("/api/auth/refresh").cookie(new jakarta.servlet.http.Cookie("riq_refresh", rt))).andReturn();
        assertThat(noHeader.getResponse().getStatus()).isEqualTo(422);
        assertThat(refresh(rt).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("SEC-04 Signing out revokes the session: the access token stops working at once")
    void logoutRevokesImmediately() throws Exception {
        MvcResult login = signIn("rohan@resolveiq.test", PASSWORD);
        String access = body(login).get("token").asText();
        assertThat(getWithToken(access, "/api/auth/me").status()).isEqualTo(200);
        MvcResult out = mvc.perform(MockMvcRequestBuilders.post("/api/auth/logout").header("X-Requested-With", "ResolveIQ")
                .cookie(new jakarta.servlet.http.Cookie("riq_refresh", refreshCookie(login)))).andReturn();
        assertThat(out.getResponse().getStatus()).isEqualTo(204);
        assertThat(out.getResponse().getHeader("Set-Cookie")).contains("Max-Age=0");
        assertThat(getWithToken(access, "/api/auth/me").status()).isEqualTo(401);
    }

    @Test
    @DisplayName("SEC-05 Deactivating an account invalidates its existing tokens on the next request")
    void deactivationRevokes() throws Exception {
        String access = body(signIn("arjun@resolveiq.test", PASSWORD)).get("token").asText();
        Long id = users.findByEmailIgnoreCase("arjun@resolveiq.test").orElseThrow().getId();
        assertThat(put("admin", "/api/admin/users/" + id, Map.of("active", false)).status()).isEqualTo(200);
        assertThat(getWithToken(access, "/api/incidents").status()).isEqualTo(401);
    }

    @Test
    @DisplayName("SEC-06 Changing a role takes effect immediately; the role in an old token is never trusted")
    void roleChangeIsImmediate() throws Exception {
        String access = body(signIn("sneha@resolveiq.test", PASSWORD)).get("token").asText();
        Long id = users.findByEmailIgnoreCase("sneha@resolveiq.test").orElseThrow().getId();
        Response promoted = post("admin", "/api/admin/users/" + id + "/role",
                Map.of("role", "TECHNICIAN", "skills", java.util.List.of("NETWORK")));
        assertThat(promoted.status()).isEqualTo(200);
        assertThat(promoted.body().get("role").asText()).isEqualTo("TECHNICIAN");
        assertThat(getWithToken(access, "/api/auth/me").status()).as("old session ended").isEqualTo(401);
        JsonNode again = body(signIn("sneha@resolveiq.test", PASSWORD));
        assertThat(again.at("/user/role").asText()).isEqualTo("TECHNICIAN");
        assertThat(getWithToken(again.get("token").asText(), "/api/technicians").status()).isEqualTo(200);
    }

    @Test
    @DisplayName("SEC-07 Only an access token authenticates API calls (a 2FA challenge or forged token does not)")
    void onlyAccessTokens() throws Exception {
        assertThat(getWithToken("eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ4In0.bad", "/api/incidents").status()).isEqualTo(401);
        JwtService jwt = jwtService;
        String challenge = jwt.issueChallenge(JwtService.TYPE_MFA, "ava@resolveiq.test",
                Map.of(JwtService.CLAIM_USER_ID, 1L), Duration.ofMinutes(5)).token();
        assertThat(getWithToken(challenge, "/api/incidents").status()).isEqualTo(401);
    }

    @Test
    @DisplayName("SEC-08 Users see where they are signed in and can sign other devices out")
    void sessionsList() throws Exception {
        signIn("kabir@resolveiq.test", PASSWORD); // device 1
        MvcResult current = signIn("kabir@resolveiq.test", PASSWORD); // device 2
        String token = body(current).get("token").asText();
        Response list = getWithToken(token, "/api/auth/sessions");
        assertThat(list.body().size()).isGreaterThanOrEqualTo(2);
        assertThat(list.body().get(0).get("current").asBoolean()).isTrue();
        MvcResult revoke = mvc.perform(MockMvcRequestBuilders.post("/api/auth/sessions/revoke-others").header("Authorization", "Bearer " + token)).andReturn();
        assertThat(revoke.getResponse().getStatus()).isEqualTo(200);
        assertThat(getWithToken(token, "/api/auth/sessions").body().size()).isEqualTo(1);
    }

    @Test
    @DisplayName("SEC-09 Changing the password signs out other devices and returns a fresh session")
    void passwordChangeRevokesOthers() throws Exception {
        String other = body(signIn("isha@resolveiq.test", PASSWORD)).get("token").asText();
        MvcResult mine = signIn("isha@resolveiq.test", PASSWORD);
        MvcResult changed = mvc.perform(MockMvcRequestBuilders.post("/api/auth/change-password")
                .header("Authorization", "Bearer " + body(mine).get("token").asText())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("currentPassword", PASSWORD, "newPassword", "Harbour-Lamp-58")))).andReturn();
        assertThat(changed.getResponse().getStatus()).isEqualTo(200);
        assertThat(getWithToken(other, "/api/auth/me").status()).isEqualTo(401);
        assertThat(getWithToken(body(changed).get("token").asText(), "/api/auth/me").status()).isEqualTo(200);
        assertThat(signIn("isha@resolveiq.test", "Harbour-Lamp-58").getResponse().getStatus()).isEqualTo(200);
    }

    @Autowired
    private JwtService jwtService;
}
