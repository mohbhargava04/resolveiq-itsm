package com.resolveiq.collab;

import com.fasterxml.jackson.databind.JsonNode;
import com.resolveiq.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MajorIncidentAndEmailIntegrationTest extends IntegrationTestBase {

    private List<String> clusterIncidents() throws Exception {
        JsonNode cluster = get("neha", "/api/dashboard").body().get("correlations").get(0);
        List<String> numbers = new ArrayList<>();
        cluster.get("incidentNumbers").forEach(n -> numbers.add(n.asText()));
        return numbers;
    }

    private List<String> subjects(String user) throws Exception {
        List<String> s = new ArrayList<>();
        get(user, "/api/notifications?size=60").body().get("items").forEach(n -> s.add(n.get("subject").asText()));
        return s;
    }

    @Test
    @DisplayName("MAJ-01 A team lead declares a major incident from a correlated outage and links the related incidents in one step")
    void declareAndLink() throws Exception {
        List<String> cluster = clusterIncidents();
        String majorNumber = cluster.getFirst();
        assertThat(post("rahul", "/api/incidents/" + majorNumber + "/major", Map.of()).status())
                .as("technicians cannot declare").isEqualTo(403);

        Response declared = post("neha", "/api/incidents/" + majorNumber + "/major",
                Map.of("summary", "Several services are failing because of the core database.", "link", cluster));
        assertThat(declared.status()).isEqualTo(200);
        JsonNode major = declared.body().get("major");
        assertThat(major.get("stage").asText()).isEqualTo("INVESTIGATING");
        assertThat(major.get("linked").size()).isEqualTo(cluster.size() - 1);

        JsonNode banner = get("ava", "/api/major-incidents").body();
        assertThat(banner.findValuesAsText("number")).contains(majorNumber);
        JsonNode dash = get("neha", "/api/dashboard").body();
        assertThat(dash.get("correlations").get(0).get("majorNumber").asText()).isEqualTo(majorNumber);

        String child = cluster.get(1);
        String childReporter = userKey(get("neha", "/api/incidents/" + child).body().get("reporter"));
        assertThat(subjects(childReporter)).anyMatch(s -> s.contains("part of a wider problem"));
        assertThat(get(childReporter, "/api/incidents/" + child).body().at("/majorParent/number").asText()).isEqualTo(majorNumber);
    }

    @Test
    @DisplayName("MAJ-02 Status updates reach everyone affected; an employee can join with 'I'm affected too'; resolving the major resolves them all")
    void updatesAndCascade() throws Exception {
        List<String> cluster = clusterIncidents();
        String majorNumber = cluster.getFirst();
        post("neha", "/api/incidents/" + majorNumber + "/major", Map.of("link", cluster));

        Response joined = post("ava", "/api/major-incidents/" + majorNumber + "/affected", Map.of("details", "Cannot pay my fees"));
        assertThat(joined.status()).isEqualTo(201);
        String avaIncident = joined.body().get("number").asText();
        assertThat(joined.body().at("/majorParent/number").asText()).isEqualTo(majorNumber);

        assertThat(post("neha", "/api/incidents/" + majorNumber + "/major/updates",
                Map.of("stage", "IDENTIFIED", "message", "A runaway query is overloading the database.")).status()).isEqualTo(200);
        assertThat(subjects("ava")).anyMatch(s -> s.startsWith("Cause identified"));

        // work the major incident to resolution
        JsonNode m = get("neha", "/api/incidents/" + majorNumber).body();
        String owner = m.get("assignee") == null || m.get("assignee").isNull() ? "neha" : userKey(m.get("assignee"));
        if (m.get("status").asText().equals("CLASSIFIED")) {
            post("neha", "/api/incidents/" + majorNumber + "/assign", Map.of("technicianId", get("neha", "/api/technicians").body().get(0).get("id").asLong()));
            owner = userKey(get("neha", "/api/incidents/" + majorNumber).body().get("assignee"));
        }
        if (get("neha", "/api/incidents/" + majorNumber).body().get("status").asText().equals("ASSIGNED")) {
            post(owner, "/api/incidents/" + majorNumber + "/acknowledge", null);
        }
        if (get("neha", "/api/incidents/" + majorNumber).body().get("status").asText().equals("ACKNOWLEDGED")) {
            post(owner, "/api/incidents/" + majorNumber + "/start", null);
        }
        Response resolved = post("neha", "/api/incidents/" + majorNumber + "/resolve",
                Map.of("summary", "Cancelled the runaway query and added an index", "rootCause", "Unindexed report query"));
        assertThat(resolved.status()).isEqualTo(200);
        assertThat(resolved.body().at("/major/stage").asText()).isEqualTo("RESOLVED");
        assertThat(get("ava", "/api/incidents/" + avaIncident).body().get("status").asText()).isEqualTo("RESOLVED");
        for (String n : cluster.subList(1, cluster.size())) {
            assertThat(get("neha", "/api/incidents/" + n).body().get("status").asText()).isIn("RESOLVED", "CLOSED");
        }
        assertThat(get("ava", "/api/major-incidents").body().findValuesAsText("number")).doesNotContain(majorNumber);
    }

    @Test
    @DisplayName("BULK-01 Team leads apply one action to many incidents; each succeeds or fails on its own")
    void bulkActions() throws Exception {
        JsonNode unassigned = get("neha", "/api/dashboard").body().get("unassigned");
        List<String> numbers = new ArrayList<>();
        unassigned.forEach(u -> numbers.add(u.get("number").asText()));
        String closed = get("neha", "/api/incidents?status=CLOSED&size=1").body().at("/items/0/number").asText();
        numbers.add(closed);
        Map<String, Object> body = new HashMap<>();
        body.put("numbers", numbers);
        body.put("action", "ASSIGN");
        body.put("params", Map.of("strategy", "LOAD"));
        Response r = post("neha", "/api/incidents/bulk", body);
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.body().get(r.body().size() - 1).get("ok").asBoolean()).as("closed incident cannot be assigned").isFalse();
        assertThat(r.body().get(0).get("ok").asBoolean()).isTrue();
        assertThat(post("rahul", "/api/incidents/bulk", body).status()).isEqualTo(403);
    }

    private MvcResult inbound(String secret, Map<String, Object> mail) throws Exception {
        var req = MockMvcRequestBuilders.post("/api/inbound/email").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(mail));
        if (secret != null) {
            req.header("X-Inbound-Secret", secret);
        }
        return mvc.perform(req).andReturn();
    }

    @Test
    @DisplayName("MAIL-01 Replying to a notification answers an open question and restarts the SLA; forged and outside senders are refused")
    void replyByEmail() throws Exception {
        String n = post("ava", "/api/incidents", Map.of("title", "Docking station not charging", "description",
                "My docking station stopped charging the laptop", "serviceCode", "DEVICES", "impact", "LOW",
                "urgency", "MEDIUM", "affectedUsers", 1)).body().at("/incident/number").asText();
        String tech = userKey(get("ava", "/api/incidents/" + n).body().get("assignee"));
        post(tech, "/api/incidents/" + n + "/acknowledge", null);
        post(tech, "/api/incidents/" + n + "/start", null);
        post(tech, "/api/incidents/" + n + "/request-info", Map.of("text", "Which dock model is it?"));

        assertThat(inbound(null, Map.of("from", "ava@resolveiq.test", "subject", "x", "text", "y")).getResponse().getStatus())
                .as("secret required").isEqualTo(403);

        MvcResult reply = inbound("test-inbound-secret", Map.of("from", "Ava Sharma <ava@resolveiq.test>",
                "subject", "RE: Action needed on " + n + " [" + n + "]",
                "text", "It is the Dell WD19.\n\nOn Tue, 3 Nov 2026 ResolveIQ wrote:\n> Which dock model is it?"));
        JsonNode result = body(reply);
        assertThat(result.get("outcome").asText()).isEqualTo("ANSWERED_QUESTION");
        JsonNode incident = get("ava", "/api/incidents/" + n).body();
        assertThat(incident.get("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(incident.at("/sla/paused").asBoolean()).isFalse();
        assertThat(incident.get("notes").findValuesAsText("content")).anyMatch(c -> c.equals("It is the Dell WD19."));

        JsonNode forged = body(inbound("test-inbound-secret", Map.of("from", "priya@resolveiq.test",
                "subject", "Re: [" + n + "]", "text", "Close this please")));
        assertThat(forged.get("outcome").asText()).as("not her incident").isEqualTo("REJECTED");
        JsonNode outsider = body(inbound("test-inbound-secret", Map.of("from", "spam@evil.example", "subject", "hi", "text", "buy now")));
        assertThat(outsider.get("outcome").asText()).isEqualTo("REJECTED");
    }

    @Test
    @DisplayName("MAIL-02 An e-mail without a reference opens a new incident, with the service guessed from the text")
    void newIncidentByEmail() throws Exception {
        JsonNode r = body(inbound("test-inbound-secret", Map.of("from", "rohan@resolveiq.test",
                "subject", "Campus Wi-Fi not working in hostel block D", "text", "No wireless connection since 9 pm.")));
        assertThat(r.get("outcome").asText()).isEqualTo("INCIDENT_CREATED");
        JsonNode i = get("rohan", "/api/incidents/" + r.get("incidentNumber").asText()).body();
        assertThat(i.at("/service/code").asText()).isEqualTo("CAMPUS_WIFI");
        assertThat(get("admin", "/api/admin/inbound").body().get("emails").get(0).get("outcome").asText()).isEqualTo("INCIDENT_CREATED");
    }
}
