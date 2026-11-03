package com.resolveiq.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.resolveiq.security.oauth.AuthorizationRequest;
import com.resolveiq.security.oauth.ExternalIdentity;
import com.resolveiq.security.oauth.FederatedSignInException;
import com.resolveiq.security.oauth.IdentityProvider;
import com.resolveiq.support.IntegrationTestBase;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "Continue with Google" end to end, with a fake identity provider standing in for Google (Adapter
 * pattern). Each test registers what the provider will say for a given authorization code.
 */
@Import(GoogleSignInIntegrationTest.FakeGoogleConfig.class)
class GoogleSignInIntegrationTest extends IntegrationTestBase {

    static final Map<String, ExternalIdentity> IDENTITIES = new ConcurrentHashMap<>();

    @TestConfiguration
    static class FakeGoogleConfig {
        @Bean
        IdentityProvider fakeGoogle() {
            return new IdentityProvider() {
                public String id() {
                    return "google";
                }

                public String displayName() {
                    return "Google";
                }

                public boolean isEnabled() {
                    return true;
                }

                public URI authorizationUri(AuthorizationRequest r, List<String> hints) {
                    return URI.create("https://accounts.example/auth?state=" + r.state() + "&hd=" + String.join(",", hints)
                            + "&code_challenge=" + r.codeChallenge());
                }

                public ExternalIdentity exchange(String code, AuthorizationRequest r) {
                    ExternalIdentity id = IDENTITIES.get(code);
                    if (id == null) {
                        throw new FederatedSignInException("invalid_token", "unknown code");
                    }
                    return id;
                }
            };
        }
    }

    private record Started(String state, Cookie cookie, String location) {
    }

    private Started start() throws Exception {
        MvcResult r = mvc.perform(MockMvcRequestBuilders.get("/api/auth/oauth/google/start").param("returnTo", "/incidents")).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(302);
        String location = r.getResponse().getHeader("Location");
        String state = UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst("state");
        return new Started(state, r.getResponse().getCookie("riq_oauth"), location);
    }

    private MvcResult callback(Started s, String code, String state) throws Exception {
        var req = MockMvcRequestBuilders.get("/api/auth/oauth/google/callback").param("code", code).param("state", state);
        if (s.cookie() != null) {
            req.cookie(s.cookie());
        }
        return mvc.perform(req).andReturn();
    }

    private static ExternalIdentity google(String sub, String email, String name) {
        return new ExternalIdentity("google", sub, email, true, name, "https://lh3.googleusercontent.com/a/photo", "resolveiq.test");
    }

    @Test
    @DisplayName("SSO-01 Start redirects to the provider with state, PKCE and the company domain hint; state cookie is HttpOnly")
    void startRedirect() throws Exception {
        Started s = start();
        assertThat(s.location()).startsWith("https://accounts.example/auth").contains("hd=resolveiq.test").contains("code_challenge=");
        assertThat(s.cookie()).isNotNull();
        assertThat(s.cookie().isHttpOnly()).isTrue();
        assertThat(get(null, "/api/auth/options").body().get("providers").get(0).get("id").asText()).isEqualTo("google");
    }

    @Test
    @DisplayName("SSO-02 A pre-registered technician is linked on first Google sign-in and keeps the role the admin gave")
    void preProvisionedKeepsRole() throws Exception {
        IDENTITIES.put("code-rahul", google("g-rahul", "rahul@resolveiq.test", "Rahul Verma"));
        Started s = start();
        MvcResult r = callback(s, "code-rahul", s.state());
        assertThat(r.getResponse().getStatus()).isEqualTo(302);
        assertThat(r.getResponse().getHeader("Location")).isEqualTo("/incidents");
        String refresh = r.getResponse().getCookie("riq_refresh").getValue();
        JsonNode session = body(refresh(refresh));
        assertThat(session.at("/user/role").asText()).isEqualTo("TECHNICIAN");
        assertThat(session.at("/user/googleLinked").asBoolean()).isTrue();
        assertThat(session.at("/user/avatarUrl").asText()).startsWith("https://lh3.googleusercontent.com/");
    }

    @Test
    @DisplayName("SSO-03 A new person from the company domain becomes an Employee and is asked to complete a profile")
    void autoProvisionedEmployee() throws Exception {
        IDENTITIES.put("code-new", google("g-new", "meghna.rao@resolveiq.test", "Meghna Rao"));
        Started s = start();
        MvcResult r = callback(s, "code-new", s.state());
        assertThat(r.getResponse().getHeader("Location")).isEqualTo("/welcome");
        JsonNode session = body(refresh(r.getResponse().getCookie("riq_refresh").getValue()));
        assertThat(session.at("/user/role").asText()).as("nobody chooses their own role").isEqualTo("EMPLOYEE");
        assertThat(session.at("/user/profileCompleted").asBoolean()).isFalse();
        assertThat(session.at("/user/passwordEnabled").asBoolean()).isFalse();

        String token = session.get("token").asText();
        MvcResult done = mvc.perform(MockMvcRequestBuilders.post("/api/auth/profile/complete").header("Authorization", "Bearer " + token)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("fullName", "Meghna Rao", "phone", "+91 98765 43210",
                        "department", "Chemistry", "location", "Block B")))).andReturn();
        assertThat(body(done).get("profileCompleted").asBoolean()).isTrue();
        assertThat(body(done).get("department").asText()).isEqualTo("Chemistry");
        assertThat(signIn("meghna.rao@resolveiq.test", "").getResponse().getStatus()).as("no password sign-in").isIn(400, 401);
    }

    @Test
    @DisplayName("SSO-04 Personal accounts, unverified e-mails and forged state are refused")
    void rejections() throws Exception {
        IDENTITIES.put("code-gmail", new ExternalIdentity("google", "g-x", "someone@gmail.com", true, "X", null, null));
        Started s1 = start();
        assertThat(callback(s1, "code-gmail", s1.state()).getResponse().getHeader("Location"))
                .isEqualTo("/login?error=domain_not_allowed");

        IDENTITIES.put("code-unverified", new ExternalIdentity("google", "g-y", "y@resolveiq.test", false, "Y", null, "resolveiq.test"));
        Started s2 = start();
        assertThat(callback(s2, "code-unverified", s2.state()).getResponse().getHeader("Location"))
                .isEqualTo("/login?error=email_unverified");

        IDENTITIES.put("code-ava", google("g-ava", "ava@resolveiq.test", "Ava"));
        Started s3 = start();
        assertThat(callback(s3, "code-ava", "forged-state").getResponse().getHeader("Location"))
                .as("login CSRF").isEqualTo("/login?error=state_mismatch");
        assertThat(callback(new Started(s3.state(), null, null), "code-ava", s3.state()).getResponse().getHeader("Location"))
                .as("no state cookie").isEqualTo("/login?error=expired");
    }

    @Test
    @DisplayName("SSO-05 A second Google account cannot take over an already-linked account")
    void accountTakeoverBlocked() throws Exception {
        IDENTITIES.put("code-priya", google("g-priya", "priya@resolveiq.test", "Priya"));
        Started s1 = start();
        assertThat(callback(s1, "code-priya", s1.state()).getResponse().getHeader("Location")).isEqualTo("/incidents");
        IDENTITIES.put("code-priya-2", google("g-attacker", "priya@resolveiq.test", "Priya"));
        Started s2 = start();
        assertThat(callback(s2, "code-priya-2", s2.state()).getResponse().getHeader("Location"))
                .isEqualTo("/login?error=account_conflict");
    }

    @Test
    @DisplayName("SSO-06 With auto-provisioning off, only pre-registered people can sign in")
    void provisioningOff() throws Exception {
        put("admin", "/api/admin/settings", Map.of("AUTO_PROVISION_SSO", "false"));
        IDENTITIES.put("code-stranger", google("g-stranger", "stranger@resolveiq.test", "Stranger"));
        Started s = start();
        assertThat(callback(s, "code-stranger", s.state()).getResponse().getHeader("Location"))
                .isEqualTo("/login?error=not_provisioned");
    }

    @Test
    @DisplayName("SSO-07 Open-redirect protection: only same-site paths are allowed after sign-in")
    void safeReturn() {
        assertThat(com.resolveiq.web.OAuthControllerAccess.safeReturn("https://evil.example")).isEqualTo("/");
        assertThat(com.resolveiq.web.OAuthControllerAccess.safeReturn("//evil.example")).isEqualTo("/");
        assertThat(com.resolveiq.web.OAuthControllerAccess.safeReturn("/incidents/INC-1001")).isEqualTo("/incidents/INC-1001");
    }
}
