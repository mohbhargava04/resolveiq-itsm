package com.resolveiq.web;

import com.resolveiq.audit.AuditRecord;
import com.resolveiq.catalog.Category;
import com.resolveiq.catalog.ItService;
import com.resolveiq.incident.IncidentAccessPolicy;
import com.resolveiq.incident.domain.Actor;
import com.resolveiq.incident.domain.Incident;
import com.resolveiq.incident.domain.IncidentStatus;
import com.resolveiq.intelligence.DuplicateDetector.SimilarIncident;
import com.resolveiq.sla.SlaRecord;
import com.resolveiq.user.domain.User;
import com.resolveiq.web.dto.CommonDtos.PersonRef;
import com.resolveiq.web.dto.CommonDtos.Ref;
import com.resolveiq.web.dto.IncidentDtos.AuditEntry;
import com.resolveiq.web.dto.IncidentDtos.DuplicateView;
import com.resolveiq.web.dto.IncidentDtos.EscalationView;
import com.resolveiq.web.dto.IncidentDtos.FeedbackView;
import com.resolveiq.web.dto.IncidentDtos.IncidentDetail;
import com.resolveiq.web.dto.IncidentDtos.IncidentSummary;
import com.resolveiq.web.dto.IncidentDtos.NoteView;
import com.resolveiq.web.dto.IncidentDtos.ResolutionView;
import com.resolveiq.web.dto.IncidentDtos.SimilarIncidentView;
import com.resolveiq.web.dto.IncidentDtos.SlaView;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Converts domain objects into API response records. Keeping entities out of the JSON layer avoids
 * leaking internals (password hashes, lazy proxies) and lets the API shape evolve independently.
 */
@Component
public class IncidentMapper {

    private final Clock clock;
    private final IncidentAccessPolicy accessPolicy;

    public IncidentMapper(Clock clock, IncidentAccessPolicy accessPolicy) {
        this.clock = clock;
        this.accessPolicy = accessPolicy;
    }

    public IncidentSummary toSummary(Incident i) {
        Instant now = clock.instant();
        SlaRecord sla = i.getSla();
        Long remaining = sla.isStarted() && i.isActive() ? sla.remaining(now).toMinutes() : null;
        return new IncidentSummary(i.getNumber(), i.getTitle(), i.getStatus().name(), name(i.getPriority()),
                i.getPriority() == null ? null : i.getPriority().label(), ref(i.getCategory()), ref(i.getService()),
                person(i.getReporter()), person(i.getAssignee()), i.getCreatedAt(), i.getUpdatedAt(),
                i.slaStatus(now).name(), sla.getResponseDueAt(), sla.getResolutionDueAt(), remaining,
                i.getEscalationLevel().name(),
                i.getDuplicateOf() == null ? null : i.getDuplicateOf().getNumber(), i.isAutoClassified());
    }

    public IncidentDetail toDetail(Incident i, User viewer) {
        Instant now = clock.instant();
        SlaRecord sla = i.getSla();
        boolean showInternal = accessPolicy.canSeeInternalNotes(viewer);
        Duration responseTime = sla.responseTime();
        Duration resolutionTime = sla.resolutionTime();
        SlaView slaView = new SlaView(sla.getPolicyName(), sla.getResponseMinutes(), sla.getResolutionMinutes(),
                sla.getStartedAt(), sla.getResponseDueAt(), sla.getResolutionDueAt(), sla.getRespondedAt(),
                sla.getResolvedAt(), sla.isResponseBreached(), sla.isResolutionBreached(), sla.isPaused(),
                i.slaStatus(now).name(), (int) Math.round(Math.min(sla.elapsedFraction(now), 9.99) * 100),
                sla.isStarted() && i.isActive() ? sla.remaining(now).toMinutes() : null,
                responseTime == null ? null : responseTime.toMinutes(),
                resolutionTime == null ? null : resolutionTime.toMinutes());
        EscalationView escalation = new EscalationView(i.getEscalationLevel().name(), i.getEscalationLevel().label(),
                i.getEscalatedAt(), person(i.getEscalatedTo()));
        ResolutionView resolution = i.getResolution() == null ? null : new ResolutionView(
                i.getResolution().getSummary(), i.getResolution().getRootCause(),
                person(i.getResolution().getResolvedBy()), i.getResolvedAt());
        FeedbackView feedback = i.getFeedback() == null || i.getFeedback().getRating() == null ? null
                : new FeedbackView(i.getFeedback().getRating(), i.getFeedback().getComment(),
                i.getFeedback().getSubmittedAt());
        DuplicateView duplicate = i.getDuplicateOf() == null ? null : new DuplicateView(i.getDuplicateOf().getNumber(),
                i.getDuplicateOf().getTitle(), i.getDuplicateScore(), i.isDuplicateConfirmed());
        var notes = i.getNotes().stream()
                .filter(n -> showInternal || !n.isInternal())
                .map(n -> new NoteView(n.getId(), n.getType().name(), n.isInternal(), n.getAuthorName(),
                        n.getContent(), n.getCreatedAt()))
                .toList();
        Set<String> next = i.allowedNextStatuses().stream().map(IncidentStatus::name).collect(Collectors.toSet());
        return new IncidentDetail(i.getNumber(), i.getTitle(), i.getDescription(), i.getStatus().name(),
                i.getStatus().label(), name(i.getPriority()), i.getPriority() == null ? null : i.getPriority().label(),
                i.getPriorityScore(), i.getPriorityRationale(), i.getPriorityStrategy(), ref(i.getCategory()),
                i.isAutoClassified(), i.getClassificationConfidence(), ref(i.getService()),
                i.getService().getCriticality().name(), i.getImpact().name(), i.getUrgency().name(),
                i.getAffectedUsers(), person(i.getReporter()), person(i.getAssignee()), i.getAssignedAt(),
                i.getAssignmentStrategy(), slaView, escalation, resolution, feedback, duplicate, i.getCreatedAt(),
                i.getUpdatedAt(), i.getClosedAt(), i.getClosureNote(), notes, i.availableActions(Actor.of(viewer)),
                next);
    }

    public SimilarIncidentView toSimilar(SimilarIncident s) {
        Incident i = s.incident();
        return new SimilarIncidentView(i.getNumber(), i.getTitle(), i.getStatus().name(), i.getService().getName(),
                s.score(), s.textSimilarity(), s.sameService());
    }

    public static AuditEntry toAudit(AuditRecord r) {
        return new AuditEntry(r.getId(), r.getOccurredAt(), r.getActorName(), r.getActorRole(), r.getAction().name(),
                r.getIncidentNumber(), r.getPreviousState(), r.getNewState(), r.getDescription());
    }

    public static PersonRef person(User u) {
        return u == null ? null : new PersonRef(u.getId(), u.getFullName(), u.getRole().name());
    }

    public static Ref ref(Category c) {
        return c == null ? null : new Ref(c.getCode(), c.getName());
    }

    public static Ref ref(ItService s) {
        return s == null ? null : new Ref(s.getCode(), s.getName());
    }

    private static String name(Enum<?> e) {
        return e == null ? null : e.name();
    }
}
