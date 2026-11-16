package com.resolveiq.assistant.state;

import com.resolveiq.assistant.ChatSession;
import com.resolveiq.assistant.ConversationStep;
import com.resolveiq.assistant.Prompt;
import com.resolveiq.assistant.StepResult;
import com.resolveiq.assistant.UserInput;

import com.resolveiq.assistant.AnswerInterpreter;
import com.resolveiq.assistant.DraftInsight;
import com.resolveiq.incident.domain.Incident;
import com.resolveiq.intelligence.DuplicateDetector.SimilarIncident;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

/**
 * Similar open incidents exist. If one is the person's own earlier report, they are pointed back to
 * it; if other people reported it, they can still log theirs (it is flagged for the team to link)
 * or decide not to.
 */
@Component
class DuplicateWarningState extends AbstractConversationState {

    static final String SAME = "SAME";
    static final String REPORT_ANYWAY = "REPORT_ANYWAY";
    static final String SKIP = "SKIP";

    private final DraftInsight insight;
    private final Clock clock;

    DuplicateWarningState(DraftInsight insight, Clock clock) {
        this.insight = insight;
        this.clock = clock;
    }

    @Override
    public ConversationStep step() {
        return ConversationStep.WARN_DUPLICATE;
    }

    @Override
    public boolean applies(ChatSession session) {
        return !session.getDraft().isDuplicatesChecked() && !insight.likelyDuplicates(session.getDraft()).isEmpty();
    }

    @Override
    public Prompt prompt(ChatSession session) {
        List<SimilarIncident> similar = insight.likelyDuplicates(session.getDraft());
        Optional<Incident> own = ownReport(session, similar);
        List<Prompt.IncidentCard> cards = similar.stream().limit(3).map(s -> card(session, s.incident())).toList();
        if (own.isPresent()) {
            return Prompt.ask("You already have an open report that looks like the same problem (" + own.get().getNumber()
                            + "). Is this the same thing?")
                    .incidents(cards)
                    .option(SAME, "Yes, it's the same - don't create another")
                    .option(REPORT_ANYWAY, "No, this is a different problem")
                    .build();
        }
        return Prompt.ask(similar.size() == 1
                        ? "Heads-up: the IT team already has a similar open report. I can still log yours, so you get "
                        + "updates and the team can see how many people are affected."
                        : "Heads-up: the IT team already has " + similar.size() + " similar open reports. I can still log "
                        + "yours, so you get updates and the team can see how many people are affected.")
                .incidents(cards)
                .option(REPORT_ANYWAY, "Log mine too")
                .option(SKIP, "No need - don't create a ticket")
                .build();
    }

    @Override
    protected StepResult answer(ChatSession session, UserInput input) {
        List<SimilarIncident> similar = insight.likelyDuplicates(session.getDraft());
        if (similar.isEmpty()) {
            session.getDraft().markDuplicatesChecked(false);
            return StepResult.next();
        }
        Optional<Incident> own = ownReport(session, similar);
        Optional<Boolean> yesNo = input.choice() == null ? AnswerInterpreter.yesNo(input.text()) : Optional.empty();
        // typed yes/no answers the question that was asked: "is it the same?" or "shall I log yours too?"
        boolean same = input.chose(SAME) || (own.isPresent() && yesNo.orElse(false));
        boolean skip = input.chose(SKIP) || (own.isEmpty() && yesNo.isPresent() && !yesNo.get());
        boolean anyway = input.chose(REPORT_ANYWAY) || (own.isPresent() ? yesNo.isPresent() && !yesNo.get() : yesNo.orElse(false));

        if (same && own.isPresent()) {
            session.finishAsDuplicate(own.get().getNumber(), clock.instant());
            return StepResult.finished("No new ticket created. " + own.get().getNumber() + " is "
                    + own.get().getStatus().label().toLowerCase() + " - open it to add details or check progress.");
        }
        if (skip) {
            Incident existing = similar.getFirst().incident();
            session.finishAsDuplicate(existing.getNumber(), clock.instant());
            return StepResult.finished("OK, I haven't created a ticket. The IT team is already on it ("
                    + existing.getNumber() + ", " + existing.getStatus().label().toLowerCase() + ").");
        }
        if (anyway) {
            session.getDraft().markDuplicatesChecked(true);
            return StepResult.next("OK - I'll log it and flag the possible link for the team.");
        }
        return StepResult.notUnderstood(PICK_ONE);
    }

    private static Optional<Incident> ownReport(ChatSession session, List<SimilarIncident> similar) {
        return similar.stream().map(SimilarIncident::incident)
                .filter(i -> session.isOwnedBy(i.getReporter())).findFirst();
    }

    private static Prompt.IncidentCard card(ChatSession session, Incident i) {
        return new Prompt.IncidentCard(i.getNumber(), i.getTitle(), i.getStatus().label(), i.getService().getName(),
                session.isOwnedBy(i.getReporter()));
    }
}
