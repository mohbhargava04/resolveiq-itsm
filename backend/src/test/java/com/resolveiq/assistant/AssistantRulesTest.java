package com.resolveiq.assistant;

import com.resolveiq.catalog.Criticality;
import com.resolveiq.catalog.ItService;
import com.resolveiq.incident.domain.Impact;
import com.resolveiq.incident.domain.Urgency;
import com.resolveiq.intelligence.text.TextAnalyzer;
import com.resolveiq.user.domain.Employee;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;

import static com.resolveiq.support.TestData.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The assistant's rules without Spring or a database. */
class AssistantRulesTest {

    private static final Instant NOW = Instant.parse("2026-11-16T09:00:00Z");

    @Test
    @DisplayName("AST-10 Everyday phrases become impact and urgency; negations and bare words inside a description are not misread")
    void interpretsEverydayPhrases() {
        assertThat(AnswerInterpreter.impactHint("nobody else has it, only me")).contains(Impact.LOW);
        assertThat(AnswerInterpreter.impactHint("everyone in my team is stuck")).contains(Impact.MEDIUM);
        assertThat(AnswerInterpreter.impactHint("all students are locked out")).contains(Impact.CRITICAL);
        assertThat(AnswerInterpreter.impactHint("the high resolution monitor flickers")).isEmpty();
        assertThat(AnswerInterpreter.urgencyHint("it's not urgent, I have a workaround")).contains(Urgency.LOW);
        assertThat(AnswerInterpreter.urgencyHint("please fix ASAP, I can't work")).contains(Urgency.HIGH);
        assertThat(AnswerInterpreter.urgencyHint("the screen is cracked")).isEmpty();

        assertThat(AnswerInterpreter.impactAnswer(UserInput.ofChoice("HIGH"))).contains(Impact.HIGH);
        assertThat(AnswerInterpreter.impactAnswer(UserInput.ofText("just me"))).contains(Impact.LOW);
        assertThat(AnswerInterpreter.urgencyAnswer(UserInput.ofText("critical"))).contains(Urgency.CRITICAL);
        assertThat(AnswerInterpreter.urgencyAnswer(UserInput.ofText("banana"))).isEmpty();

        assertThat(AnswerInterpreter.yesNo("Yes, that's it")).contains(true);
        assertThat(AnswerInterpreter.yesNo("nope")).contains(false);
        assertThat(AnswerInterpreter.yesNo("maybe")).isEmpty();
        assertThat(AnswerInterpreter.device("My laptop's screen is flickering")).contains("Laptop");
        assertThat(AnswerInterpreter.device("The portal is slow")).isEmpty();
    }

    @Test
    @DisplayName("AST-11 The draft derives a title, keeps related fields consistent and adds the device to the description")
    void draftRules() {
        IncidentDraft draft = IncidentDraft.empty();
        draft.describe("  the wifi keeps dropping in the library.   It started after lunch.  ");
        assertThat(draft.getTitle()).isEqualTo("The wifi keeps dropping in the library");
        assertThat(draft.getDescription()).isEqualTo("the wifi keeps dropping in the library. It started after lunch.");
        assertThat(IncidentDraft.titleFrom("x".repeat(40) + " " + "y".repeat(200)).length()).isLessThanOrEqualTo(IncidentDraft.MAX_TITLE);

        draft.suggestService("CAMPUS_WIFI");
        assertThat(draft.isServiceConfirmed()).isFalse();
        draft.chooseService("CAMPUS_WIFI");
        draft.markMajorChecked();
        draft.markDuplicatesChecked(true);
        draft.chooseService("NETWORK_CORE");
        assertThat(draft.isMajorChecked()).as("checks that depend on the service are redone").isFalse();
        assertThat(draft.isDuplicatesChecked()).isFalse();
        assertThat(draft.isDuplicatesAcknowledged()).isFalse();

        draft.noteDevice("  Laptop ");
        assertThat(draft.isDeviceAsked()).isTrue();
        assertThat(draft.fullDescription()).endsWith("Device: Laptop");
    }

    @Test
    @DisplayName("AST-12 A conversation keeps an append-only transcript, belongs to one person and cannot move once finished")
    void sessionRules() {
        Employee ava = withId(new Employee("Ava Sharma", "ava@x.io", "h", null, null));
        Employee other = withId(new Employee("Rohan Gupta", "rohan@x.io", "h", null, null));
        ChatSession session = new ChatSession(ava);
        assertThat(session.isOwnedBy(ava)).isTrue();
        assertThat(session.isOwnedBy(other)).isFalse();
        assertThat(session.isOwnedBy(null)).isFalse();

        session.say(MessageSender.ASSISTANT, "What's going wrong?", NOW);
        session.say(MessageSender.USER, "  ", NOW);
        session.say(MessageSender.USER, "Wi-Fi is down", NOW);
        assertThat(session.getMessages()).extracting(ChatMessage::getText).containsExactly("What's going wrong?", "Wi-Fi is down");
        assertThat(session.getMessages()).extracting(ChatMessage::getPosition).containsExactly(0, 1);
        assertThatThrownBy(() -> session.getMessages().clear()).isInstanceOf(UnsupportedOperationException.class);

        session.moveTo(ConversationStep.ASK_IMPACT);
        assertThatThrownBy(() -> session.moveTo(ConversationStep.FINISHED)).isInstanceOf(IllegalArgumentException.class);
        session.finishWithIncident("INC-2001", NOW);
        assertThat(session.isFinished()).isTrue();
        assertThat(session.getStep()).isEqualTo(ConversationStep.FINISHED);
        assertThat(session.getOutcome()).isEqualTo(ConversationOutcome.INCIDENT_CREATED);
        assertThatThrownBy(() -> session.moveTo(ConversationStep.ASK_URGENCY)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(session::restart).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> session.cancel(NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("AST-13 Service matching: naming the service settles it; otherwise its own words are weighed, name words double")
    void serviceWording() {
        ItService wifi = new ItService("CAMPUS_WIFI", "Campus Wi-Fi", "Wireless network across campus and hostels",
                Criticality.MEDIUM, "Network Operations", 5000);
        ItService payment = new ItService("PAYMENT", "Payment Gateway", "Fee and purchase payments (UPI, cards, net banking)",
                Criticality.CRITICAL, "Finance Systems", 5000);
        assertThat(score(wifi, "the campus wi-fi is down again")).isEqualTo(1.0);
        assertThat(score(payment, "my fee payment by UPI fails")).isGreaterThan(score(wifi, "my fee payment by UPI fails"));
        assertThat(score(wifi, "wifi in the hostel is unusable")).isGreaterThan(score(payment, "wifi in the hostel is unusable"));
        assertThat(score(payment, "my chair is broken")).isZero();
    }

    private static double score(ItService service, String text) {
        return ServiceMatcher.wordingScore(service, new HashSet<>(TextAnalyzer.analyze(text)),
                " " + text.toLowerCase(Locale.ROOT) + " ");
    }
}
