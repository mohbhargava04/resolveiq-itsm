package com.resolveiq.audit;

import com.resolveiq.incident.domain.Actor;
import com.resolveiq.incident.domain.Incident;
import com.resolveiq.incident.domain.NoteType;
import com.resolveiq.incident.event.AssignmentFailed;
import com.resolveiq.incident.event.DuplicateSuspected;
import com.resolveiq.incident.event.FeedbackSubmitted;
import com.resolveiq.incident.event.IncidentAssigned;
import com.resolveiq.incident.event.IncidentClassified;
import com.resolveiq.incident.event.IncidentEscalated;
import com.resolveiq.incident.event.IncidentEvent;
import com.resolveiq.incident.event.IncidentObserver;
import com.resolveiq.incident.event.IncidentReported;
import com.resolveiq.incident.event.IncidentStatusChanged;
import com.resolveiq.incident.event.IncidentUpdated;
import com.resolveiq.incident.event.LinkedAsDuplicate;
import com.resolveiq.incident.event.NoteAdded;
import com.resolveiq.incident.event.PriorityAssessed;
import com.resolveiq.incident.event.SlaBreached;
import com.resolveiq.incident.event.SlaWarningRaised;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

/**
 * Observer that turns every incident event into an audit record, producing the complete incident
 * history ("10:05 INC1024 created by Employee E102 ...").
 *
 * <p>The exhaustive pattern-matching {@code switch} over the sealed {@link IncidentEvent} hierarchy
 * means the compiler forces us to decide how a new event type is audited.</p>
 */
@Component
public class AuditTrailObserver implements IncidentObserver {

    private final AuditRepository repository;
    private final DateTimeFormatter time;

    public AuditTrailObserver(AuditRepository repository,
                              @org.springframework.beans.factory.annotation.Value("${itsm.display-zone:Asia/Kolkata}") String zone) {
        this.repository = repository;
        this.time = DateTimeFormatter.ofPattern("d MMM, HH:mm").withZone(java.time.ZoneId.of(zone));
    }

    @Override
    public int order() {
        return 0;
    }

    @Override
    public boolean isCritical() {
        return true;
    }

    @Override
    public void onIncidentEvent(IncidentEvent event) {
        repository.save(toRecord(event));
    }

    public AuditRecord toRecord(IncidentEvent event) {
        Incident incident = event.incident();
        Actor actor = event.actor();
        AuditRecord.Builder b = AuditRecord.builder(actionFor(event), event.occurredAt())
                .actor(actor.userId(), actor.name(), actor.role() == null ? "SYSTEM" : actor.role().name())
                .incident(incident.getId(), incident.getNumber());

        switch (event) {
            case IncidentReported e -> b.states(null, "NEW").description(
                    incident.getNumber() + " created by " + actor.roleLabel() + " " + actor.name()
                            + " for " + incident.getService().getName() + ": " + incident.getTitle());
            case IncidentClassified e -> b.description("Classified as " + e.categoryName()
                    + (e.automatic() ? String.format(" by the AI classifier (%.0f%% confidence)",
                    (e.confidence() == null ? 0 : e.confidence()) * 100) : " by the reporter"));
            case PriorityAssessed e -> b.states(e.previous(), e.current()).description(
                    (e.previous() == null ? "Priority calculated as " : "Priority changed to ") + e.current()
                            + " using " + e.strategy() + " — " + e.rationale()
                            + (e.reason() == null || e.previous() == null ? "" : " (reason: " + e.reason() + ")"));
            case IncidentAssigned e -> b.states(e.previousAssignee(), e.assigneeName()).description(
                    (e.previousAssignee() == null ? "Assigned to " : "Re-assigned to ") + e.assigneeName()
                            + (e.strategy() == null ? "" : " via " + e.strategy()));
            case IncidentStatusChanged e -> b.states(e.from(), e.to()).description(
                    e.comment() == null || e.comment().isBlank() ? e.from().label() + " to " + e.to().label() : e.comment());
            case NoteAdded e -> b.description(noteLabel(e.noteType()) + (e.internal() ? " (internal)" : "")
                    + ": " + e.excerpt());
            case SlaWarningRaised e -> b.description(target(e.target()) + " SLA approaching: due " + time.format(e.dueAt()));
            case SlaBreached e -> b.description(target(e.target()) + " SLA breached (was due " + time.format(e.dueAt()) + ")");
            case IncidentEscalated e -> b.states(e.from(), e.to()).description(
                    "Escalated to " + e.to().label() + " " + e.targetName() + ": " + e.reason());
            case DuplicateSuspected e -> b.description(String.format(
                    "Possible duplicate of %s (similarity %.0f%%)", e.candidateNumber(), e.score() * 100));
            case LinkedAsDuplicate e -> b.description("Confirmed as duplicate of " + e.parentNumber());
            case FeedbackSubmitted e -> b.description("Reporter rated the resolution " + e.rating() + "/5"
                    + (e.comment() == null ? "" : ": " + e.comment()));
            case AssignmentFailed e -> b.description("Automatic assignment failed: " + e.reason());
            case IncidentUpdated e -> b.description("Details updated: " + e.changes());
        }
        return b.build();
    }

    static AuditAction actionFor(IncidentEvent event) {
        return switch (event) {
            case IncidentReported e -> AuditAction.INCIDENT_CREATED;
            case IncidentClassified e -> AuditAction.INCIDENT_CLASSIFIED;
            case PriorityAssessed e -> e.previous() == null ? AuditAction.PRIORITY_CALCULATED : AuditAction.PRIORITY_CHANGED;
            case IncidentAssigned e -> e.previousAssignee() == null ? AuditAction.INCIDENT_ASSIGNED
                    : AuditAction.INCIDENT_REASSIGNED;
            case IncidentStatusChanged e -> AuditAction.STATUS_CHANGED;
            case NoteAdded e -> switch (e.noteType()) {
                case INFO_REQUEST -> AuditAction.INFO_REQUESTED;
                case INFO_RESPONSE -> AuditAction.INFO_PROVIDED;
                default -> AuditAction.NOTE_ADDED;
            };
            case SlaWarningRaised e -> AuditAction.SLA_WARNING;
            case SlaBreached e -> AuditAction.SLA_BREACHED;
            case IncidentEscalated e -> AuditAction.INCIDENT_ESCALATED;
            case DuplicateSuspected e -> AuditAction.DUPLICATE_SUSPECTED;
            case LinkedAsDuplicate e -> AuditAction.DUPLICATE_LINKED;
            case FeedbackSubmitted e -> AuditAction.FEEDBACK_SUBMITTED;
            case AssignmentFailed e -> AuditAction.ASSIGNMENT_FAILED;
            case IncidentUpdated e -> AuditAction.INCIDENT_UPDATED;
        };
    }

    private static String target(com.resolveiq.sla.SlaTarget t) {
        return t == com.resolveiq.sla.SlaTarget.RESPONSE ? "Response" : "Resolution";
    }

    private static String noteLabel(NoteType type) {
        return switch (type) {
            case INVESTIGATION -> "Investigation note";
            case COMMENT -> "Comment";
            case INFO_REQUEST -> "Information requested";
            case INFO_RESPONSE -> "Information provided";
            case RESOLUTION -> "Resolution details";
            case SYSTEM -> "System note";
        };
    }
}
