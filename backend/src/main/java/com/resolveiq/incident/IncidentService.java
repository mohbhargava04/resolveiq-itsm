package com.resolveiq.incident;

import com.resolveiq.assignment.AssignmentService;
import com.resolveiq.catalog.Category;
import com.resolveiq.catalog.CategoryRepository;
import com.resolveiq.catalog.ItService;
import com.resolveiq.catalog.ItServiceRepository;
import com.resolveiq.common.exception.BusinessRuleViolationException;
import com.resolveiq.common.exception.DuplicateIncidentException;
import com.resolveiq.common.exception.IncidentNotFoundException;
import com.resolveiq.common.exception.InvalidIncidentException;
import com.resolveiq.common.exception.ItsmException;
import com.resolveiq.common.exception.ServiceNotFoundException;
import com.resolveiq.common.exception.TechnicianUnavailableException;
import com.resolveiq.common.exception.UnauthorizedOperationException;
import com.resolveiq.common.exception.WorkloadExceededException;
import com.resolveiq.escalation.EscalationService;
import com.resolveiq.incident.domain.Actor;
import com.resolveiq.incident.domain.Classification;
import com.resolveiq.incident.domain.Impact;
import com.resolveiq.incident.domain.Incident;
import com.resolveiq.incident.domain.NoteType;
import com.resolveiq.incident.domain.Priority;
import com.resolveiq.incident.domain.Resolution;
import com.resolveiq.incident.domain.Urgency;
import com.resolveiq.incident.event.IncidentEventPublisher;
import com.resolveiq.intelligence.CategoryClassifier;
import com.resolveiq.intelligence.CategorySuggestion;
import com.resolveiq.intelligence.DuplicateDetector;
import com.resolveiq.intelligence.DuplicateDetector.SimilarIncident;
import com.resolveiq.priority.PriorityAssessment;
import com.resolveiq.priority.PriorityEngine;
import com.resolveiq.settings.SettingKey;
import com.resolveiq.settings.SettingsService;
import com.resolveiq.sla.SlaPolicy;
import com.resolveiq.sla.SlaPolicyResolver;
import com.resolveiq.user.domain.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Application service: the single entry point for every incident use case. It loads the aggregate,
 * lets the domain object enforce the rules, persists it, then publishes the recorded events to the
 * observers. Controllers stay thin and the domain stays free of infrastructure concerns.
 */
@Service
@Transactional
public class IncidentService {

    /** Minimum classifier confidence to accept an AI category without a human choosing one. */
    static final double AUTO_CATEGORY_MIN_CONFIDENCE = 0.20;
    static final String FALLBACK_CATEGORY = "APPLICATION";

    /** What happened when an incident was reported - shown to the reporter immediately. */
    public record ReportOutcome(Incident incident, List<SimilarIncident> similarIncidents,
                                CategorySuggestion categorySuggestion, String assignmentMessage) {
    }

    private final IncidentRepository incidents;
    private final IncidentFactory factory;
    private final ItServiceRepository services;
    private final CategoryRepository categories;
    private final PriorityEngine priorityEngine;
    private final SlaPolicyResolver slaResolver;
    private final AssignmentService assignment;
    private final EscalationService escalation;
    private final DuplicateDetector duplicateDetector;
    private final CategoryClassifier classifier;
    private final SettingsService settings;
    private final IncidentEventPublisher publisher;
    private final IncidentAccessPolicy accessPolicy;
    private final Clock clock;
    private final ZoneId zone;

    public IncidentService(IncidentRepository incidents, IncidentFactory factory, ItServiceRepository services,
                           CategoryRepository categories, PriorityEngine priorityEngine, SlaPolicyResolver slaResolver,
                           AssignmentService assignment, EscalationService escalation,
                           DuplicateDetector duplicateDetector, CategoryClassifier classifier,
                           SettingsService settings, IncidentEventPublisher publisher,
                           IncidentAccessPolicy accessPolicy, Clock clock,
                           @Value("${itsm.display-zone:Asia/Kolkata}") String zone) {
        this.incidents = incidents;
        this.factory = factory;
        this.services = services;
        this.categories = categories;
        this.priorityEngine = priorityEngine;
        this.slaResolver = slaResolver;
        this.assignment = assignment;
        this.escalation = escalation;
        this.duplicateDetector = duplicateDetector;
        this.classifier = classifier;
        this.settings = settings;
        this.publisher = publisher;
        this.accessPolicy = accessPolicy;
        this.clock = clock;
        this.zone = ZoneId.of(zone);
    }

    // =========================================================================================
    // Report → Classify → Prioritise → SLA → Assign → Notify   (Workflow 1 of the brief)
    // =========================================================================================

    public ReportOutcome report(ReportIncidentCommand cmd, User reporter) {
        Instant now = clock.instant();
        ItService service = services.findByCodeIgnoreCase(require(cmd.serviceCode(), "The affected service is required"))
                .filter(ItService::isActive)
                .orElseThrow(() -> new ServiceNotFoundException(cmd.serviceCode()));

        // 1. Duplicate detection
        List<SimilarIncident> similar = duplicateDetector.findSimilarOpenIncidents(
                safe(cmd.title()), safe(cmd.description()), service, null, now);
        if (!cmd.acknowledgeDuplicates()) {
            List<SimilarIncident> ownNearIdentical = similar.stream()
                    .filter(duplicateDetector::isNearIdentical)
                    .filter(s -> s.incident().getReporter().getId().equals(reporter.getId()))
                    .toList();
            if (!ownNearIdentical.isEmpty()) {
                throw new DuplicateIncidentException(ownNearIdentical.stream().map(this::describe).toList());
            }
        }

        // 2. Classification - manual or AI-suggested
        CategorySuggestion suggestion = null;
        Category category;
        boolean automatic;
        Double confidence;
        if (cmd.categoryCode() != null && !cmd.categoryCode().isBlank()) {
            category = categories.findByCodeIgnoreCase(cmd.categoryCode()).filter(Category::isActive)
                    .orElseThrow(() -> new InvalidIncidentException("Unknown incident category '" + cmd.categoryCode() + "'"));
            automatic = false;
            confidence = null;
        } else {
            suggestion = classifier.classify(safe(cmd.title()) + " " + safe(cmd.description()));
            String code = suggestion.isConfident(AUTO_CATEGORY_MIN_CONFIDENCE) ? suggestion.categoryCode() : FALLBACK_CATEGORY;
            category = categories.findByCodeIgnoreCase(code)
                    .orElseThrow(() -> new InvalidIncidentException("Could not determine a category; please choose one"));
            automatic = true;
            confidence = suggestion.confidence();
        }

        // 3. Create, prioritise and start the SLA clock
        Incident incident = factory.create(cmd.title(), cmd.description(), reporter, service, cmd.impact(),
                cmd.urgency(), cmd.affectedUsers(), now);
        PriorityAssessment assessment = priorityEngine.assess(incident.getImpact(), incident.getUrgency(),
                incident.getAffectedUsers(), service, category);
        SlaPolicy policy = slaResolver.resolve(service, assessment.priority());
        incident.classify(new Classification(category, automatic, confidence, assessment, policy), Actor.SYSTEM, now);

        // 4. Flag a probable duplicate for human review (does not block)
        similar.stream().filter(duplicateDetector::isSuspectedDuplicate).findFirst()
                .ifPresent(s -> incident.flagSuspectedDuplicate(s.incident(), s.score(), now));

        // 5. Automatic assignment
        String assignmentMessage = null;
        if (settings.isEnabled(SettingKey.AUTO_ASSIGNMENT)) {
            try {
                User chosen = assignment.autoAssign(incident, now);
                assignmentMessage = "Assigned to " + chosen.getFullName();
            } catch (TechnicianUnavailableException | WorkloadExceededException ex) {
                incident.recordAssignmentFailure(ex.getMessage(), now);
                assignmentMessage = "Queued for manual assignment: " + ex.getMessage();
            }
        }

        Incident saved = incidents.save(incident);
        publisher.publishAll(saved.pullEvents());
        return new ReportOutcome(saved, similar, suggestion, assignmentMessage);
    }

    // =========================================================================================
    // Queries
    // =========================================================================================

    @Transactional(readOnly = true)
    public Incident get(String number, User viewer) {
        Incident incident = load(number);
        accessPolicy.requireView(viewer, incident);
        return incident;
    }

    @Transactional(readOnly = true)
    public Page<Incident> search(IncidentSearchCriteria criteria, Pageable pageable, User viewer) {
        IncidentSearchCriteria scoped = accessPolicy.canSeeInternalNotes(viewer) ? criteria
                : criteria.restrictedToReporter(viewer.getId());
        return incidents.findAll(IncidentSpecifications.from(scoped, clock.instant(), zone), pageable);
    }

    // =========================================================================================
    // Lifecycle commands
    // =========================================================================================

    public Incident updateDetails(String number, String title, String description, Impact impact, Urgency urgency,
                                  Integer affectedUsers, User actor) {
        return execute(number, actor, (incident, now) -> {
            Impact newImpact = impact == null ? incident.getImpact() : impact;
            Urgency newUrgency = urgency == null ? incident.getUrgency() : urgency;
            int newUsers = affectedUsers == null ? incident.getAffectedUsers() : affectedUsers;
            boolean recalc = incident.getPriority() != null
                    && incident.priorityInputsDifferFrom(newImpact, newUrgency, newUsers);
            incident.updateDetails(title == null ? incident.getTitle() : title,
                    description == null ? incident.getDescription() : description,
                    newImpact, newUrgency, newUsers, Actor.of(actor), now);
            if (recalc) {
                recalculatePriority(incident, "Impact/urgency/affected users changed", Actor.of(actor), now);
            }
        });
    }

    /**
     * Assigns a technician: explicitly chosen, or by a named strategy, or by the active strategy.
     */
    public Incident assign(String number, Long technicianId, String strategyKey, User actor) {
        return execute(number, actor, (incident, now) -> {
            Actor who = Actor.of(actor);
            if (!who.isManagement()) {
                throw new UnauthorizedOperationException("Only team leads and administrators can assign incidents");
            }
            if (technicianId != null) {
                assignment.assignManually(incident, technicianId, who, now);
            } else {
                var ranked = assignment.recommend(incident, strategyKey, now);
                if (ranked.isEmpty()) {
                    throw new TechnicianUnavailableException("No eligible technician found using "
                            + (strategyKey == null ? assignment.activeStrategy().displayName()
                            : assignment.require(strategyKey).displayName()));
                }
                var strategy = strategyKey == null ? assignment.activeStrategy() : assignment.require(strategyKey);
                incident.assignTo(ranked.getFirst().profile().technician(), strategy.displayName(), who, now);
            }
        });
    }

    public Incident acknowledge(String number, User actor) {
        return execute(number, actor, (i, now) -> i.acknowledge(Actor.of(actor), now));
    }

    public Incident startWork(String number, User actor) {
        return execute(number, actor, (i, now) -> i.startWork(Actor.of(actor), now));
    }

    public Incident requestInformation(String number, String question, User actor) {
        return execute(number, actor, (i, now) -> i.requestInformation(question, Actor.of(actor), now));
    }

    public Incident provideInformation(String number, String answer, User actor) {
        return execute(number, actor, (i, now) -> i.provideInformation(answer, Actor.of(actor), now));
    }

    public Incident resolve(String number, String summary, String rootCause, User actor) {
        return execute(number, actor, (i, now) -> {
            if (summary == null || summary.isBlank()) {
                throw new InvalidIncidentException("Resolution details are required");
            }
            i.resolve(new Resolution(summary, rootCause, actor), Actor.of(actor), now);
            closeSuspectedDuplicatesOf(i, now);
        });
    }

    public Incident verify(String number, User actor) {
        return execute(number, actor, (i, now) -> {
            i.verify(Actor.of(actor), now);
            if (settings.isEnabled(SettingKey.AUTO_CLOSE_VERIFIED)) {
                i.close("Closed automatically after verification", Actor.SYSTEM, now);
            }
        });
    }

    public Incident reopen(String number, String reason, User actor) {
        return execute(number, actor, (i, now) -> i.reopen(reason, Actor.of(actor), now));
    }

    public Incident close(String number, String note, User actor) {
        return execute(number, actor, (i, now) -> i.close(note, Actor.of(actor), now));
    }

    public Incident addNote(String number, NoteType type, String content, boolean internal, User actor) {
        if (type == NoteType.SYSTEM || type == NoteType.RESOLUTION || type == NoteType.INFO_REQUEST) {
            throw new BusinessRuleViolationException("Use the dedicated action to add a " + type + " entry");
        }
        return execute(number, actor, (i, now) -> i.addNote(type, content, internal, actor, now));
    }

    public Incident escalate(String number, String reason, User actor) {
        return execute(number, actor, (i, now) -> escalation.escalateManually(i, reason, Actor.of(actor), now));
    }

    public Incident submitFeedback(String number, int rating, String comment, User actor) {
        return execute(number, actor, (i, now) -> i.submitFeedback(rating, comment, Actor.of(actor), now));
    }

    /** Recalculate from the current inputs, or override to a specific priority with a reason. */
    public Incident reprioritise(String number, Priority override, String reason, User actor) {
        return execute(number, actor, (i, now) -> {
            if (override == null) {
                recalculatePriority(i, reason == null ? "Recalculated" : reason, Actor.of(actor), now);
            } else {
                if (reason == null || reason.isBlank()) {
                    throw new InvalidIncidentException("A reason is required to override the calculated priority");
                }
                PriorityAssessment manual = new PriorityAssessment(override, i.getPriorityScore() == null ? 0
                        : i.getPriorityScore(), "Manual override by " + actor.getFullName(), List.of(reason.trim()));
                i.reprioritise(manual, slaResolver.resolve(i.getService(), override), reason, Actor.of(actor), now);
            }
        });
    }

    public Incident markDuplicate(String number, String parentNumber, User actor) {
        return execute(number, actor, (i, now) -> {
            Incident parent = load(parentNumber);
            i.closeAsDuplicateOf(parent, Actor.of(actor), now);
        });
    }

    public Incident dismissDuplicate(String number, User actor) {
        return execute(number, actor, (i, now) -> {
            if (!Actor.of(actor).isManagement() && !i.isAssignee(Actor.of(actor))) {
                throw new UnauthorizedOperationException("Only IT staff can dismiss duplicate warnings");
            }
            i.dismissSuspectedDuplicate();
        });
    }

    // =========================================================================================
    // Helpers
    // =========================================================================================

    /** Template for every command: load, check visibility, mutate, save, publish events. */
    private Incident execute(String number, User actor, BiConsumer<Incident, Instant> action) {
        Incident incident = load(number);
        accessPolicy.requireView(actor, incident);
        action.accept(incident, clock.instant());
        Incident saved = incidents.save(incident);
        publisher.publishAll(saved.pullEvents());
        return saved;
    }

    private void recalculatePriority(Incident incident, String reason, Actor actor, Instant now) {
        PriorityAssessment assessment = priorityEngine.assess(incident.getImpact(), incident.getUrgency(),
                incident.getAffectedUsers(), incident.getService(), incident.getCategory());
        if (assessment.priority() == incident.getPriority()) {
            return;
        }
        SlaPolicy policy = slaResolver.resolve(incident.getService(), assessment.priority());
        incident.reprioritise(assessment, policy, reason, actor, now);
    }

    /**
     * When a parent incident is resolved, open incidents flagged as its duplicates are closed against
     * it automatically, and their reporters are notified (Workflow 3).
     */
    private void closeSuspectedDuplicatesOf(Incident parent, Instant now) {
        for (Incident child : incidents.findDuplicatesOf(parent.getId())) {
            if (child.isDuplicateConfirmed() || !child.isActive()) {
                continue;
            }
            try {
                child.closeAsDuplicateOf(parent, Actor.SYSTEM, now);
                publisher.publishAll(incidents.save(child).pullEvents());
            } catch (ItsmException ignored) {
                // child in a state that cannot be closed as duplicate; leave it for a human
            }
        }
    }

    private Incident load(String number) {
        return incidents.findByNumberIgnoreCase(number == null ? "" : number.trim())
                .orElseThrow(() -> new IncidentNotFoundException(number));
    }

    private Map<String, Object> describe(SimilarIncident s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("number", s.incident().getNumber());
        m.put("title", s.incident().getTitle());
        m.put("status", s.incident().getStatus().name());
        m.put("score", s.score());
        return m;
    }

    private static String require(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new InvalidIncidentException(message);
        }
        return value;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
