package com.resolveiq.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.resolveiq.sla.SlaMonitorService;
import com.resolveiq.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The three end-to-end workflows required for the demonstration, plus search, validation and admin rules. */
class IncidentWorkflowIntegrationTest extends IntegrationTestBase {

    @Autowired
    private SlaMonitorService slaMonitor;

    private Map<String, Object> report(String title, String description, String service, String impact, String urgency,
                                       int users) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("description", description);
        body.put("serviceCode", service);
        body.put("impact", impact);
        body.put("urgency", urgency);
        body.put("affectedUsers", users);
        return body;
    }

    private List<String> inboxSubjects(String user) throws Exception {
        List<String> subjects = new ArrayList<>();
        get(user, "/api/notifications?size=50").body().get("items").forEach(n -> subjects.add(n.get("subject").asText()));
        return subjects;
    }

    @Test
    @DisplayName("TC-39 Workflow 1: employee reports → AI classifies → priority → SLA → technician assigned → notified")
    void workflowReportToAssignment() throws Exception {
        Response r = post("priya", "/api/incidents", report("Cannot log in to the HR portal",
                "SSO login loops and says my password is wrong even after a reset", "HR_PORTAL", "MEDIUM", "HIGH", 5));
        assertThat(r.status()).isEqualTo(201);
        JsonNode i = r.body().get("incident");
        assertThat(i.get("number").asText()).startsWith("INC-");
        assertThat(i.get("autoClassified").asBoolean()).isTrue();
        assertThat(i.at("/category/code").asText()).isEqualTo("AUTHENTICATION");
        assertThat(i.get("priority").asText()).matches("P[1-4]");
        assertThat(i.get("priorityRationale").asText()).contains("Impact MEDIUM");
        assertThat(i.at("/sla/responseDueAt").isMissingNode()).isFalse();
        assertThat(i.get("status").asText()).isEqualTo("ASSIGNED");
        assertThat(r.body().get("assignmentMessage").asText()).startsWith("Assigned to");

        String assignee = userKey(i.get("assignee"));
        assertThat(inboxSubjects(assignee)).anyMatch(s -> s.contains(i.get("number").asText()) && s.contains("assigned"));
        assertThat(inboxSubjects("priya")).anyMatch(s -> s.contains("We received your incident"));
    }

    @Test
    @DisplayName("TC-40 Workflow 2: investigate → request info → reporter answers → resolve → reporter notified → verify → close")
    void workflowInvestigationToClosure() throws Exception {
        JsonNode i = post("ava", "/api/incidents", report("Laptop battery drains in an hour",
                "My laptop battery goes from full to empty in about an hour", "DEVICES", "LOW", "MEDIUM", 1))
                .body().get("incident");
        String n = i.get("number").asText();
        String tech = userKey(i.get("assignee"));

        assertThat(post(tech, "/api/incidents/" + n + "/acknowledge", null).body().get("status").asText())
                .isEqualTo("ACKNOWLEDGED");
        assertThat(post(tech, "/api/incidents/" + n + "/start", null).body().get("status").asText())
                .isEqualTo("IN_PROGRESS");
        post(tech, "/api/incidents/" + n + "/notes", Map.of("type", "INVESTIGATION", "content", "Battery health 41%", "internal", true));
        JsonNode waiting = post(tech, "/api/incidents/" + n + "/request-info", Map.of("text", "Which model is it?")).body();
        assertThat(waiting.get("status").asText()).isEqualTo("AWAITING_INFO");
        assertThat(waiting.at("/sla/paused").asBoolean()).isTrue();

        // the employee does not see the internal investigation note
        JsonNode asReporter = get("ava", "/api/incidents/" + n).body();
        asReporter.get("notes").forEach(note -> assertThat(note.get("internal").asBoolean()).isFalse());
        assertThat(asReporter.get("availableActions").toString()).contains("PROVIDE_INFO");

        assertThat(post("ava", "/api/incidents/" + n + "/provide-info", Map.of("text", "Dell Latitude 5420"))
                .body().get("status").asText()).isEqualTo("IN_PROGRESS");
        JsonNode resolved = post(tech, "/api/incidents/" + n + "/resolve",
                Map.of("summary", "Replaced the battery under warranty", "rootCause", "Battery wear")).body();
        assertThat(resolved.get("status").asText()).isEqualTo("RESOLVED");
        assertThat(inboxSubjects("ava")).anyMatch(s -> s.contains(n) && s.contains("resolved"));

        assertThat(post("ava", "/api/incidents/" + n + "/verify", null).body().get("status").asText()).isEqualTo("VERIFIED");
        assertThat(post("ava", "/api/incidents/" + n + "/close", Map.of("text", "Works now")).body().get("status").asText())
                .isEqualTo("CLOSED");

        JsonNode audit = get("ava", "/api/incidents/" + n + "/audit").body();
        List<String> transitions = new ArrayList<>();
        audit.forEach(a -> {
            if (a.get("action").asText().equals("STATUS_CHANGED")) {
                transitions.add(a.get("previousState").asText() + "→" + a.get("newState").asText());
            }
        });
        assertThat(transitions).containsExactly("NEW→CLASSIFIED", "CLASSIFIED→ASSIGNED", "ASSIGNED→ACKNOWLEDGED",
                "ACKNOWLEDGED→IN_PROGRESS", "IN_PROGRESS→AWAITING_INFO", "AWAITING_INFO→IN_PROGRESS",
                "IN_PROGRESS→RESOLVED", "RESOLVED→VERIFIED", "VERIFIED→CLOSED");
        audit.forEach(a -> {
            assertThat(a.get("occurredAt")).isNotNull();
            assertThat(a.get("actor")).isNotNull();
            assertThat(a.get("incidentNumber").asText()).isEqualTo(n);
        });
    }

    @Test
    @DisplayName("TC-41 Workflow 3: a near-identical report from the same person is refused as a duplicate")
    void duplicateFromSameReporterRejected() throws Exception {
        Map<String, Object> body = report("Fee payment failing at checkout",
                "The fee payment fails at checkout, transaction does not go through", "PAYMENT", "HIGH", "HIGH", 20);
        assertThat(post("arjun", "/api/incidents", body).status()).isEqualTo(201);
        Response again = post("arjun", "/api/incidents", body);
        assertThat(again.status()).isEqualTo(409);
        assertThat(again.code()).isEqualTo("DUPLICATE_INCIDENT");
        assertThat(again.body().get("matches").size()).isGreaterThan(0);

        body.put("acknowledgeDuplicates", true);
        assertThat(post("arjun", "/api/incidents", body).status()).isEqualTo(201);
    }

    @Test
    @DisplayName("TC-42 Workflow 3: related reports are flagged, correlated to a root cause, and closed with the parent")
    void duplicatesCorrelatedAndClosedWithParent() throws Exception {
        JsonNode child = post("rohan", "/api/incidents", report("Payments failing for students",
                "Students say the payment transaction is failing at checkout", "PAYMENT", "MEDIUM", "HIGH", 30))
                .body().get("incident");
        assertThat(child.at("/duplicateOf/number").asText()).isNotBlank();       // flagged as a probable duplicate
        String parent = child.at("/duplicateOf/number").asText();

        JsonNode clusters = get("neha", "/api/intelligence/correlations").body();
        assertThat(clusters.size()).isGreaterThan(0);
        assertThat(clusters.get(0).get("rootServiceCode").asText()).isEqualTo("CORE_DB");

        // resolve the parent: its flagged duplicates are closed automatically
        JsonNode p = get("neha", "/api/incidents/" + parent).body();
        String owner = userKey(p.get("assignee"));
        if (p.get("status").asText().equals("ASSIGNED")) {
            post(owner, "/api/incidents/" + parent + "/acknowledge", null);
            post(owner, "/api/incidents/" + parent + "/start", null);
        }
        Response resolved = post(owner, "/api/incidents/" + parent + "/resolve",
                Map.of("summary", "Increased the database connection pool", "rootCause", "Pool exhausted"));
        assertThat(resolved.status()).isEqualTo(200);
        JsonNode childAfter = get("rohan", "/api/incidents/" + child.get("number").asText()).body();
        assertThat(childAfter.get("status").asText()).isEqualTo("CLOSED");
        assertThat(childAfter.at("/duplicateOf/confirmed").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("TC-43 SLA monitor: an unacknowledged P1 breaches its response target and is escalated")
    void slaBreachAndEscalation() throws Exception {
        Map<String, Object> body = report("Core network switch down", "Whole admin block offline, no network at all",
                "NETWORK_CORE", "CRITICAL", "CRITICAL", 400);
        JsonNode i = post("rohan", "/api/incidents", body).body().get("incident");
        assertThat(i.get("priority").asText()).isEqualTo("P1");
        String n = i.get("number").asText();

        clock.advance(Duration.ofMinutes(6));                    // response target is 5 minutes
        SlaMonitorService.ScanResult scan = slaMonitor.scan();
        assertThat(scan.breaches()).isGreaterThan(0);

        JsonNode after = get("neha", "/api/incidents/" + n).body();
        assertThat(after.at("/sla/responseBreached").asBoolean()).isTrue();
        assertThat(after.at("/sla/status").asText()).isEqualTo("BREACHED");
        assertThat(after.at("/escalation/level").asText()).isEqualTo("TEAM_LEAD");
        String lead = userKey(after.at("/escalation/escalatedTo"));
        assertThat(inboxSubjects(lead)).anyMatch(s -> s.contains("Escalation") && s.contains(n));
    }

    @Test
    @DisplayName("TC-44 Search: priority, status, service, SLA and date filters combine")
    void search() throws Exception {
        JsonNode p1Open = get("neha", "/api/incidents?priority=P1&openOnly=true").body();
        p1Open.get("items").forEach(x -> assertThat(x.get("priority").asText()).isEqualTo("P1"));
        JsonNode payment = get("neha", "/api/incidents?service=PAYMENT&status=CLOSED").body();
        payment.get("items").forEach(x -> {
            assertThat(x.at("/service/code").asText()).isEqualTo("PAYMENT");
            assertThat(x.get("status").asText()).isEqualTo("CLOSED");
        });
        assertThat(payment.get("totalItems").asInt()).isGreaterThan(0);
        assertThat(get("neha", "/api/incidents?q=wi-fi").body().get("totalItems").asInt()).isGreaterThan(0);
        assertThat(get("neha", "/api/incidents?from=2000-01-01&to=2000-01-02").body().get("totalItems").asInt()).isZero();
        assertThat(get("neha", "/api/incidents?slaStatus=PAUSED").body().get("totalItems").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("TC-45 Invalid input and unknown service are rejected with clear errors")
    void validation() throws Exception {
        Response blank = post("ava", "/api/incidents", report("", "", "PAYMENT", "LOW", "LOW", 0));
        assertThat(blank.status()).isEqualTo(400);
        assertThat(blank.body().get("fieldErrors").has("title")).isTrue();
        assertThat(blank.body().get("fieldErrors").has("affectedUsers")).isTrue();

        Response unknown = post("ava", "/api/incidents", report("Broken", "Something broke", "NOPE", "LOW", "LOW", 1));
        assertThat(unknown.status()).isEqualTo(404);
        assertThat(unknown.code()).isEqualTo("SERVICE_NOT_FOUND");

        Response badEnum = post("ava", "/api/incidents", report("Broken", "Something broke", "PAYMENT", "HUGE", "LOW", 1));
        assertThat(badEnum.status()).isEqualTo(400);
    }

    @Test
    @DisplayName("TC-46 Invalid state transition through the API returns 409 with the current state")
    void invalidTransitionApi() throws Exception {
        Response r = post("ava", "/api/incidents/INC-1043/verify", null);  // Ava's incident is AWAITING_INFO
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.code()).isEqualTo("INVALID_STATE_TRANSITION");
        assertThat(r.body().get("currentState").asText()).isEqualTo("AWAITING_INFO");
    }

    @Test
    @DisplayName("TC-47 Admin switches the assignment strategy at runtime; new incidents use it")
    void switchStrategy() throws Exception {
        assertThat(put("admin", "/api/admin/settings", Map.of("ASSIGNMENT_STRATEGY", "LOAD")).status()).isEqualTo(200);
        JsonNode i = post("ava", "/api/incidents", report("Printer offline", "The 4th floor printer shows offline",
                "PRINTING", "LOW", "LOW", 3)).body().get("incident");
        assertThat(i.get("assignmentStrategy").asText()).isEqualTo("Load-based");

        Response bad = put("admin", "/api/admin/settings", Map.of("ASSIGNMENT_STRATEGY", "COIN_FLIP"));
        assertThat(bad.status()).isEqualTo(422);
    }

    @Test
    @DisplayName("TC-48 A new category added at runtime is learned by the classifier immediately")
    void newCategoryLearned() throws Exception {
        Response created = post("admin", "/api/admin/categories", Map.of("code", "IOT", "name", "IoT Devices",
                "keywords", List.of("smart board", "projector", "sensor", "iot", "camera"), "securitySensitive", false));
        assertThat(created.status()).isEqualTo(201);
        JsonNode s = post("ava", "/api/intelligence/classify", Map.of("text", "The classroom projector and smart board stopped responding")).body();
        assertThat(s.get("categoryCode").asText()).isEqualTo("IOT");
    }

    @Test
    @DisplayName("TC-49 Manager reports: KPIs, workload, breakdowns and CSV export")
    void reports() throws Exception {
        JsonNode overview = get("manager", "/api/reports/overview").body();
        assertThat(overview.get("totalIncidents").asInt()).isGreaterThan(40);
        assertThat(overview.get("mttrMinutes").asDouble()).isPositive();
        assertThat(get("manager", "/api/reports/technician-workload").body().size()).isEqualTo(7);
        assertThat(get("manager", "/api/reports/by-service").body().size()).isGreaterThan(5);
        assertThat(get("manager", "/api/reports/trend?days=14").body().size()).isEqualTo(14);
        assertThat(get("manager", "/api/dashboard").body().has("correlations")).isTrue();
        int csv = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/reports/export.csv")
                .header("Authorization", "Bearer " + token("manager"))).andReturn().getResponse().getStatus();
        assertThat(csv).isEqualTo(200);
    }

    @Test
    @DisplayName("Technician can toggle availability; off-duty technicians are skipped by assignment")
    void availability() throws Exception {
        JsonNode me = put("rahul", "/api/technicians/me/availability", Map.of("available", false)).body();
        assertThat(me.at("/technician/available").asBoolean()).isFalse();
        JsonNode recs = get("neha", "/api/incidents/INC-1039/recommendations").body();
        recs.forEach(r -> assertThat(r.at("/technician/name").asText()).isNotEqualTo("Rahul Verma"));
    }
}
