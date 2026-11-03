package com.resolveiq.collab;

import com.fasterxml.jackson.databind.JsonNode;
import com.resolveiq.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AttachmentAndKnowledgeIntegrationTest extends IntegrationTestBase {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 'I', 'H', 'D', 'R'};

    private String myIncident(String user) throws Exception {
        return post(user, "/api/incidents", Map.of("title", "Monitor shows no signal", "description",
                "The external monitor shows no signal when I connect my laptop", "serviceCode", "DEVICES",
                "impact", "LOW", "urgency", "MEDIUM", "affectedUsers", 1)).body().at("/incident/number").asText();
    }

    private MvcResult upload(String user, String number, String name, byte[] data) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.multipart("/api/incidents/" + number + "/attachments")
                .file(new MockMultipartFile("file", name, "application/octet-stream", data))
                .header("Authorization", "Bearer " + token(user))).andReturn();
    }

    @Test
    @DisplayName("ATT-01 Files are checked by content: a real PNG is accepted; HTML renamed .png, SVG and the EICAR virus test are refused")
    void uploadValidation() throws Exception {
        String n = myIncident("ava");
        MvcResult ok = upload("ava", n, "../../screenshot.png", PNG);
        assertThat(ok.getResponse().getStatus()).isEqualTo(201);
        JsonNode a = body(ok);
        assertThat(a.get("fileName").asText()).as("path stripped").isEqualTo("screenshot.png");
        assertThat(a.get("contentType").asText()).isEqualTo("image/png");

        assertThat(upload("ava", n, "pic.png", "<html><script>alert(1)</script></html>".getBytes()).getResponse().getStatus())
                .isEqualTo(422);
        assertThat(upload("ava", n, "logo.svg", "<svg onload=alert(1)></svg>".getBytes()).getResponse().getStatus()).isEqualTo(422);
        byte[] eicar = "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*".getBytes(StandardCharsets.US_ASCII);
        MvcResult virus = upload("ava", n, "notes.txt", eicar);
        assertThat(virus.getResponse().getStatus()).isEqualTo(422);
        assertThat(virus.getResponse().getContentAsString()).contains("virus scanner");
        assertThat(upload("ava", n, "error.log", "2026-11-03 ERROR timeout\n".getBytes()).getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    @DisplayName("ATT-02 Downloads are private to people who can see the incident and are served with nosniff and a sandbox CSP")
    void downloadAccess() throws Exception {
        String n = myIncident("ava");
        long id = body(upload("ava", n, "shot.png", PNG)).get("id").asLong();
        MvcResult own = mvc.perform(MockMvcRequestBuilders.get("/api/attachments/" + id).header("Authorization", "Bearer " + token("ava"))).andReturn();
        assertThat(own.getResponse().getStatus()).isEqualTo(200);
        assertThat(own.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(own.getResponse().getHeader("Content-Security-Policy")).startsWith("sandbox");
        assertThat(own.getResponse().getContentAsByteArray()).isEqualTo(PNG);
        assertThat(get("priya", "/api/attachments/" + id).status()).as("another employee").isIn(403, 404);
        assertThat(get("neha", "/api/incidents/" + n + "/attachments").body()).hasSize(1);
        assertThat(get("ava", "/api/incidents/" + n).body().get("attachmentCount").asLong()).isEqualTo(1);
    }

    @Test
    @DisplayName("KB-01 Search finds articles by meaning; internal runbooks are hidden from employees")
    void search() throws Exception {
        JsonNode results = get("ava", "/api/kb?q=vpn keeps dropping at home").body();
        assertThat(results.get(0).get("title").asText()).containsIgnoringCase("VPN");
        assertThat(get("ava", "/api/kb").body().findValuesAsText("audience")).containsOnly("EVERYONE");
        assertThat(get("rahul", "/api/kb").body().findValuesAsText("audience")).contains("IT_STAFF");
        long runbook = 0;
        for (JsonNode r : get("rahul", "/api/kb?q=runbook").body()) {
            if (r.get("audience").asText().equals("IT_STAFF")) {
                runbook = r.get("id").asLong();
            }
        }
        assertThat(get("ava", "/api/kb/" + runbook).status()).isEqualTo(404);
    }

    @Test
    @DisplayName("KB-02 While reporting, matching self-help articles are suggested; 'this fixed it' counts a deflection")
    void suggestAndDeflect() throws Exception {
        JsonNode s = post("ava", "/api/kb/suggest", Map.of("text", "My account gets locked again after I changed my password",
                "serviceCode", "IDENTITY")).body();
        assertThat(s).isNotEmpty();
        assertThat(s.get(0).get("title").asText()).containsIgnoringCase("locked");
        long id = s.get(0).get("id").asLong();
        int before = s.get(0).get("deflections").asInt();
        assertThat(post("ava", "/api/kb/" + id + "/solved-my-problem", null).body().get("deflections").asInt()).isEqualTo(before + 1);
        assertThat(post("ava", "/api/kb/suggest", Map.of("text", "zzz qqq")).body()).isEmpty();
    }

    @Test
    @DisplayName("KB-03 Votes count once per person (changing a vote moves it)")
    void votes() throws Exception {
        long id = get("ava", "/api/kb?q=printer driver").body().get(0).get("id").asLong();
        int helpful = get("ava", "/api/kb/" + id).body().get("helpfulVotes").asInt();
        post("ava", "/api/kb/" + id + "/vote", Map.of("helpful", true));
        JsonNode again = post("ava", "/api/kb/" + id + "/vote", Map.of("helpful", true)).body();
        assertThat(again.get("helpfulVotes").asInt()).isEqualTo(helpful + 1);
        JsonNode changed = post("ava", "/api/kb/" + id + "/vote", Map.of("helpful", false)).body();
        assertThat(changed.get("helpfulVotes").asInt()).isEqualTo(helpful);
        assertThat(changed.get("myVote").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("KB-04 Technicians turn a resolved incident into an article; employees cannot write articles")
    void articleFromIncident() throws Exception {
        String resolved = get("kabir", "/api/incidents?status=CLOSED&size=1").body().at("/items/0/number").asText();
        JsonNode draft = get("kabir", "/api/kb/draft-from-incident/" + resolved).body();
        assertThat(draft.get("solution").asText()).isNotBlank();
        Map<String, Object> body = new java.util.HashMap<>(json.convertValue(draft, Map.class));
        body.put("sourceIncident", resolved);
        body.put("publish", true);
        Response created = post("kabir", "/api/kb", body);
        assertThat(created.status()).isEqualTo(201);
        assertThat(created.body().get("status").asText()).isEqualTo("PUBLISHED");
        assertThat(get("kabir", "/api/incidents/" + resolved).body().get("knowledgeArticleId").asLong())
                .isEqualTo(created.body().get("id").asLong());
        assertThat(post("ava", "/api/kb", body).status()).isEqualTo(403);
    }
}
