package com.resolveiq.notification;

import com.resolveiq.incident.domain.Incident;
import com.resolveiq.incident.domain.IncidentStatus;
import com.resolveiq.incident.domain.NoteType;
import com.resolveiq.incident.domain.Priority;
import com.resolveiq.incident.event.AssignmentFailed;
import com.resolveiq.incident.event.DuplicateSuspected;
import com.resolveiq.incident.event.FeedbackSubmitted;
import com.resolveiq.incident.event.IncidentAssigned;
import com.resolveiq.incident.event.IncidentClassified;
import com.resolveiq.incident.event.IncidentEscalated;
import com.resolveiq.incident.event.IncidentEvent;
import com.resolveiq.incident.event.IncidentReported;
import com.resolveiq.incident.event.IncidentStatusChanged;
import com.resolveiq.incident.event.IncidentUpdated;
import com.resolveiq.incident.event.LinkedAsDuplicate;
import com.resolveiq.incident.event.NoteAdded;
import com.resolveiq.incident.event.PriorityAssessed;
import com.resolveiq.incident.event.SlaBreached;
import com.resolveiq.incident.event.SlaWarningRaised;
import com.resolveiq.user.domain.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.resolveiq.notification.ChannelType.EMAIL;
import static com.resolveiq.notification.ChannelType.IN_APP;
import static com.resolveiq.notification.ChannelType.SMS;

/**
 * Routing rules: for each event, who is told, through which channels, and with what wording.
 * Kept separate from delivery (the channels) and from triggering (the observer) - each class has a
 * single responsibility. Critical (P1) alerts also go out by SMS.
 */
@Component
public class NotificationPolicy {

    /** One recipient with the channels to use. */
    public record Dispatch(User recipient, Set<ChannelType> channels, String subject, String body, boolean urgent) {
    }

    private final RecipientDirectory directory;
    private final DateTimeFormatter timeFormat;

    public NotificationPolicy(RecipientDirectory directory,
                              @Value("${itsm.display-zone:Asia/Kolkata}") String zone) {
        this.directory = directory;
        this.timeFormat = DateTimeFormatter.ofPattern("dd MMM, HH:mm z").withZone(ZoneId.of(zone));
    }

    public List<Dispatch> route(IncidentEvent event) {
        Incident i = event.incident();
        String ref = i.getNumber() + " · " + i.getTitle();
        boolean p1 = i.getPriority() == Priority.P1;
        Plan plan = new Plan(event);

        switch (event) {
            case IncidentReported e -> plan.add(i.getReporter(), EnumSet.of(IN_APP, EMAIL),
                    "We received your incident " + i.getNumber(),
                    "Thanks for reporting \"" + i.getTitle() + "\". Our team has been alerted and you can "
                            + "track progress in ResolveIQ.", false, true);

            case IncidentClassified e -> {
                // covered by the PriorityAssessed notification that is raised at the same time
            }

            case PriorityAssessed e -> {
                if (e.previous() == null) {
                    plan.add(i.getReporter(), EnumSet.of(IN_APP), i.getNumber() + " prioritised as "
                                    + e.current() + " (" + e.current().label() + ")",
                            "Target resolution: " + due(i.getSla().getResolutionDueAt()) + ".", false);
                    if (e.current() == Priority.P1) {
                        directory.supervisors().forEach(u -> plan.add(u, EnumSet.of(IN_APP, EMAIL, SMS),
                                "Critical P1 incident: " + ref,
                                i.getService().getName() + " — " + i.getAffectedUsers()
                                        + " users affected. " + e.rationale(), true));
                    }
                } else {
                    plan.add(i.getReporter(), EnumSet.of(IN_APP), i.getNumber() + " priority changed to " + e.current(),
                            "Priority changed from " + e.previous() + " to " + e.current() + ".", false);
                    plan.add(i.getAssignee(), EnumSet.of(IN_APP, EMAIL), i.getNumber() + " priority changed to "
                            + e.current(), "New resolution target: " + due(i.getSla().getResolutionDueAt())
                            + (e.reason() == null ? "" : ". Reason: " + e.reason()), e.current() == Priority.P1);
                }
            }

            case IncidentAssigned e -> {
                plan.add(i.getAssignee(), p1 ? EnumSet.of(IN_APP, EMAIL, SMS) : EnumSet.of(IN_APP, EMAIL),
                        "New " + i.getPriority() + " incident assigned: " + i.getNumber(),
                        i.getTitle() + " (" + i.getService().getName() + "). Please acknowledge by "
                                + due(i.getSla().getResponseDueAt()) + ".", p1);
                plan.add(i.getReporter(), EnumSet.of(IN_APP), i.getNumber() + " assigned to " + e.assigneeName(),
                        e.assigneeName() + " is now working on your incident.", false);
            }

            case IncidentStatusChanged e -> statusChanged(plan, i, e);

            case NoteAdded e -> {
                if (e.internal()) {
                    break;
                }
                boolean byReporter = i.isReporter(e.actor());
                if (e.noteType() == NoteType.INFO_REQUEST) {
                    plan.add(i.getReporter(), EnumSet.of(IN_APP, EMAIL), "Action needed on " + i.getNumber(),
                            e.actor().name() + " needs more information: \"" + e.excerpt() + "\"", false);
                } else if (e.noteType() == NoteType.INFO_RESPONSE || (byReporter && e.noteType() == NoteType.COMMENT)) {
                    plan.add(i.getAssignee(), EnumSet.of(IN_APP, EMAIL), "Reporter replied on " + i.getNumber(),
                            "\"" + e.excerpt() + "\"", false);
                } else if (e.noteType() == NoteType.COMMENT || e.noteType() == NoteType.INVESTIGATION) {
                    plan.add(i.getReporter(), EnumSet.of(IN_APP), "Update on " + i.getNumber(),
                            e.actor().name() + ": \"" + e.excerpt() + "\"", false);
                }
            }

            case SlaWarningRaised e -> {
                if (i.getAssignee() != null) {
                    plan.add(i.getAssignee(), p1 ? EnumSet.of(IN_APP, EMAIL, SMS) : EnumSet.of(IN_APP, EMAIL),
                            "SLA approaching on " + i.getNumber(),
                            e.target() + " target due " + due(e.dueAt()) + ".", p1);
                } else {
                    directory.teamLeads().forEach(u -> plan.add(u, EnumSet.of(IN_APP),
                            "Unassigned incident nearing SLA: " + i.getNumber(), "Due " + due(e.dueAt()), p1));
                }
            }

            case SlaBreached e -> {
                String subject = e.target() + " SLA breached on " + i.getNumber();
                String body = ref + " missed its " + e.target().name().toLowerCase() + " target ("
                        + due(e.dueAt()) + ").";
                plan.add(i.getAssignee(), p1 ? EnumSet.of(IN_APP, EMAIL, SMS) : EnumSet.of(IN_APP, EMAIL),
                        subject, body, true);
                directory.teamLeadFor(i.getAssignee()).ifPresent(l -> plan.add(l, EnumSet.of(IN_APP, EMAIL),
                        subject, body, true));
                directory.systemAdministrators().forEach(a -> plan.add(a, EnumSet.of(IN_APP), subject, body, true));
            }

            case IncidentEscalated e -> {
                if (e.targetUserId() != null && event.incident().getEscalatedTo() != null) {
                    boolean urgent = i.getPriority() != null && i.getPriority().isHighPriority();
                    plan.add(i.getEscalatedTo(), urgent ? EnumSet.of(IN_APP, EMAIL, SMS) : EnumSet.of(IN_APP, EMAIL),
                            "Escalation: " + i.getNumber() + " needs your attention",
                            ref + " was escalated to you. Reason: " + e.reason(), urgent);
                }
                plan.add(i.getAssignee(), EnumSet.of(IN_APP), i.getNumber() + " escalated to " + e.to().label(),
                        e.reason(), false);
            }

            case DuplicateSuspected e -> {
                Collection<? extends User> who = i.getAssignee() != null ? List.of(i.getAssignee()) : directory.teamLeads();
                who.forEach(u -> plan.add(u, EnumSet.of(IN_APP), "Possible duplicate: " + i.getNumber(),
                        String.format("%s looks like %s (%.0f%% similar). Review and link if appropriate.",
                                i.getNumber(), e.candidateNumber(), e.score() * 100), false));
            }

            case LinkedAsDuplicate e -> plan.add(i.getReporter(), EnumSet.of(IN_APP, EMAIL),
                    i.getNumber() + " merged into " + e.parentNumber(),
                    "Your report describes the same problem as " + e.parentNumber()
                            + ", which is already being worked on. You will be updated through that incident.", false);

            case FeedbackSubmitted e -> {
                plan.add(i.getAssignee(), EnumSet.of(IN_APP), "Feedback on " + i.getNumber() + ": " + e.rating() + "/5",
                        e.comment() == null ? "No comment left." : "\"" + e.comment() + "\"", false);
                if (e.rating() <= 2) {
                    directory.teamLeadFor(i.getAssignee()).ifPresent(l -> plan.add(l, EnumSet.of(IN_APP, EMAIL),
                            "Low satisfaction on " + i.getNumber(), "Rated " + e.rating() + "/5 by the reporter.", false));
                }
            }

            case AssignmentFailed e -> directory.supervisors().forEach(u -> plan.add(u, EnumSet.of(IN_APP, EMAIL),
                    "Needs manual assignment: " + i.getNumber(), e.reason(), p1));

            case IncidentUpdated e -> plan.add(i.getAssignee(), EnumSet.of(IN_APP), i.getNumber() + " was updated",
                    e.changes(), false);
        }
        return plan.dispatches();
    }

    private void statusChanged(Plan plan, Incident i, IncidentStatusChanged e) {
        IncidentStatus to = e.to();
        if (to == IncidentStatus.RESOLVED) {
            plan.add(i.getReporter(), EnumSet.of(IN_APP, EMAIL), i.getNumber() + " has been resolved",
                    "Resolution: " + (i.getResolution() == null ? e.comment() : i.getResolution().getSummary())
                            + "\nPlease confirm the fix works, or reopen it if the problem persists.", false);
        } else if (to == IncidentStatus.CLOSED && !i.isDuplicateConfirmed()) {
            plan.add(i.getReporter(), EnumSet.of(IN_APP, EMAIL), i.getNumber() + " is closed",
                    "Thank you. You can still rate how we did.", false);
        } else if (to == IncidentStatus.ACKNOWLEDGED || (to == IncidentStatus.IN_PROGRESS
                && e.from() == IncidentStatus.ACKNOWLEDGED)) {
            plan.add(i.getReporter(), EnumSet.of(IN_APP), i.getNumber() + " is " + to.label().toLowerCase(),
                    e.comment(), false);
        } else if (to == IncidentStatus.IN_PROGRESS && e.from() == IncidentStatus.RESOLVED) {
            plan.add(i.getAssignee(), EnumSet.of(IN_APP, EMAIL), i.getNumber() + " was reopened",
                    e.comment(), i.getPriority() == Priority.P1);
        }
    }

    private String due(Instant instant) {
        return instant == null ? "not set" : timeFormat.format(instant);
    }

    /** Collects dispatches, merging channels per recipient and skipping the person who acted. */
    private static final class Plan {
        private final IncidentEvent event;
        private final Map<Long, Dispatch> byRecipient = new LinkedHashMap<>();

        Plan(IncidentEvent event) {
            this.event = event;
        }

        void add(User recipient, Set<ChannelType> channels, String subject, String body, boolean urgent) {
            add(recipient, channels, subject, body, urgent, false);
        }

        void add(User recipient, Set<ChannelType> channels, String subject, String body, boolean urgent,
                 boolean includeActor) {
            if (recipient == null || !recipient.isActive()) {
                return;
            }
            if (!includeActor && event.actor().is(recipient)) {
                return; // don't notify people about their own actions
            }
            Dispatch existing = byRecipient.get(recipient.getId());
            if (existing != null) {
                Set<ChannelType> merged = EnumSet.copyOf(existing.channels());
                merged.addAll(channels);
                byRecipient.put(recipient.getId(), new Dispatch(recipient, merged, existing.subject(),
                        existing.body(), existing.urgent() || urgent));
            } else {
                byRecipient.put(recipient.getId(), new Dispatch(recipient, EnumSet.copyOf(channels), subject,
                        body == null ? "" : body, urgent));
            }
        }

        List<Dispatch> dispatches() {
            return new ArrayList<>(byRecipient.values());
        }
    }
}
