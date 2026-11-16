package com.resolveiq.assistant.state;

import com.resolveiq.assistant.ChatSession;
import com.resolveiq.assistant.ConversationStep;
import com.resolveiq.assistant.Prompt;
import com.resolveiq.assistant.StepResult;
import com.resolveiq.assistant.UserInput;

import com.resolveiq.assistant.AnswerInterpreter;
import com.resolveiq.assistant.DraftInsight;
import com.resolveiq.incident.IncidentService;
import com.resolveiq.incident.domain.Incident;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

/**
 * An open major incident affects the same service (or one it depends on). Instead of another
 * duplicate report, the person can say "I'm affected too": they get a linked incident that is
 * updated and resolved together with the major one.
 */
@Component
class MajorIncidentState extends AbstractConversationState {

    static final String AFFECTED = "AFFECTED";
    static final String DIFFERENT = "DIFFERENT";

    private final DraftInsight insight;
    private final IncidentService incidents;
    private final Clock clock;

    MajorIncidentState(DraftInsight insight, IncidentService incidents, Clock clock) {
        this.insight = insight;
        this.incidents = incidents;
        this.clock = clock;
    }

    @Override
    public ConversationStep step() {
        return ConversationStep.OFFER_MAJOR_INCIDENT;
    }

    @Override
    public boolean applies(ChatSession session) {
        return !session.getDraft().isMajorChecked() && !insight.relatedMajorIncidents(session.getDraft()).isEmpty();
    }

    @Override
    public Prompt prompt(ChatSession session) {
        Incident major = insight.relatedMajorIncidents(session.getDraft()).getFirst();
        String latest = major.getMajorUpdates().isEmpty() ? null : major.getMajorUpdates().getFirst().getMessage();
        return Prompt.ask("There is an ongoing major incident on " + major.getService().getName()
                        + " that the IT team is already working on. Is this what you are seeing?")
                .major(new Prompt.MajorCard(major.getNumber(), major.getTitle(), major.getService().getName(),
                        major.getMajorStage() == null ? null : major.getMajorStage().label(), latest))
                .option(AFFECTED, "Yes, I'm affected too")
                .option(DIFFERENT, "No, mine is different")
                .build();
    }

    @Override
    protected StepResult answer(ChatSession session, UserInput input) {
        Optional<Boolean> yesNo = AnswerInterpreter.yesNo(input.text());
        boolean typed = input.choice() == null;
        if (input.chose(DIFFERENT) || (typed && yesNo.isPresent() && !yesNo.get())) {
            session.getDraft().markMajorChecked();
            return StepResult.next("Understood - I'll log yours separately.");
        }
        if (input.chose(AFFECTED) || (typed && yesNo.orElse(false))) {
            List<Incident> related = insight.relatedMajorIncidents(session.getDraft());
            if (related.isEmpty()) {
                // it was resolved while we were talking
                session.getDraft().markMajorChecked();
                return StepResult.next("That major incident has just been resolved, so I'll log yours on its own.");
            }
            Incident major = related.getFirst();
            Incident mine = incidents.reportAffected(major.getNumber(), session.getDraft().fullDescription(),
                    session.getOwner());
            session.finishJoiningMajor(mine.getNumber(), clock.instant());
            return StepResult.finished("You're on the list. Your reference is " + mine.getNumber()
                    + ", linked to major incident " + major.getNumber() + ". You'll get every status update, and yours "
                    + "will be resolved together with it - no need to report it again.");
        }
        return StepResult.notUnderstood(PICK_ONE);
    }
}
