package com.resolveiq.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.resolveiq.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** "Report with assistant" driven over HTTP, against the seeded demo organisation. */
class AssistantConversationIntegrationTest extends IntegrationTestBase {

    private static final String SESSIONS = "/api/assistant/sessions";

    /** What a cooperative person answers at each step unless a test says otherwise. */
    private static final Map<String, String> DEFAULT_ANSWERS = Map.of(
            "CONFIRM_SERVICE", "YES", "OFFER_MAJOR_INCIDENT", "DIFFERENT", "OFFER_SELF_HELP", "NOT_FIXED",
            "WARN_DUPLICATE", "REPORT_ANYWAY", "ASK_IMPACT", "LOW", "ASK_URGENCY", "MEDIUM", "ASK_DEVICE", "SKIP",
            "CONFIRM_SUMMARY", "SUBMIT");

    private final List<String> visited = new ArrayList<>();

    private JsonNode start(String user) throws Exception {
        Response r = post(user, SESSIONS, null);
        assertThat(r.status()).isEqualTo(201);
        return r.body();
    }

    private JsonNode say(String user, JsonNode reply, String text) throws Exception {
        return send(user, reply, Map.of("text", text));
    }

    private JsonNode choose(String user, JsonNode reply, String choice) throws Exception {
        return send(user, reply, Map.of("choice", choice));
    }

    private JsonNode send(String user, JsonNode reply, Map<String, String> body) throws Exception {
        Response r = post(user, SESSIONS + "/" + reply.get("sessionId").asLong() + "/messages", body);
        assertThat(r.status()).as("reply to %s", body).isEqualTo(200);
        return r.body();
    }

    /** Answers each step (test-specific answer first, then the default) until the step in {@code until} or the end. */
    private JsonNode drive(String user, JsonNode reply, Map<String, String> answers, String until) throws Exception {
        for (int turn = 0; turn < 20 && !reply.get("done").asBoolean(); turn++) {
            String step = reply.get("step").asText();
            visited.add(step);
            if (step.equals(until)) {
                return reply;
            }
            reply = choose(user, reply, answers.getOrDefault(step, DEFAULT_ANSWERS.get(step)));
        }
        return reply;
    }

    private List<String> options(JsonNode reply) {
        return reply.at("/prompt/options").findValuesAsText("value");
    }

    private int incidentCount(String user) throws Exception {
        return get(user, "/api/incidents?size=1").body().get("totalItems").asInt();
    }

    private List<String> auditActions(String number) throws Exception {
        return get("admin", "/api/audit?incident=" + number + "&size=50").body().findValuesAsText("action");
    }

    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("AST-01 A conversation ends in an incident created by the normal pipeline: classified, prioritised, on an SLA, assigned, notified, audited")
    void conversationCreatesIncidentThroughThePipeline() throws Exception {
        int before = incidentCount("priya");
        JsonNode reply = start("priya");
        assertThat(reply.get("step").asText()).isEqualTo("DESCRIBE_PROBLEM");
        assertThat(reply.get("messages").get(0).asText()).startsWith("Hi Priya");

        reply = say("priya", reply, "The HR Portal shows a blank white page when I open my payslip");
        assertThat(reply.get("step").asText()).isEqualTo("CONFIRM_SERVICE");
        assertThat(reply.get("messages").toString()).contains("HR Portal");

        reply = drive("priya", reply, Map.of("ASK_IMPACT", "MEDIUM", "ASK_URGENCY", "HIGH"), "CONFIRM_SUMMARY");
        JsonNode summary = reply.at("/prompt/summary");
        assertThat(summary.get("service").asText()).isEqualTo("HR Portal");
        assertThat(summary.get("priority").asText()).matches("P[1-4]");
        assertThat(summary.get("responseMinutes").asInt()).isPositive();
        assertThat(incidentCount("priya")).as("nothing is created before the person confirms").isEqualTo(before);

        reply = choose("priya", reply, "SUBMIT");
        assertThat(reply.get("done").asBoolean()).isTrue();
        assertThat(reply.get("outcome").asText()).isEqualTo("INCIDENT_CREATED");
        String number = reply.get("incidentNumber").asText();
        assertThat(reply.get("messages").toString()).contains(number);

        JsonNode incident = get("priya", "/api/incidents/" + number).body();
        assertThat(incident.at("/service/code").asText()).isEqualTo("HR_PORTAL");
        assertThat(incident.get("autoClassified").asBoolean()).isTrue();
        assertThat(incident.get("priority").asText()).isEqualTo(summary.get("priority").asText());
        assertThat(incident.at("/sla/responseDueAt").isMissingNode()).isFalse();
        assertThat(incident.get("status").asText()).isIn("ASSIGNED", "CLASSIFIED");
        assertThat(auditActions(number)).contains("INCIDENT_CREATED", "REPORTED_VIA_ASSISTANT");
        assertThat(get("priya", "/api/notifications?size=30").body().findValuesAsText("subject"))
                .anyMatch(s -> s.contains("We received your incident"));
        assertThat(incidentCount("priya")).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("AST-02 The assistant asks only for what is missing: impact, urgency and device given in the description are not asked again")
    void asksOnlyForMissingInformation() throws Exception {
        JsonNode reply = say("ava", start("ava"),
                "The Campus Wi-Fi keeps disconnecting on my laptop. My whole team is affected and it is urgent, we are blocked.");
        assertThat(reply.get("messages").toString()).contains("affects your team").contains("blocking your work");

        reply = drive("ava", reply, Map.of(), "CONFIRM_SUMMARY");
        assertThat(visited).contains("CONFIRM_SERVICE").doesNotContain("ASK_IMPACT", "ASK_URGENCY", "ASK_DEVICE");
        JsonNode summary = reply.at("/prompt/summary");
        assertThat(summary.get("service").asText()).isEqualTo("Campus Wi-Fi");
        assertThat(summary.get("impact").asText()).contains("team");
        assertThat(summary.get("device").asText()).isEqualTo("Laptop");

        // a plain description asks for both, and for the device because it is a network problem
        visited.clear();
        drive("kabir", say("kabir", start("kabir"), "The Campus Wi-Fi keeps disconnecting in the lab"), Map.of(), "CONFIRM_SUMMARY");
        assertThat(visited).contains("ASK_IMPACT", "ASK_URGENCY", "ASK_DEVICE");
    }

    @Test
    @DisplayName("AST-03 Self-help deflection: a knowledge-base article shown in the chat fixes it; no incident is created and the article is credited")
    void selfHelpDeflection() throws Exception {
        int before = incidentCount("ava");
        JsonNode reply = say("ava", start("ava"), "My account gets locked again after I changed my password");
        reply = drive("ava", reply, Map.of(), "OFFER_SELF_HELP");
        assertThat(reply.get("step").asText()).isEqualTo("OFFER_SELF_HELP");
        JsonNode article = reply.at("/prompt/articles/0");
        assertThat(article.get("solution").asText()).as("the fix is shown inline").isNotBlank();
        long id = article.get("id").asLong();
        int deflections = article.get("deflections").asInt();

        // an article that was never offered cannot be credited
        assertThat(choose("ava", reply, "FIXED:999999").get("step").asText()).isEqualTo("OFFER_SELF_HELP");

        reply = choose("ava", reply, "FIXED:" + id);
        assertThat(reply.get("done").asBoolean()).isTrue();
        assertThat(reply.get("outcome").asText()).isEqualTo("DEFLECTED");
        assertThat(reply.get("articleId").asLong()).isEqualTo(id);
        assertThat(reply.hasNonNull("incidentNumber")).isFalse();
        assertThat(incidentCount("ava")).isEqualTo(before);
        assertThat(get("ava", "/api/kb/" + id).body().get("deflections").asInt()).isEqualTo(deflections + 1);
    }

    @Test
    @DisplayName("AST-04 Duplicate warning: someone else's similar open incident is shown; the person can skip (no ticket) or log theirs, which is flagged")
    void duplicateWarning() throws Exception {
        Map<String, Object> existing = new HashMap<>(Map.of("title", "Lecture videos will not upload to the Learning Portal",
                "description", "Uploading lecture videos to the Learning Portal fails at 99 percent with an error",
                "serviceCode", "LMS", "impact", "MEDIUM", "urgency", "MEDIUM", "affectedUsers", 20));
        String other = post("rohan", "/api/incidents", existing).body().at("/incident/number").asText();
        String text = "Uploading lecture videos to the Learning Portal fails at 99 percent with an error";

        int before = incidentCount("ava");
        JsonNode reply = drive("ava", say("ava", start("ava"), text), Map.of(), "WARN_DUPLICATE");
        assertThat(reply.get("step").asText()).isEqualTo("WARN_DUPLICATE");
        assertThat(reply.at("/prompt/incidents").findValuesAsText("number")).contains(other);
        assertThat(reply.at("/prompt/incidents/0/mine").asBoolean()).isFalse();
        assertThat(options(reply)).containsExactly("REPORT_ANYWAY", "SKIP");

        JsonNode skipped = choose("ava", reply, "SKIP");
        assertThat(skipped.get("outcome").asText()).isEqualTo("DUPLICATE_AVOIDED");
        assertThat(incidentCount("ava")).isEqualTo(before);

        // a second person logs theirs anyway: it is created and flagged as a suspected duplicate
        JsonNode logged = drive("sneha", say("sneha", start("sneha"), text), Map.of(), null);
        assertThat(logged.get("outcome").asText()).isEqualTo("INCIDENT_CREATED");
        JsonNode mine = get("sneha", "/api/incidents/" + logged.get("incidentNumber").asText()).body();
        assertThat(mine.toString()).contains(other);
    }

    @Test
    @DisplayName("AST-05 The person's own earlier report is recognised: 'it's the same' ends without a second ticket")
    void ownDuplicate() throws Exception {
        String text = "The projector in seminar room 2 printer queue is stuck and nothing prints from Printing Services";
        JsonNode first = drive("ava", say("ava", start("ava"), text), Map.of(), null);
        String number = first.get("incidentNumber").asText();
        int before = incidentCount("ava");

        JsonNode reply = drive("ava", say("ava", start("ava"), text), Map.of(), "WARN_DUPLICATE");
        assertThat(reply.at("/prompt/incidents/0/mine").asBoolean()).isTrue();
        assertThat(options(reply)).containsExactly("SAME", "REPORT_ANYWAY");
        reply = say("ava", reply, "yes");
        assertThat(reply.get("outcome").asText()).isEqualTo("DUPLICATE_AVOIDED");
        assertThat(reply.get("incidentNumber").asText()).isEqualTo(number);
        assertThat(incidentCount("ava")).isEqualTo(before);
    }

    @Test
    @DisplayName("AST-06 An active major incident on the same service is offered: 'I'm affected too' links the person instead of a new report")
    void joinsMajorIncident() throws Exception {
        String majorNumber = get("neha", "/api/dashboard").body().at("/correlations/0/incidentNumbers/0").asText();
        assertThat(post("neha", "/api/incidents/" + majorNumber + "/major", Map.of()).status()).isEqualTo(200);
        String serviceName = get("neha", "/api/incidents/" + majorNumber).body().at("/service/name").asText();

        JsonNode reply = say("sneha", start("sneha"), "I think " + serviceName + " is not working for me since this morning");
        reply = drive("sneha", reply, Map.of(), "OFFER_MAJOR_INCIDENT");
        assertThat(reply.get("step").asText()).isEqualTo("OFFER_MAJOR_INCIDENT");
        assertThat(reply.at("/prompt/major/number").asText()).isEqualTo(majorNumber);

        reply = choose("sneha", reply, "AFFECTED");
        assertThat(reply.get("outcome").asText()).isEqualTo("JOINED_MAJOR_INCIDENT");
        JsonNode mine = get("sneha", "/api/incidents/" + reply.get("incidentNumber").asText()).body();
        assertThat(mine.at("/majorParent/number").asText()).isEqualTo(majorNumber);
    }

    @Test
    @DisplayName("AST-07 Invalid answers never break the conversation: too little detail, nonsense, empty and over-long messages are handled")
    void invalidAnswers() throws Exception {
        JsonNode reply = start("ava");
        reply = say("ava", reply, "help");
        assertThat(reply.get("step").asText()).isEqualTo("DESCRIBE_PROBLEM");
        assertThat(reply.get("messages").get(0).asText()).contains("a little more");
        reply = send("ava", reply, Map.of());
        assertThat(reply.get("step").asText()).isEqualTo("DESCRIBE_PROBLEM");

        long id = reply.get("sessionId").asLong();
        assertThat(post("ava", SESSIONS + "/" + id + "/messages", Map.of("text", "x".repeat(4300))).status()).isEqualTo(400);

        // a problem that matches no service: the assistant asks instead of guessing
        reply = say("ava", reply, "Something strange happened with the thing yesterday");
        assertThat(reply.get("step").asText()).isEqualTo("CONFIRM_SERVICE");
        assertThat(options(reply)).contains("PAYMENT", "CAMPUS_WIFI", "DEVICES");
        reply = say("ava", reply, "the flux capacitor");
        assertThat(reply.get("step").asText()).isEqualTo("CONFIRM_SERVICE");
        assertThat(reply.get("messages").get(0).asText()).contains("couldn't match");
        reply = say("ava", reply, "end-user devices");

        reply = drive("ava", reply, Map.of(), "ASK_IMPACT");
        JsonNode confused = say("ava", reply, "purple monkey dishwasher");
        assertThat(confused.get("step").asText()).isEqualTo("ASK_IMPACT");
        assertThat(options(confused)).containsExactly("LOW", "MEDIUM", "HIGH", "CRITICAL");
        assertThat(say("ava", confused, "just me").get("step").asText()).isNotEqualTo("ASK_IMPACT");
    }

    @Test
    @DisplayName("AST-08 Authorization: sign-in is required, and a conversation can only be read or continued by its owner")
    void authorization() throws Exception {
        assertThat(post(null, SESSIONS, null).status()).isEqualTo(401);
        long id = start("ava").get("sessionId").asLong();
        assertThat(get(null, SESSIONS + "/" + id).status()).isEqualTo(401);

        Response peek = get("rahul", SESSIONS + "/" + id);
        assertThat(peek.status()).isEqualTo(403);
        assertThat(peek.code()).isEqualTo("UNAUTHORIZED_OPERATION");
        assertThat(post("admin", SESSIONS + "/" + id + "/messages", Map.of("text", "The Wi-Fi is down in the library")).status())
                .as("not even an administrator can speak in someone else's conversation").isEqualTo(403);
        assertThat(get("ava", SESSIONS + "/999999").status()).isEqualTo(404);

        JsonNode transcript = get("ava", SESSIONS + "/" + id).body();
        assertThat(transcript.get("outcome").asText()).isEqualTo("OPEN");
        assertThat(transcript.get("messages").size()).isEqualTo(2);
        assertThat(transcript.at("/messages/0/sender").asText()).isEqualTo("ASSISTANT");
    }

    @Test
    @DisplayName("AST-09 Change, start over and cancel: answers can be corrected from the summary; cancelling creates nothing and closes the conversation")
    void changeRestartCancel() throws Exception {
        int before = incidentCount("ava");
        JsonNode reply = drive("ava", say("ava", start("ava"), "Printing Services: the 4th floor printer shows offline"),
                Map.of(), "CONFIRM_SUMMARY");
        assertThat(reply.at("/prompt/summary/impact").asText()).isEqualTo("Single user or minor inconvenience");

        reply = choose("ava", reply, "CHANGE_IMPACT");
        assertThat(reply.get("step").asText()).isEqualTo("ASK_IMPACT");
        reply = choose("ava", reply, "HIGH");
        assertThat(reply.get("step").asText()).as("straight back to the summary").isEqualTo("CONFIRM_SUMMARY");
        assertThat(reply.at("/prompt/summary/impact").asText()).contains("Several departments");

        reply = choose("ava", reply, "CHANGE_SERVICE");
        assertThat(reply.get("step").asText()).isEqualTo("CONFIRM_SERVICE");
        reply = say("ava", reply, "start over");
        assertThat(reply.get("step").asText()).isEqualTo("DESCRIBE_PROBLEM");

        reply = say("ava", reply, "cancel");
        assertThat(reply.get("done").asBoolean()).isTrue();
        assertThat(reply.get("outcome").asText()).isEqualTo("CANCELLED");
        assertThat(incidentCount("ava")).isEqualTo(before);

        JsonNode after = say("ava", reply, "The printer is offline again");
        assertThat(after.get("done").asBoolean()).isTrue();
        assertThat(after.get("messages").get(0).asText()).contains("has ended");
    }
}
