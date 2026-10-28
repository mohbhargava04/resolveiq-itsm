package com.resolveiq.incident.domain;

import com.resolveiq.catalog.Category;
import com.resolveiq.catalog.ItService;
import com.resolveiq.common.BaseEntity;
import com.resolveiq.common.exception.BusinessRuleViolationException;
import com.resolveiq.common.exception.InvalidIncidentException;
import com.resolveiq.common.exception.InvalidStateTransitionException;
import com.resolveiq.common.exception.UnauthorizedOperationException;
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
import com.resolveiq.priority.PriorityAssessment;
import com.resolveiq.sla.SlaPolicy;
import com.resolveiq.sla.SlaRecord;
import com.resolveiq.sla.SlaStatus;
import com.resolveiq.sla.SlaTarget;
import com.resolveiq.user.domain.Role;
import com.resolveiq.user.domain.Technician;
import com.resolveiq.user.domain.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The incident aggregate root - the central object of the system.
 *
 * <h2>Design notes</h2>
 * <ul>
 *   <li><b>Encapsulation</b>: no setters. Every change goes through a business method that
 *       validates permissions and invariants, so an incident can never be put in an inconsistent
 *       state by a careless caller.</li>
 *   <li><b>State pattern</b>: lifecycle methods delegate to {@link IncidentState} objects; this class
 *       contains the permission checks and the "apply" steps the states call back into.</li>
 *   <li><b>Composition</b>: notes, the SLA record, the resolution and feedback are owned parts.</li>
 *   <li><b>Association / aggregation</b>: the reporter, assignee, category and service exist
 *       independently and are only referenced.</li>
 *   <li><b>Domain events</b>: each change records an {@link IncidentEvent}; the application layer
 *       publishes them to observers (audit trail, notifications, live dashboard) after saving.</li>
 * </ul>
 */
@Entity
@Table(name = "incidents")
public class Incident extends BaseEntity {

    public static final int MAX_TITLE = 160;
    public static final int MAX_DESCRIPTION = 4000;

    @Column(nullable = false, unique = true, length = 20)
    private String number;

    @Column(nullable = false, length = MAX_TITLE)
    private String title;

    @Column(nullable = false, length = MAX_DESCRIPTION)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id")
    private User reporter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(name = "auto_classified", nullable = false)
    private boolean autoClassified;

    @Column(name = "classification_confidence")
    private Double classificationConfidence;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id")
    private ItService service;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Impact impact;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Urgency urgency;

    @Column(name = "affected_users", nullable = false)
    private int affectedUsers;

    @Enumerated(EnumType.STRING)
    @Column(length = 5)
    private Priority priority;

    @Column(name = "priority_score")
    private Double priorityScore;

    @Column(name = "priority_rationale", length = 1000)
    private String priorityRationale;

    @Column(name = "priority_strategy", length = 60)
    private String priorityStrategy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IncidentStatus status = IncidentStatus.NEW;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private Technician assignee;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    @Column(name = "assignment_strategy", length = 60)
    private String assignmentStrategy;

    @Embedded
    private SlaRecord sla = new SlaRecord();

    @Enumerated(EnumType.STRING)
    @Column(name = "escalation_level", nullable = false, length = 20)
    private EscalationLevel escalationLevel = EscalationLevel.NONE;

    @Column(name = "escalated_at")
    private Instant escalatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "escalated_to_id")
    private User escalatedTo;

    @Embedded
    private Resolution resolution;

    @Embedded
    private Feedback feedback;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "duplicate_of_id")
    private Incident duplicateOf;

    @Column(name = "duplicate_score")
    private Double duplicateScore;

    @Column(name = "duplicate_confirmed", nullable = false)
    private boolean duplicateConfirmed;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closure_note", length = 1000)
    private String closureNote;

    @OneToMany(mappedBy = "incident", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC, id ASC")
    private List<IncidentNote> notes = new ArrayList<>();

    @Transient
    private final List<IncidentEvent> pendingEvents = new ArrayList<>();

    protected Incident() {
        // for JPA
    }

    /** Use {@link com.resolveiq.incident.IncidentFactory}; this constructor only enforces invariants. */
    public Incident(String number, String title, String description, User reporter, ItService service,
                    Impact impact, Urgency urgency, int affectedUsers, Instant reportedAt) {
        this.number = Objects.requireNonNull(number, "number");
        this.reporter = Objects.requireNonNull(reporter, "reporter");
        this.service = requireService(service);
        changeDescriptiveFields(title, description, impact, urgency, affectedUsers);
        this.status = IncidentStatus.NEW;
        overrideCreatedAt(reportedAt);
        record(new IncidentReported(this, Actor.of(reporter), reportedAt));
    }

    // =========================================================================================
    // Lifecycle operations (public API) - permission check, then delegate to the current state
    // =========================================================================================

    public void classify(Classification classification, Actor actor, Instant at) {
        state().classify(this, classification, actor, at);
    }

    public void assignTo(Technician technician, String strategy, Actor actor, Instant at) {
        Objects.requireNonNull(technician, "technician");
        if (!actor.isManagement()) {
            throw new UnauthorizedOperationException("Only team leads and administrators can assign incidents");
        }
        if (technician.equals(assignee) && status == IncidentStatus.ASSIGNED) {
            throw new BusinessRuleViolationException(number + " is already assigned to " + technician.getFullName());
        }
        state().assign(this, technician, strategy, actor, at);
    }

    public void acknowledge(Actor actor, Instant at) {
        if (!isAssignee(actor)) {
            throw new UnauthorizedOperationException("Only the assigned technician can acknowledge " + number);
        }
        state().acknowledge(this, actor, at);
    }

    public void startWork(Actor actor, Instant at) {
        requireAssigneeOrManagement(actor, "start work on");
        state().startWork(this, actor, at);
    }

    public void requestInformation(String question, Actor actor, Instant at) {
        requireAssigneeOrManagement(actor, "request information for");
        requireText(question, "A question for the reporter is required");
        state().requestInformation(this, question, actor, at);
    }

    public void provideInformation(String answer, Actor actor, Instant at) {
        if (!isReporter(actor) && !actor.isManagement()) {
            throw new UnauthorizedOperationException("Only the reporter can answer an information request");
        }
        requireText(answer, "The requested information is required");
        state().provideInformation(this, answer, actor, at);
    }

    public void resolve(Resolution resolution, Actor actor, Instant at) {
        requireAssigneeOrManagement(actor, "resolve");
        state().resolve(this, Objects.requireNonNull(resolution, "resolution"), actor, at);
    }

    public void verify(Actor actor, Instant at) {
        if (!isReporter(actor) && !actor.hasRole(Role.ADMIN, Role.MANAGER, Role.TEAM_LEAD) && !actor.isSystem()) {
            throw new UnauthorizedOperationException("Only the reporter or a supervisor can verify the resolution");
        }
        state().verify(this, actor, at);
    }

    public void reopen(String reason, Actor actor, Instant at) {
        if (!isReporter(actor) && !actor.hasRole(Role.ADMIN, Role.MANAGER, Role.TEAM_LEAD)) {
            throw new UnauthorizedOperationException("Only the reporter or a supervisor can reopen " + number);
        }
        requireText(reason, "Please say why the resolution did not work");
        state().reopen(this, reason, actor, at);
    }

    public void close(String note, Actor actor, Instant at) {
        if (!isReporter(actor) && !actor.isManagement()) {
            throw new UnauthorizedOperationException("Only the reporter or a supervisor can close " + number);
        }
        state().close(this, note, actor, at);
    }

    public void closeAsDuplicateOf(Incident parent, Actor actor, Instant at) {
        Objects.requireNonNull(parent, "parent");
        if (!actor.isManagement() && !isAssignee(actor)) {
            throw new UnauthorizedOperationException("Only IT staff can link duplicates");
        }
        if (parent == this || parent.getNumber().equals(number)) {
            throw new BusinessRuleViolationException("An incident cannot duplicate itself");
        }
        if (parent.getDuplicateOf() != null && parent.isDuplicateConfirmed()) {
            throw new BusinessRuleViolationException(parent.getNumber() + " is itself a duplicate of "
                    + parent.getDuplicateOf().getNumber() + "; link to the original instead");
        }
        state().closeAsDuplicate(this, parent, actor, at);
    }

    // =========================================================================================
    // Operations that do not change the lifecycle state
    // =========================================================================================

    public IncidentNote addNote(NoteType type, String content, boolean internal, User author, Instant at) {
        if (!state().acceptsNotes()) {
            throw new InvalidStateTransitionException(status.name(), "ADD_NOTES_TO");
        }
        Actor actor = author == null ? Actor.SYSTEM : Actor.of(author);
        boolean isStaff = actor.isSystem() || (actor.role() != null && actor.role() != Role.EMPLOYEE);
        if (!isStaff && !isReporter(actor)) {
            throw new UnauthorizedOperationException("You can only add notes to your own incidents");
        }
        if (!isStaff && internal) {
            throw new UnauthorizedOperationException("Employees cannot create internal notes");
        }
        if (type == NoteType.INVESTIGATION && !isStaff) {
            throw new UnauthorizedOperationException("Only IT staff can add investigation notes");
        }
        IncidentNote note = new IncidentNote(this, author, actor.name(), type, internal, content, at);
        notes.add(note);
        record(new NoteAdded(this, actor, at, type, internal, excerpt(content)));
        return note;
    }

    public void updateDetails(String newTitle, String newDescription, Impact newImpact, Urgency newUrgency,
                              int newAffectedUsers, Actor actor, Instant at) {
        if (!state().allowsDetailEdits()) {
            throw new InvalidStateTransitionException(status.name(), "EDIT");
        }
        if (!isReporter(actor) && !isAssignee(actor) && !actor.isManagement()) {
            throw new UnauthorizedOperationException("You cannot edit " + number);
        }
        List<String> changes = new ArrayList<>();
        if (!Objects.equals(title, newTitle)) {
            changes.add("title");
        }
        if (!Objects.equals(description, newDescription)) {
            changes.add("description");
        }
        if (impact != newImpact) {
            changes.add("impact " + impact + "→" + newImpact);
        }
        if (urgency != newUrgency) {
            changes.add("urgency " + urgency + "→" + newUrgency);
        }
        if (affectedUsers != newAffectedUsers) {
            changes.add("affected users " + affectedUsers + "→" + newAffectedUsers);
        }
        changeDescriptiveFields(newTitle, newDescription, newImpact, newUrgency, newAffectedUsers);
        if (!changes.isEmpty()) {
            record(new IncidentUpdated(this, actor, at, String.join(", ", changes)));
        }
    }

    /** True when an edit touched a field that feeds the priority formula. */
    public boolean priorityInputsDifferFrom(Impact otherImpact, Urgency otherUrgency, int otherUsers) {
        return impact != otherImpact || urgency != otherUrgency || affectedUsers != otherUsers;
    }

    public void reprioritise(PriorityAssessment assessment, SlaPolicy policy, String reason, Actor actor, Instant at) {
        if (!state().allowsReprioritisation()) {
            throw new InvalidStateTransitionException(status.name(), "REPRIORITISE");
        }
        if (!actor.isManagement() && !isAssignee(actor)) {
            throw new UnauthorizedOperationException("Only the assignee or a supervisor can change priority");
        }
        Priority previous = this.priority;
        applyPriority(assessment);
        sla.retarget(policy);
        record(new PriorityAssessed(this, actor, at, previous, priority, assessment.score(),
                assessment.rationale(), assessment.strategy(), reason));
    }

    public void escalate(EscalationLevel target, User targetUser, String reason, Actor actor, Instant at) {
        if (!state().allowsEscalation()) {
            throw new InvalidStateTransitionException(status.name(), "ESCALATE");
        }
        if (!actor.isManagement() && !isAssignee(actor)) {
            throw new UnauthorizedOperationException("Only the assignee or a supervisor can escalate " + number);
        }
        if (!escalationLevel.isBelow(target)) {
            throw new BusinessRuleViolationException(number + " is already escalated to " + escalationLevel.label());
        }
        EscalationLevel previous = escalationLevel;
        escalationLevel = target;
        escalatedAt = at;
        escalatedTo = targetUser;
        String targetName = targetUser == null ? target.label() : targetUser.getFullName();
        notes.add(new IncidentNote(this, null, Actor.SYSTEM.name(), NoteType.SYSTEM, true,
                "Escalated to " + target.label() + " (" + targetName + "): " + reason, at));
        record(new IncidentEscalated(this, actor, at, previous, target,
                targetUser == null ? null : targetUser.getId(), targetName, reason));
    }

    public void submitFeedback(int rating, String comment, Actor actor, Instant at) {
        if (!isReporter(actor)) {
            throw new UnauthorizedOperationException("Only the reporter can rate the resolution");
        }
        if (!state().acceptsFeedback()) {
            throw new InvalidStateTransitionException(status.name(), "RATE");
        }
        if (feedback != null && feedback.getRating() != null) {
            throw new BusinessRuleViolationException("Feedback has already been submitted for " + number);
        }
        feedback = new Feedback(rating, comment, at);
        record(new FeedbackSubmitted(this, actor, at, rating, feedback.getComment()));
    }

    /** Flags a possible duplicate for a human to confirm; does not change state. */
    public void flagSuspectedDuplicate(Incident candidate, double score, Instant at) {
        if (candidate == null || candidate == this) {
            return;
        }
        this.duplicateOf = candidate;
        this.duplicateScore = score;
        this.duplicateConfirmed = false;
        record(new DuplicateSuspected(this, Actor.SYSTEM, at, candidate.getNumber(), score));
    }

    public void dismissSuspectedDuplicate() {
        if (!duplicateConfirmed) {
            duplicateOf = null;
            duplicateScore = null;
        }
    }

    public void recordAssignmentFailure(String reason, Instant at) {
        record(new AssignmentFailed(this, Actor.SYSTEM, at, reason));
    }

    /** Called periodically by the SLA monitor. Records warning/breach events only once each. */
    public List<SlaRecord.Signal> evaluateSla(Instant now) {
        if (!status.isActive()) {
            return List.of();
        }
        List<SlaRecord.Signal> signals = sla.evaluate(now);
        for (SlaRecord.Signal s : signals) {
            if (s.kind() == SlaRecord.Signal.Kind.BREACH) {
                record(new SlaBreached(this, Actor.SYSTEM, now, s.target(), s.dueAt()));
            } else {
                record(new SlaWarningRaised(this, Actor.SYSTEM, now, s.target(), s.dueAt()));
            }
        }
        return signals;
    }

    // =========================================================================================
    // Callbacks used by the State objects (package-private: not part of the public API)
    // =========================================================================================

    void applyClassification(Classification c, Actor actor, Instant at) {
        this.category = c.category();
        this.autoClassified = c.automatic();
        this.classificationConfidence = c.confidence();
        applyPriority(c.assessment());
        sla.start(c.slaPolicy(), getCreatedAt() == null ? at : getCreatedAt());
        record(new IncidentClassified(this, actor, at, category.getCode(), category.getName(),
                c.automatic(), c.confidence()));
        record(new PriorityAssessed(this, actor, at, null, priority, c.assessment().score(),
                c.assessment().rationale(), c.assessment().strategy(), "Initial assessment"));
    }

    void applyAssignment(Technician technician, String strategy, Actor actor, Instant at) {
        if (!technician.isAssignable()) {
            throw new BusinessRuleViolationException(technician.getFullName() + " is not available for assignment");
        }
        String previous = assignee == null ? null : assignee.getFullName();
        this.assignee = technician;
        this.assignedAt = at;
        this.assignmentStrategy = strategy;
        technician.recordAssignment(at);
        record(new IncidentAssigned(this, actor, at, previous, technician.getId(), technician.getFullName(), strategy));
    }

    void applyAcknowledgement(Actor actor, Instant at) {
        if (sla.markResponded(at)) {
            record(new SlaBreached(this, Actor.SYSTEM, at, SlaTarget.RESPONSE, sla.getResponseDueAt()));
        }
    }

    void applyInformationRequest(String question, Actor actor, Instant at) {
        notes.add(new IncidentNote(this, null, actor.name(), NoteType.INFO_REQUEST, false, question, at));
        sla.pause(at);
        record(new NoteAdded(this, actor, at, NoteType.INFO_REQUEST, false, excerpt(question)));
    }

    void applyInformationResponse(String answer, Actor actor, Instant at) {
        notes.add(new IncidentNote(this, reporter, actor.name(), NoteType.INFO_RESPONSE, false, answer, at));
        sla.resume(at);
        record(new NoteAdded(this, actor, at, NoteType.INFO_RESPONSE, false, excerpt(answer)));
    }

    void applyResolution(Resolution r, Actor actor, Instant at) {
        this.resolution = r;
        notes.add(new IncidentNote(this, r.getResolvedBy(), actor.name(), NoteType.RESOLUTION, false,
                r.getSummary() + (r.getRootCause() == null ? "" : "\nRoot cause: " + r.getRootCause()), at));
        if (sla.markResolved(at)) {
            record(new SlaBreached(this, Actor.SYSTEM, at, SlaTarget.RESOLUTION, sla.getResolutionDueAt()));
        }
    }

    void applyReopen(String reason, Actor actor, Instant at) {
        sla.reopen();
        notes.add(new IncidentNote(this, null, actor.name(), NoteType.COMMENT, false, "Reopened: " + reason, at));
    }

    void applyClosure(String note, Instant at) {
        this.closedAt = at;
        this.closureNote = note == null || note.isBlank() ? null : note.trim();
    }

    void applyDuplicateClosure(Incident parent, Actor actor, Instant at) {
        this.duplicateOf = parent;
        this.duplicateConfirmed = true;
        this.closedAt = at;
        this.closureNote = "Duplicate of " + parent.getNumber();
        notes.add(new IncidentNote(this, null, Actor.SYSTEM.name(), NoteType.SYSTEM, false,
                "Closed as a duplicate of " + parent.getNumber() + ". Progress will be tracked there.", at));
        record(new LinkedAsDuplicate(this, actor, at, parent.getNumber()));
        moveTo(IncidentStatus.CLOSED, actor, at, "Closed as duplicate of " + parent.getNumber());
    }

    void moveTo(IncidentStatus next, Actor actor, Instant at, String comment) {
        IncidentStatus previous = this.status;
        this.status = next;
        record(new IncidentStatusChanged(this, actor, at, previous, next, comment));
    }

    // =========================================================================================
    // Helpers
    // =========================================================================================

    private IncidentState state() {
        return IncidentStates.of(status);
    }

    private void applyPriority(PriorityAssessment a) {
        this.priority = a.priority();
        this.priorityScore = Math.round(a.score() * 100.0) / 100.0;
        this.priorityRationale = truncate(a.rationale(), 1000);
        this.priorityStrategy = a.strategy();
    }

    private void changeDescriptiveFields(String title, String description, Impact impact, Urgency urgency,
                                         int affectedUsers) {
        requireText(title, "A short title is required");
        requireText(description, "A description of the problem is required");
        if (title.trim().length() > MAX_TITLE) {
            throw new InvalidIncidentException("Title must be at most " + MAX_TITLE + " characters");
        }
        if (description.trim().length() > MAX_DESCRIPTION) {
            throw new InvalidIncidentException("Description must be at most " + MAX_DESCRIPTION + " characters");
        }
        if (impact == null || urgency == null) {
            throw new InvalidIncidentException("Impact and urgency are required");
        }
        if (affectedUsers < 1) {
            throw new InvalidIncidentException("At least one user must be affected");
        }
        this.title = title.trim();
        this.description = description.trim();
        this.impact = impact;
        this.urgency = urgency;
        this.affectedUsers = affectedUsers;
    }

    private static ItService requireService(ItService service) {
        if (service == null) {
            throw new InvalidIncidentException("The affected IT service is required");
        }
        if (!service.isActive()) {
            throw new InvalidIncidentException(service.getName() + " is no longer an active service");
        }
        return service;
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new InvalidIncidentException(message);
        }
    }

    private void requireAssigneeOrManagement(Actor actor, String action) {
        if (!isAssignee(actor) && !actor.isManagement()) {
            throw new UnauthorizedOperationException("Only the assigned technician or a supervisor can "
                    + action + " " + number);
        }
    }

    private void record(IncidentEvent event) {
        pendingEvents.add(event);
    }

    private static String excerpt(String text) {
        return truncate(text, 140);
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    public boolean isReporter(Actor actor) {
        return actor != null && actor.is(reporter);
    }

    public boolean isAssignee(Actor actor) {
        return actor != null && assignee != null && actor.is(assignee);
    }

    /** Returns and clears the events recorded since the last call. */
    public List<IncidentEvent> pullEvents() {
        List<IncidentEvent> copy = List.copyOf(pendingEvents);
        pendingEvents.clear();
        return copy;
    }

    public boolean isActive() {
        return status.isActive();
    }

    public SlaStatus slaStatus(Instant now) {
        return getSla().status(now);
    }

    public java.util.Set<IncidentStatus> allowedNextStatuses() {
        return state().allowedTransitions();
    }

    /**
     * Which operations this actor may perform right now. Combines the current state's capabilities
     * with the same permission rules the lifecycle methods enforce, so the UI never offers a button
     * that would be rejected.
     */
    public java.util.Set<IncidentAction> availableActions(Actor actor) {
        java.util.Set<IncidentAction> actions = java.util.EnumSet.noneOf(IncidentAction.class);
        boolean reporter = isReporter(actor);
        boolean assignee = isAssignee(actor);
        boolean management = actor.isManagement();
        boolean supervisor = actor.hasRole(Role.TEAM_LEAD, Role.ADMIN, Role.MANAGER);
        boolean staff = actor.role() != null && actor.role() != Role.EMPLOYEE;
        java.util.Set<IncidentStatus> next = state().allowedTransitions();

        if (state().allowsDetailEdits() && (reporter || assignee || management)) {
            actions.add(IncidentAction.EDIT);
        }
        if (management && next.contains(IncidentStatus.ASSIGNED)) {
            actions.add(IncidentAction.ASSIGN);
        }
        if (assignee && status == IncidentStatus.ASSIGNED) {
            actions.add(IncidentAction.ACKNOWLEDGE);
        }
        if ((assignee || management) && status == IncidentStatus.ACKNOWLEDGED) {
            actions.add(IncidentAction.START_WORK);
        }
        if ((assignee || management) && status == IncidentStatus.IN_PROGRESS) {
            actions.add(IncidentAction.REQUEST_INFO);
            actions.add(IncidentAction.RESOLVE);
        }
        if ((reporter || management) && status == IncidentStatus.AWAITING_INFO) {
            actions.add(IncidentAction.PROVIDE_INFO);
        }
        if ((reporter || supervisor) && status == IncidentStatus.RESOLVED) {
            actions.add(IncidentAction.VERIFY);
            actions.add(IncidentAction.REOPEN);
        }
        if ((reporter || management) && status == IncidentStatus.VERIFIED) {
            actions.add(IncidentAction.CLOSE);
        }
        if (state().acceptsNotes() && (staff || reporter)) {
            actions.add(IncidentAction.ADD_NOTE);
            if (staff) {
                actions.add(IncidentAction.ADD_INTERNAL_NOTE);
            }
        }
        if (state().allowsReprioritisation() && (assignee || management)) {
            actions.add(IncidentAction.REPRIORITISE);
        }
        if (state().allowsEscalation() && (assignee || management) && !escalationLevel.isTop()) {
            actions.add(IncidentAction.ESCALATE);
        }
        if ((management || assignee) && next.contains(IncidentStatus.CLOSED) && status != IncidentStatus.VERIFIED) {
            actions.add(IncidentAction.MARK_DUPLICATE);
        }
        if (reporter && state().acceptsFeedback() && (feedback == null || feedback.getRating() == null)) {
            actions.add(IncidentAction.FEEDBACK);
        }
        return actions;
    }

    // =========================================================================================
    // Accessors
    // =========================================================================================

    public String getNumber() {
        return number;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public User getReporter() {
        return reporter;
    }

    public Category getCategory() {
        return category;
    }

    public boolean isAutoClassified() {
        return autoClassified;
    }

    public Double getClassificationConfidence() {
        return classificationConfidence;
    }

    public ItService getService() {
        return service;
    }

    public Impact getImpact() {
        return impact;
    }

    public Urgency getUrgency() {
        return urgency;
    }

    public int getAffectedUsers() {
        return affectedUsers;
    }

    public Priority getPriority() {
        return priority;
    }

    public Double getPriorityScore() {
        return priorityScore;
    }

    public String getPriorityRationale() {
        return priorityRationale;
    }

    public String getPriorityStrategy() {
        return priorityStrategy;
    }

    public IncidentStatus getStatus() {
        return status;
    }

    public Technician getAssignee() {
        return assignee;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public String getAssignmentStrategy() {
        return assignmentStrategy;
    }

    public SlaRecord getSla() {
        if (sla == null) {
            sla = new SlaRecord();
        }
        return sla;
    }

    public EscalationLevel getEscalationLevel() {
        return escalationLevel;
    }

    public Instant getEscalatedAt() {
        return escalatedAt;
    }

    public User getEscalatedTo() {
        return escalatedTo;
    }

    public Resolution getResolution() {
        return resolution;
    }

    public Feedback getFeedback() {
        return feedback;
    }

    public Incident getDuplicateOf() {
        return duplicateOf;
    }

    public Double getDuplicateScore() {
        return duplicateScore;
    }

    public boolean isDuplicateConfirmed() {
        return duplicateConfirmed;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public String getClosureNote() {
        return closureNote;
    }

    public Instant getResolvedAt() {
        return getSla().getResolvedAt();
    }

    public Instant getAcknowledgedAt() {
        return getSla().getRespondedAt();
    }

    public List<IncidentNote> getNotes() {
        return Collections.unmodifiableList(notes);
    }
}
