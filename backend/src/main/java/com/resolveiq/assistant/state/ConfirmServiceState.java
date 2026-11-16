package com.resolveiq.assistant.state;

import com.resolveiq.assistant.ChatSession;
import com.resolveiq.assistant.ConversationStep;
import com.resolveiq.assistant.Prompt;
import com.resolveiq.assistant.StepResult;
import com.resolveiq.assistant.UserInput;

import com.resolveiq.assistant.AnswerInterpreter;
import com.resolveiq.assistant.DraftInsight;
import com.resolveiq.assistant.IncidentDraft;
import com.resolveiq.assistant.ServiceMatcher;
import com.resolveiq.catalog.ItService;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Confirms the service the assistant guessed, or lets the person pick one. Nothing is filed against
 * a guessed service: the person always confirms, because the service drives priority, SLA and routing.
 */
@Component
class ConfirmServiceState extends AbstractConversationState {

    static final String YES = "YES";
    static final String OTHER = "OTHER";

    private final ServiceMatcher matcher;
    private final DraftInsight insight;

    ConfirmServiceState(ServiceMatcher matcher, DraftInsight insight) {
        this.matcher = matcher;
        this.insight = insight;
    }

    @Override
    public ConversationStep step() {
        return ConversationStep.CONFIRM_SERVICE;
    }

    @Override
    public boolean applies(ChatSession session) {
        return !session.getDraft().isServiceConfirmed();
    }

    @Override
    public Prompt prompt(ChatSession session) {
        IncidentDraft draft = session.getDraft();
        Optional<ItService> guess = insight.service(draft);
        if (guess.isEmpty()) {
            Prompt.Builder all = Prompt.ask("Which service is this about?").freeText("Or type the service name…");
            matcher.activeServices().forEach(s -> all.option(s.getCode(), s.getName(), s.getDescription()));
            return all.build();
        }
        ItService g = guess.get();
        Prompt.Builder b = Prompt.ask("It sounds like this is about " + g.getName()
                + (g.getDescription() == null ? "" : " (" + lowerFirst(g.getDescription()) + ")") + ". Is that right?");
        b.option(YES, "Yes, " + g.getName());
        matcher.match(draft.getDescription(), 3).stream()
                .filter(c -> !c.service().getCode().equals(g.getCode()))
                .forEach(c -> b.option(c.service().getCode(), "No, it's " + c.service().getName()));
        return b.option(OTHER, "Something else").build();
    }

    @Override
    protected StepResult answer(ChatSession session, UserInput input) {
        IncidentDraft draft = session.getDraft();
        boolean hasGuess = draft.getServiceCode() != null;
        Optional<Boolean> yesNo = AnswerInterpreter.yesNo(input.text());
        if (hasGuess && (input.chose(YES) || (input.choice() == null && yesNo.orElse(false)))) {
            return confirm(draft, insight.service(draft).orElse(null));
        }
        if (input.chose(OTHER) || (hasGuess && input.choice() == null && yesNo.isPresent() && !yesNo.get())) {
            draft.suggestService(null);
            return StepResult.next("No problem.");
        }
        Optional<ItService> named = matcher.resolve(input.choice() != null ? input.choice() : input.text());
        if (named.isPresent()) {
            return confirm(draft, named.get());
        }
        draft.suggestService(null);
        return StepResult.next("I couldn't match that to a service I know.");
    }

    private static StepResult confirm(IncidentDraft draft, ItService service) {
        if (service == null) {
            draft.suggestService(null);
            return StepResult.next("That service is no longer available.");
        }
        draft.chooseService(service.getCode());
        return StepResult.next("Got it - " + service.getName() + ".");
    }

    private static String lowerFirst(String s) {
        return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }
}
