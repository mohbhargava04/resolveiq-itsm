package com.resolveiq.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.resolveiq.notification.ChannelType;
import com.resolveiq.notification.Notification;
import com.resolveiq.notification.NotificationRepository;
import com.resolveiq.security.mfa.Totp;
import com.resolveiq.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Two-factor sign-in with an authenticator app, recovery codes, and "forgot password" e-mail links. */
class MfaAndPasswordResetIntegrationTest extends IntegrationTestBase {

    @Autowired
    private NotificationRepository notifications;

    private String code(String secret) {
        return Totp.codeAt(secret, Totp.stepAt(clock.instant()));
    }

    /** The link from the newest reset e-mail (demo mode keeps it in the delivery log). */
    private String latestResetToken(String email) {
        Notification mail = notifications.findRecentByChannel(ChannelType.EMAIL, PageRequest.of(0, 200)).stream()
                .filter(n -> n.getRecipient().getEmail().equals(email) && n.getSubject().contains("Reset"))
                .max(java.util.Comparator.comparing(Notification::getId)).orElseThrow();
        Matcher m = Pattern.compile("token=([A-Za-z0-9_-]+)").matcher(mail.getMessage());
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    private Response verify(String challenge, String code) throws Exception {
        return post(null, "/api/auth/mfa/verify", Map.of("challenge", challenge, "code", code));
    }

    @Test
    @DisplayName("SEC-10 Turning on 2FA needs a correct code; sign-in then asks for a code, refuses wrong and replayed codes")
    void totpSignIn() throws Exception {
        JsonNode setup = post("admin", "/api/auth/mfa/setup", null).body();
        String secret = setup.get("secret").asText();
        assertThat(setup.get("otpauthUri").asText()).startsWith("otpauth://totp/ResolveIQ:admin%40resolveiq.test?secret=" + secret);

        assertThat(post("admin", "/api/auth/mfa/enable", Map.of("code", "000000")).code()).isEqualTo("INVALID_MFA_CODE");
        JsonNode enabled = post("admin", "/api/auth/mfa/enable", Map.of("code", code(secret))).body();
        assertThat(enabled.get("recoveryCodes")).hasSize(10);

        JsonNode first = body(signIn("admin@resolveiq.test", PASSWORD));
        assertThat(first.get("mfaRequired").asBoolean()).isTrue();
        assertThat(first.get("token")).as("no session before the second factor").isNull();
        String challenge = first.get("challenge").asText();

        assertThat(verify(challenge, "123456").status()).isEqualTo(401);
        String used = code(secret);
        assertThat(verify(challenge, used).code()).as("code used during set-up cannot be replayed").isEqualTo("INVALID_MFA_CODE");

        clock.advance(Duration.ofSeconds(31));
        Response ok = verify(challenge, code(secret));
        assertThat(ok.status()).isEqualTo(200);
        assertThat(ok.body().at("/user/mfaEnabled").asBoolean()).isTrue();
        assertThat(verify(challenge, code(secret)).status()).as("same code twice").isEqualTo(401);
    }

    @Test
    @DisplayName("SEC-11 A recovery code signs in once; wrong 2FA codes count towards the lockout")
    void recoveryCodesAndLockout() throws Exception {
        String secret = post("manager", "/api/auth/mfa/setup", null).body().get("secret").asText();
        String recovery = post("manager", "/api/auth/mfa/enable", Map.of("code", code(secret))).body().get("recoveryCodes").get(0).asText();

        String challenge = body(signIn("manager@resolveiq.test", PASSWORD)).get("challenge").asText();
        assertThat(verify(challenge, recovery.toLowerCase()).status()).isEqualTo(200);
        String challenge2 = body(signIn("manager@resolveiq.test", PASSWORD)).get("challenge").asText();
        assertThat(verify(challenge2, recovery).status()).as("recovery codes work once").isEqualTo(401);

        for (int i = 0; i < 3; i++) {
            verify(challenge2, "999999");
        }
        assertThat(verify(challenge2, "999999").status()).as("fifth wrong attempt locks the account").isEqualTo(423);
    }

    @Test
    @DisplayName("SEC-12 Forgot password: same answer for unknown addresses; the link works once, for 30 minutes")
    void passwordReset() throws Exception {
        Response unknown = post(null, "/api/auth/password/forgot", Map.of("email", "nobody@resolveiq.test"));
        Response known = post(null, "/api/auth/password/forgot", Map.of("email", "dev@resolveiq.test"));
        assertThat(unknown.status()).isEqualTo(202);
        assertThat(known.status()).isEqualTo(202);
        assertThat(known.body()).as("no hint whether the account exists").isEqualTo(unknown.body());

        String token = latestResetToken("dev@resolveiq.test");

        String oldSession = body(signIn("dev@resolveiq.test", PASSWORD)).get("token").asText();
        assertThat(post(null, "/api/auth/password/reset", Map.of("token", token, "password", "password123")).status())
                .as("breached password refused").isEqualTo(422);
        assertThat(post(null, "/api/auth/password/reset", Map.of("token", token, "password", "Copper-Kite-93")).status()).isEqualTo(200);
        assertThat(getWithToken(oldSession, "/api/auth/me").status()).as("all sessions signed out").isEqualTo(401);
        assertThat(post(null, "/api/auth/password/reset", Map.of("token", token, "password", "Copper-Kite-94")).code())
                .as("single use").isEqualTo("INVALID_TOKEN");
        assertThat(signIn("dev@resolveiq.test", "Copper-Kite-93").getResponse().getStatus()).isEqualTo(200);

        post(null, "/api/auth/password/forgot", Map.of("email", "dev@resolveiq.test"));
        String second = latestResetToken("dev@resolveiq.test");
        assertThat(second).isNotEqualTo(token);
        clock.advance(Duration.ofMinutes(31));
        assertThat(post(null, "/api/auth/password/reset", Map.of("token", second, "password", "Copper-Kite-95")).code())
                .as("expired").isEqualTo("INVALID_TOKEN");
    }

    @Test
    @DisplayName("SEC-13 Self-registration only accepts company e-mail domains, and can be switched off")
    void registrationRules() throws Exception {
        Response gmail = post(null, "/api/auth/register", Map.of("fullName", "Outsider", "email", "someone@gmail.com",
                "password", "Granite-Owl-27"));
        assertThat(gmail.status()).isEqualTo(403);
        assertThat(gmail.code()).isEqualTo("EMAIL_DOMAIN_NOT_ALLOWED");

        put("admin", "/api/admin/settings", Map.of("SELF_REGISTRATION", "false"));
        Response off = post(null, "/api/auth/register", Map.of("fullName", "Insider", "email", "insider@resolveiq.test",
                "password", "Granite-Owl-27"));
        assertThat(off.code()).isEqualTo("REGISTRATION_DISABLED");
        assertThat(get(null, "/api/auth/options").body().get("selfRegistration").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("SEC-14 A company-account-only user (no password) cannot sign in with any password")
    void companyAccountOnly() throws Exception {
        Response created = post("admin", "/api/admin/users", Map.of("role", "EMPLOYEE", "fullName", "Google Only",
                "email", "google.only@resolveiq.test"));
        assertThat(created.status()).isEqualTo(201);
        assertThat(created.body().get("passwordEnabled").asBoolean()).isFalse();
        MvcResult r = signIn("google.only@resolveiq.test", "");
        assertThat(r.getResponse().getStatus()).isIn(400, 401);
        assertThat(signIn("google.only@resolveiq.test", "Anything-123").getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("SEC-15 Admin can see the security overview and reset someone's 2FA")
    void adminSecurityTools() throws Exception {
        String secret = post("neha", "/api/auth/mfa/setup", null).body().get("secret").asText();
        post("neha", "/api/auth/mfa/enable", Map.of("code", code(secret)));
        assertThat(body(signIn("neha@resolveiq.test", PASSWORD)).get("mfaRequired").asBoolean()).isTrue();

        JsonNode overview = get("admin", "/api/admin/security").body();
        assertThat(overview.has("activeSessions")).isTrue();
        assertThat(overview.get("allowedDomains").get(0).asText()).isEqualTo("resolveiq.test");

        long id = 0;
        for (JsonNode u : get("admin", "/api/admin/users").body()) {
            if (u.get("email").asText().equals("neha@resolveiq.test")) {
                id = u.get("id").asLong();
            }
        }
        assertThat(post("admin", "/api/admin/users/" + id + "/reset-mfa", null).status()).isEqualTo(200);
        assertThat(body(signIn("neha@resolveiq.test", PASSWORD)).get("token")).isNotNull();
        assertThat(post("neha", "/api/admin/users/" + id + "/reset-mfa", null).status()).as("team lead is not an admin").isEqualTo(403);
    }
}
