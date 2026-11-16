package com.resolveiq.assistant.state;

import com.resolveiq.assistant.ChatSession;
import com.resolveiq.assistant.ConversationStep;
import com.resolveiq.assistant.Prompt;
import com.resolveiq.assistant.StepResult;
import com.resolveiq.assistant.UserInput;

import com.resolveiq.assistant.AnswerInterpreter;
import com.resolveiq.assistant.IncidentDraft;
import com.resolveiq.assistant.ServiceMatcher;
import com.resolveiq.incident.domain.Impact;
import com.resolveiq.incident.domain.Urgency;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * "What's wrong?" Takes the person's own words and learns as much as it can from them - a likely
 * service, and any hint about who is affected, how urgent it is and which device - so later steps
 * that would ask for those are skipped.
 */
@Component
class DescribeProblemState extends AbstractConversationState {

    static final int MIN_LENGTH = 8;
    static final int MAX_LENGTH = 4000;

    private final ServiceMatcher matcher;

    DescribeProblemState(ServiceMatcher matcher) {
        this.matcher = matcher;
    }

    @Override
    public ConversationStep step() {
        return ConversationStep.DESCRIBE_PROBLEM;
    }

    @Override
    public boolean applies(ChatSession session) {
        return !session.getDraft().hasDescription();
    }

    @Override
    public Prompt prompt(ChatSession session) {
        return Prompt.ask("What's going wrong? Describe it in your own words - for example "
                        + "\"The Wi-Fi in the library keeps dropping\".")
                .freeText("Describe the problem…").build();
    }

    @Override
    protected String emptyAnswerMessage() {
        return "Tell me what's going wrong and I'll take it from there.";
    }

    @Override
    protected StepResult answer(ChatSession session, UserInput input) {
        String text = input.text();
        if (text.length() < MIN_LENGTH || text.split("\\s+").length < 2) {
            return StepResult.notUnderstood("Could you tell me a little more? One sentence about what you were doing "
                    + "and what happened is enough.");
        }
        if (text.length() > MAX_LENGTH) {
            return StepResult.notUnderstood("That's a lot of detail - please keep it under " + MAX_LENGTH
                    + " characters. You can attach logs or screenshots to the incident afterwards.");
        }
        IncidentDraft draft = session.getDraft();
        draft.describe(text);

        List<String> noted = new ArrayList<>();
        Optional<Impact> impact = AnswerInterpreter.impactHint(text);
        impact.ifPresent(draft::rateImpact);
        impact.map(DescribeProblemState::phrase).ifPresent(noted::add);
        Optional<Urgency> urgency = AnswerInterpreter.urgencyHint(text);
        urgency.ifPresent(draft::rateUrgency);
        urgency.map(DescribeProblemState::phrase).ifPresent(noted::add);
        AnswerInterpreter.device(text).ifPresent(draft::noteDevice);

        matcher.match(text, 1).stream().findFirst().ifPresent(c -> draft.suggestService(c.service().getCode()));

        return StepResult.next(noted.isEmpty() ? "Thanks."
                : "Thanks. I've noted that " + String.join(" and ", noted) + " - you can change that before I submit.");
    }

    private static String phrase(Impact impact) {
        return switch (impact) {
            case LOW -> "it affects only you";
            case MEDIUM -> "it affects your team";
            case HIGH -> "it affects several departments";
            case CRITICAL -> "it affects the whole organisation";
        };
    }

    private static String phrase(Urgency urgency) {
        return switch (urgency) {
            case LOW -> "it can wait";
            case MEDIUM -> "you need it fixed today";
            case HIGH -> "it is blocking your work";
            case CRITICAL -> "it needs attention immediately";
        };
    }
}
