package com.resolveiq.incident.domain;

import com.resolveiq.user.domain.Technician;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.resolveiq.incident.domain.IncidentStatus.ACKNOWLEDGED;
import static com.resolveiq.incident.domain.IncidentStatus.ASSIGNED;
import static com.resolveiq.incident.domain.IncidentStatus.AWAITING_INFO;
import static com.resolveiq.incident.domain.IncidentStatus.CLASSIFIED;
import static com.resolveiq.incident.domain.IncidentStatus.CLOSED;
import static com.resolveiq.incident.domain.IncidentStatus.IN_PROGRESS;
import static com.resolveiq.incident.domain.IncidentStatus.NEW;
import static com.resolveiq.incident.domain.IncidentStatus.RESOLVED;
import static com.resolveiq.incident.domain.IncidentStatus.VERIFIED;

/**
 * Registry of the stateless, shared state objects (one flyweight instance per state) plus the
 * concrete state classes themselves.
 *
 * <pre>
 * NEW ─► CLASSIFIED ─► ASSIGNED ─► ACKNOWLEDGED ─► IN_PROGRESS ─► RESOLVED ─► VERIFIED ─► CLOSED
 *  │         │            ▲  │           │            │  ▲  │          │
 *  │         │            └──┴───────────┴────────────┘  │  ▼          │ (reopen)
 *  └─────────┴──► CLOSED (as duplicate)          AWAITING_INFO          └──► IN_PROGRESS
 * </pre>
 */
public final class IncidentStates {

    private static final Map<IncidentStatus, IncidentState> STATES = new EnumMap<>(IncidentStatus.class);

    static {
        register(new NewState());
        register(new ClassifiedState());
        register(new AssignedState());
        register(new AcknowledgedState());
        register(new InProgressState());
        register(new AwaitingInfoState());
        register(new ResolvedState());
        register(new VerifiedState());
        register(new ClosedState());
    }

    private IncidentStates() {
    }

    private static void register(IncidentState state) {
        STATES.put(state.status(), state);
    }

    public static IncidentState of(IncidentStatus status) {
        IncidentState state = STATES.get(status);
        if (state == null) {
            throw new IllegalStateException("No state object registered for " + status);
        }
        return state;
    }

    public static Map<IncidentStatus, Set<IncidentStatus>> transitionTable() {
        Map<IncidentStatus, Set<IncidentStatus>> table = new EnumMap<>(IncidentStatus.class);
        STATES.forEach((k, v) -> table.put(k, v.allowedTransitions()));
        return Collections.unmodifiableMap(table);
    }

    // ==========================================================================================

    static final class NewState extends AbstractIncidentState {
        @Override
        public IncidentStatus status() {
            return NEW;
        }

        @Override
        public Set<IncidentStatus> allowedTransitions() {
            return EnumSet.of(CLASSIFIED, CLOSED);
        }

        @Override
        public void classify(Incident incident, Classification classification, Actor actor, Instant at) {
            incident.applyClassification(classification, actor, at);
            incident.moveTo(CLASSIFIED, actor, at, "Classified as " + classification.category().getName()
                    + " with priority " + classification.assessment().priority());
        }

        @Override
        public void closeAsDuplicate(Incident incident, Incident parent, Actor actor, Instant at) {
            incident.applyDuplicateClosure(parent, actor, at);
        }

        @Override
        public boolean allowsDetailEdits() {
            return true;
        }

        @Override
        public boolean allowsEscalation() {
            return true;
        }
    }

    static final class ClassifiedState extends AbstractIncidentState {
        @Override
        public IncidentStatus status() {
            return CLASSIFIED;
        }

        @Override
        public Set<IncidentStatus> allowedTransitions() {
            return EnumSet.of(ASSIGNED, CLOSED);
        }

        @Override
        public void assign(Incident incident, Technician technician, String strategy, Actor actor, Instant at) {
            incident.applyAssignment(technician, strategy, actor, at);
            incident.moveTo(ASSIGNED, actor, at, "Assigned to " + technician.getFullName());
        }

        @Override
        public void closeAsDuplicate(Incident incident, Incident parent, Actor actor, Instant at) {
            incident.applyDuplicateClosure(parent, actor, at);
        }

        @Override
        public boolean allowsDetailEdits() {
            return true;
        }

        @Override
        public boolean allowsReprioritisation() {
            return true;
        }

        @Override
        public boolean allowsEscalation() {
            return true;
        }
    }

    /** Shared behaviour of the "being worked on" states: re-assignment, edits, escalation. */
    abstract static class WorkInProgressState extends AbstractIncidentState {
        @Override
        public void assign(Incident incident, Technician technician, String strategy, Actor actor, Instant at) {
            incident.applyAssignment(technician, strategy, actor, at);
            // A new owner must acknowledge again, so the incident always returns to ASSIGNED.
            incident.moveTo(ASSIGNED, actor, at, "Re-assigned to " + technician.getFullName());
        }

        @Override
        public boolean allowsDetailEdits() {
            return true;
        }

        @Override
        public boolean allowsReprioritisation() {
            return true;
        }

        @Override
        public boolean allowsEscalation() {
            return true;
        }
    }

    static final class AssignedState extends WorkInProgressState {
        @Override
        public IncidentStatus status() {
            return ASSIGNED;
        }

        @Override
        public Set<IncidentStatus> allowedTransitions() {
            return EnumSet.of(ACKNOWLEDGED, ASSIGNED, CLOSED);
        }

        @Override
        public void acknowledge(Incident incident, Actor actor, Instant at) {
            incident.applyAcknowledgement(actor, at);
            incident.moveTo(ACKNOWLEDGED, actor, at, "Acknowledged by " + actor.name());
        }

        @Override
        public void closeAsDuplicate(Incident incident, Incident parent, Actor actor, Instant at) {
            incident.applyDuplicateClosure(parent, actor, at);
        }
    }

    static final class AcknowledgedState extends WorkInProgressState {
        @Override
        public IncidentStatus status() {
            return ACKNOWLEDGED;
        }

        @Override
        public Set<IncidentStatus> allowedTransitions() {
            return EnumSet.of(IN_PROGRESS, ASSIGNED);
        }

        @Override
        public void startWork(Incident incident, Actor actor, Instant at) {
            incident.moveTo(IN_PROGRESS, actor, at, "Investigation started");
        }
    }

    static final class InProgressState extends WorkInProgressState {
        @Override
        public IncidentStatus status() {
            return IN_PROGRESS;
        }

        @Override
        public Set<IncidentStatus> allowedTransitions() {
            return EnumSet.of(AWAITING_INFO, RESOLVED, ASSIGNED);
        }

        @Override
        public void requestInformation(Incident incident, String question, Actor actor, Instant at) {
            incident.applyInformationRequest(question, actor, at);
            incident.moveTo(AWAITING_INFO, actor, at, "Waiting for information from the reporter");
        }

        @Override
        public void resolve(Incident incident, Resolution resolution, Actor actor, Instant at) {
            incident.applyResolution(resolution, actor, at);
            incident.moveTo(RESOLVED, actor, at, resolution.getSummary());
        }
    }

    static final class AwaitingInfoState extends AbstractIncidentState {
        @Override
        public IncidentStatus status() {
            return AWAITING_INFO;
        }

        @Override
        public Set<IncidentStatus> allowedTransitions() {
            return EnumSet.of(IN_PROGRESS);
        }

        @Override
        public void provideInformation(Incident incident, String answer, Actor actor, Instant at) {
            incident.applyInformationResponse(answer, actor, at);
            incident.moveTo(IN_PROGRESS, actor, at, "Reporter provided the requested information");
        }

        @Override
        public boolean allowsDetailEdits() {
            return true;
        }

        @Override
        public boolean allowsReprioritisation() {
            return true;
        }

        @Override
        public boolean allowsEscalation() {
            return true;
        }
    }

    static final class ResolvedState extends AbstractIncidentState {
        @Override
        public IncidentStatus status() {
            return RESOLVED;
        }

        @Override
        public Set<IncidentStatus> allowedTransitions() {
            return EnumSet.of(VERIFIED, IN_PROGRESS);
        }

        @Override
        public void verify(Incident incident, Actor actor, Instant at) {
            incident.moveTo(VERIFIED, actor, at, "Resolution confirmed");
        }

        @Override
        public void reopen(Incident incident, String reason, Actor actor, Instant at) {
            incident.applyReopen(reason, actor, at);
            incident.moveTo(IN_PROGRESS, actor, at, "Reopened: " + reason);
        }

        @Override
        public boolean acceptsFeedback() {
            return true;
        }
    }

    static final class VerifiedState extends AbstractIncidentState {
        @Override
        public IncidentStatus status() {
            return VERIFIED;
        }

        @Override
        public Set<IncidentStatus> allowedTransitions() {
            return EnumSet.of(CLOSED);
        }

        @Override
        public void close(Incident incident, String note, Actor actor, Instant at) {
            incident.applyClosure(note, at);
            incident.moveTo(CLOSED, actor, at, note == null || note.isBlank() ? "Incident closed" : note);
        }

        @Override
        public boolean acceptsFeedback() {
            return true;
        }
    }

    /** Terminal state: nothing is allowed except leaving feedback. */
    static final class ClosedState extends AbstractIncidentState {
        @Override
        public IncidentStatus status() {
            return CLOSED;
        }

        @Override
        public Set<IncidentStatus> allowedTransitions() {
            return EnumSet.noneOf(IncidentStatus.class);
        }

        @Override
        public boolean acceptsNotes() {
            return false;
        }

        @Override
        public boolean acceptsFeedback() {
            return true;
        }
    }
}
