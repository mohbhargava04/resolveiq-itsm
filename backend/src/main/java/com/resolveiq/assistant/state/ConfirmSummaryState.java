package com.resolveiq.assistant.state;

import com.resolveiq.assistant.ChatSession;
import com.resolveiq.assistant.ConversationStep;
import com.resolveiq.assistant.Prompt;
import com.resolveiq.assistant.StepResult;
import com.resolveiq.assistant.UserInput;

import com.resolveiq.assistant.AnswerInterpreter;
import com.resolveiq.assistant.DraftInsight;
import com.resolveiq.assistant.IncidentDraft;
import com.resolveiq.audit.AuditAction;
import com.resolveiq.audit.AuditService;
import com.resolveiq.catalog.Category;
import com.resolveiq.catalog.ItService;
import com.resolveiq.common.exception.DuplicateIncidentException;
import com.resolveiq.incident.IncidentService;
import com.resolveiq.incident.ReportIncidentCommand;
import com.resolveiq.incident.domain.Incident;
import com.resolveiq.intelligence.CategorySuggestion;
import com.resolveiq.priority.PriorityAssessment;
import com.resolveiq.priority.PriorityEngine;
import com.resolveiq.sla.SlaPolicy;
import com.resolveiq.sla.SlaPolicyResolver;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Optional;

/**
 * Shows exactly what will be sent, with the priority and SLA it is expected to get, and creates the
 * incident only when the person confirms. Creation goes through {@link IncidentService#report} -
 * the same pipeline as the form - so classification, priority, SLA, assignment, notifications and
 * the audit trail all apply unchanged.
 */
@Component
class ConfirmSummaryState extends AbstractConversationState {

    static final String SUBMIT = "SUBMIT";
    static final String CHANGE_SERVICE = "CHANGE_SERVICE";
    static final String CHANGE_IMPACT = "CHANGE_IMPACT";
    static final String CHANGE_URGENCY = "CHANGE_URGENCY";

    private final DraftInsight insight;
    private final IncidentService incidents;
    private final PriorityEngine priorityEngine;
    private final SlaPolicyResolver slaResolver;
    private final AuditService audit;
    private final Clock clock;

    ConfirmSummaryState(DraftInsight insight, IncidentService incidents, PriorityEngine priorityEngine,
                        SlaPolicyResolver slaResolver, AuditService audit, Clock clock) {
        this.insight = insight;
        this.incidents = incidents;
        this.priorityEngine = priorityEngine;
        this.slaResolver = slaResolver;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public ConversationStep step() {
        return ConversationStep.CONFIRM_SUMMARY;
    }

    /** Always the last thing asked: everything else has been answered by now. */
    @Override
    public boolean applies(ChatSession session) {
        return true;
    }

    @Override
    public Prompt prompt(ChatSession session) {
        return Prompt.ask("Here's what I'll send to the IT team. Shall I submit it?")
                .summary(summarise(session.getDraft()))
                .option(SUBMIT, "Submit")
                .option(CHANGE_SERVICE, "Change the service")
                .option(CHANGE_IMPACT, "Change who is affected")
                .option(CHANGE_URGENCY, "Change the urgency")
                .build();
    }

    @Override
    protected StepResult answer(ChatSession session, UserInput input) {
        IncidentDraft draft = session.getDraft();
        if (input.chose(CHANGE_SERVICE)) {
            draft.forgetService();
            return StepResult.next("Sure.");
        }
        if (input.chose(CHANGE_IMPACT)) {
            draft.rateImpact(null);
            return StepResult.next("Sure.");
        }
        if (input.chose(CHANGE_URGENCY)) {
            draft.rateUrgency(null);
            return StepResult.next("Sure.");
        }
        Optional<Boolean> yesNo = input.choice() == null ? AnswerInterpreter.yesNo(input.text()) : Optional.empty();
        if (input.chose(SUBMIT) || yesNo.orElse(false) || input.text().equalsIgnoreCase("submit")) {
            return submit(session);
        }
        if (yesNo.isPresent()) {
            return StepResult.notUnderstood("No problem - tell me what to change using the options below, "
                    + "or choose Start over.");
        }
        return StepResult.notUnderstood(PICK_ONE);
    }

    private StepResult submit(ChatSession session) {
        IncidentDraft draft = session.getDraft();
        IncidentService.ReportOutcome outcome;
        try {
            // no category is passed: the pipeline classifies the text itself, exactly as for the form
            outcome = incidents.report(new ReportIncidentCommand(draft.getTitle(), draft.fullDescription(),
                    draft.getServiceCode(), null, draft.getImpact(), draft.getUrgency(), null,
                    draft.isDuplicatesAcknowledged()), session.getOwner());
        } catch (DuplicateIncidentException e) {
            // the person's own near-identical report: send them through the duplicate warning
            draft.reopenDuplicateCheck();
            return StepResult.next("Hold on - it looks like you have already reported this.");
        }
        Incident incident = outcome.incident();
        audit.recordIncidentAction(session.getOwner(), AuditAction.REPORTED_VIA_ASSISTANT, incident.getId(),
                incident.getNumber(), "Reported through the assistant (conversation " + session.getId() + ")");
        session.finishWithIncident(incident.getNumber(), clock.instant());

        StringBuilder done = new StringBuilder("Done - ").append(incident.getNumber()).append(" is logged");
        if (incident.getPriority() != null) {
            done.append(" as ").append(incident.getPriority().name()).append(" (")
                    .append(incident.getPriority().label()).append(")");
        }
        done.append(". ");
        if (outcome.assignmentMessage() != null) {
            done.append(outcome.assignmentMessage()).append(". ");
        }
        done.append("You'll be notified as it progresses, and you can add screenshots or details on the incident page.");
        return StepResult.finished(done.toString());
    }

    private Prompt.Summary summarise(IncidentDraft draft) {
        Optional<ItService> service = insight.service(draft);
        CategorySuggestion suggestion = insight.categorySuggestion(draft);
        Optional<Category> category = insight.category(draft);
        String priority = null;
        String priorityLabel = null;
        Integer response = null;
        Integer resolution = null;
        if (service.isPresent() && draft.getImpact() != null && draft.getUrgency() != null) {
            PriorityAssessment a = priorityEngine.assess(draft.getImpact(), draft.getUrgency(), 1, service.get(),
                    category.orElse(null));
            SlaPolicy policy = slaResolver.resolve(service.get(), a.priority());
            priority = a.priority().name();
            priorityLabel = a.priority().label();
            response = policy.getResponseMinutes();
            resolution = policy.getResolutionMinutes();
        }
        return new Prompt.Summary(draft.getTitle(), draft.getDescription(),
                service.map(ItService::getName).orElse(null), category.map(Category::getName).orElse(null),
                category.isPresent() && category.get().getCode().equals(suggestion.categoryCode())
                        ? suggestion.confidence() : null,
                draft.getImpact() == null ? null : draft.getImpact().description(),
                draft.getUrgency() == null ? null : draft.getUrgency().description(),
                draft.getDevice(), priority, priorityLabel, response, resolution);
    }
}
