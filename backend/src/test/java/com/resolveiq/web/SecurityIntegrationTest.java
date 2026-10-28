package com.resolveiq.web;

import com.resolveiq.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Authentication, role-based authorisation and input validation through the real HTTP layer. */
class SecurityIntegrationTest extends IntegrationTestBase {

    @Test
    @DisplayName("TC-33 Valid login returns a signed JWT and the user's role")
    void login() throws Exception {
        Response r = post(null, "/api/auth/login", Map.of("email", "rahul@resolveiq.test", "password", PASSWORD));
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.body().get("token").asText()).contains(".");
        assertThat(r.body().at("/user/role").asText()).isEqualTo("TECHNICIAN");
        assertThat(r.body().at("/user/technician/skills").size()).isGreaterThan(0);
    }

    @Test
    @DisplayName("TC-34 Invalid login: wrong password and unknown e-mail give the same 401")
    void invalidLogin() throws Exception {
        Response wrong = post(null, "/api/auth/login", Map.of("email", "ava@resolveiq.test", "password", "nope12345"));
        Response unknown = post(null, "/api/auth/login", Map.of("email", "ghost@resolveiq.test", "password", "nope12345"));
        assertThat(wrong.status()).isEqualTo(401);
        assertThat(wrong.code()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(unknown.body().get("detail")).isEqualTo(wrong.body().get("detail"));
    }

    @Test
    @DisplayName("TC-35 Five wrong passwords lock the account (brute-force protection)")
    void lockout() throws Exception {
        for (int i = 0; i < 4; i++) {
            assertThat(post(null, "/api/auth/login", Map.of("email", "sneha@resolveiq.test", "password", "bad-pass-1"))
                    .status()).isEqualTo(401);
        }
        Response locked = post(null, "/api/auth/login", Map.of("email", "sneha@resolveiq.test", "password", "bad-pass-1"));
        assertThat(locked.status()).isEqualTo(423);
        assertThat(locked.code()).isEqualTo("ACCOUNT_LOCKED");
        // even the right password is refused while locked
        assertThat(post(null, "/api/auth/login", Map.of("email", "sneha@resolveiq.test", "password", PASSWORD))
                .code()).isEqualTo("ACCOUNT_LOCKED");
    }

    @Test
    @DisplayName("TC-36 Requests without a token are rejected with 401")
    void unauthenticated() throws Exception {
        Response r = get(null, "/api/incidents");
        assertThat(r.status()).isEqualTo(401);
        assertThat(r.code()).isEqualTo("UNAUTHENTICATED");
    }

    @Test
    @DisplayName("TC-37 Registration creates an employee; duplicate e-mail and invalid input are rejected")
    void register() throws Exception {
        Response ok = post(null, "/api/auth/register", Map.of("fullName", "New Person", "email", "new.person@resolveiq.test",
                "password", "Welcome123", "department", "Physics"));
        assertThat(ok.status()).isEqualTo(201);
        assertThat(ok.body().at("/user/role").asText()).isEqualTo("EMPLOYEE");

        Response dup = post(null, "/api/auth/register", Map.of("fullName", "Ava Again", "email", "ava@resolveiq.test",
                "password", "Welcome123"));
        assertThat(dup.status()).isEqualTo(409);

        Response bad = post(null, "/api/auth/register", Map.of("fullName", "", "email", "not-an-email", "password", "x"));
        assertThat(bad.status()).isEqualTo(400);
        assertThat(bad.code()).isEqualTo("VALIDATION_FAILED");
        assertThat(bad.body().get("fieldErrors").has("email")).isTrue();
        assertThat(bad.body().get("fieldErrors").has("password")).isTrue();
    }

    @Test
    @DisplayName("TC-38 Role-based authorisation: each role is confined to its own functions")
    void roles() throws Exception {
        assertThat(get("rahul", "/api/admin/users").status()).isEqualTo(403);     // technician → admin area
        assertThat(get("rahul", "/api/reports/overview").status()).isEqualTo(403); // technician → management reports
        assertThat(get("ava", "/api/technicians").status()).isEqualTo(403);       // employee → technician roster
        assertThat(post("ava", "/api/incidents/INC-1039/assign", Map.of()).status()).isEqualTo(403);
        assertThat(get("neha", "/api/reports/overview").status()).isEqualTo(200); // team lead → reports
        assertThat(get("admin", "/api/admin/users").status()).isEqualTo(200);
        assertThat(get("manager", "/api/audit").status()).isEqualTo(200);         // manager inherits admin rights
    }

    @Test
    @DisplayName("Employees only ever see their own incidents (row-level security)")
    void rowLevel() throws Exception {
        Response list = get("ava", "/api/incidents?size=100");
        list.body().get("items").forEach(i -> assertThat(i.at("/reporter/name").asText()).isEqualTo("Ava Sharma"));
        // INC-1037 was reported by Arjun: Ava gets "not found", not "forbidden"
        assertThat(get("ava", "/api/incidents/INC-1037").status()).isEqualTo(404);
        assertThat(get("rahul", "/api/incidents/INC-1037").status()).isEqualTo(200);
    }
}
