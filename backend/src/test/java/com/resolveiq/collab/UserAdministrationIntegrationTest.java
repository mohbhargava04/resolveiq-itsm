package com.resolveiq.collab;

import com.fasterxml.jackson.databind.JsonNode;
import com.resolveiq.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class UserAdministrationIntegrationTest extends IntegrationTestBase {

    private long id(String email) throws Exception {
        for (JsonNode u : get("admin", "/api/admin/users").body()) {
            if (u.get("email").asText().equals(email)) {
                return u.get("id").asLong();
            }
        }
        throw new AssertionError(email);
    }

    @Test
    @DisplayName("ADM-01 Import pre-registers people with their role; bad rows are reported, not fatal")
    void csvImport() throws Exception {
        String csv = """
                email,name,role,department,skills
                tara.nair@resolveiq.test,Tara Nair,TECHNICIAN,IT,network;vpn
                omar.k@resolveiq.test,Omar K,EMPLOYEE,Physics,
                ava@resolveiq.test,Ava Again,EMPLOYEE,,
                x@gmail.com,Outsider,EMPLOYEE,,
                bad@resolveiq.test,Bad Role,WIZARD,,
                """;
        JsonNode rows = post("admin", "/api/admin/users/import", Map.of("csv", csv)).body();
        assertThat(rows.findValuesAsText("outcome")).containsExactly("CREATED", "CREATED", "SKIPPED", "ERROR", "ERROR");
        JsonNode tara = get("admin", "/api/admin/users").body().findParent("email");
        assertThat(get("admin", "/api/admin/users").body().toString()).contains("tara.nair@resolveiq.test");
        assertThat(tara).isNotNull();
    }

    @Test
    @DisplayName("ADM-02 A technician who has worked incidents cannot be demoted; nobody can change their own role")
    void roleRules() throws Exception {
        Response demote = post("admin", "/api/admin/users/" + id("kabir@resolveiq.test") + "/role", Map.of("role", "EMPLOYEE"));
        assertThat(demote.status()).isEqualTo(422);
        assertThat(demote.body().get("detail").asText()).contains("Deactivate the account instead");
        assertThat(post("admin", "/api/admin/users/" + id("admin@resolveiq.test") + "/role", Map.of("role", "MANAGER")).status())
                .isIn(403, 422);
        assertThat(post("admin", "/api/admin/users/" + id("neha@resolveiq.test") + "/role", Map.of("role", "MANAGER")).status())
                .as("only a manager grants manager").isEqualTo(403);
        Response promote = post("admin", "/api/admin/users/" + id("kabir@resolveiq.test") + "/role", Map.of("role", "TEAM_LEAD"));
        assertThat(promote.body().get("role").asText()).isEqualTo("TEAM_LEAD");
    }

    @Test
    @DisplayName("ADM-03 Admins can unlock a locked account and sign someone out everywhere")
    void unlockAndSignOut() throws Exception {
        for (int i = 0; i < 5; i++) {
            signIn("arjun@resolveiq.test", "wrong-pass-1");
        }
        assertThat(signIn("arjun@resolveiq.test", PASSWORD).getResponse().getStatus()).isEqualTo(423);
        long arjun = id("arjun@resolveiq.test");
        assertThat(post("admin", "/api/admin/users/" + arjun + "/unlock", null).status()).isEqualTo(200);
        String token = body(signIn("arjun@resolveiq.test", PASSWORD)).get("token").asText();
        assertThat(post("admin", "/api/admin/users/" + arjun + "/revoke-sessions", null).status()).isEqualTo(200);
        assertThat(getWithToken(token, "/api/auth/me").status()).isEqualTo(401);
    }

    @Test
    @DisplayName("ADM-04 Allowed domains are validated and tidied when an admin changes them")
    void domainSetting() throws Exception {
        Response r = put("admin", "/api/admin/settings", Map.of("ALLOWED_EMAIL_DOMAINS", " ResolveIQ.test, @hyderabad.bits-pilani.ac.in "));
        assertThat(r.body().get("ALLOWED_EMAIL_DOMAINS").asText()).isEqualTo("resolveiq.test,hyderabad.bits-pilani.ac.in");
        assertThat(put("admin", "/api/admin/settings", Map.of("ALLOWED_EMAIL_DOMAINS", "not a domain")).status()).isEqualTo(422);
    }
}
