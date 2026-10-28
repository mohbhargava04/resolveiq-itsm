package com.resolveiq.incident.domain;

import com.resolveiq.user.domain.Technician;

import java.time.Instant;
import java.util.Set;

/**
 * <b>State pattern</b>: one implementation per lifecycle state. The {@link Incident} delegates every
 * lifecycle operation to the object representing its current state, so the rules "what may happen
 * next" live in exactly one class per state instead of in a giant switch statement scattered
 * across services.
 *
 * <p>Operations that are illegal in a state are rejected by {@link AbstractIncidentState} with an
 * {@link com.resolveiq.common.exception.InvalidStateTransitionException}; concrete states override
 * only the operations they permit. Adding a new state (e.g. ON_HOLD) means adding one class and
 * one registry entry.</p>
 */
public interface IncidentState {

    IncidentStatus status();

    /** States reachable from this one (used for documentation, UI hints and tests). */
    Set<IncidentStatus> allowedTransitions();

    // ---- lifecycle transitions ---------------------------------------------------------------

    void classify(Incident incident, Classification classification, Actor actor, Instant at);

    void assign(Incident incident, Technician technician, String strategy, Actor actor, Instant at);

    void acknowledge(Incident incident, Actor actor, Instant at);

    void startWork(Incident incident, Actor actor, Instant at);

    void requestInformation(Incident incident, String question, Actor actor, Instant at);

    void provideInformation(Incident incident, String answer, Actor actor, Instant at);

    void resolve(Incident incident, Resolution resolution, Actor actor, Instant at);

    void verify(Incident incident, Actor actor, Instant at);

    void reopen(Incident incident, String reason, Actor actor, Instant at);

    void close(Incident incident, String note, Actor actor, Instant at);

    void closeAsDuplicate(Incident incident, Incident parent, Actor actor, Instant at);

    // ---- capabilities that do not change state -----------------------------------------------

    /** Title/description/impact etc. may still be edited. */
    boolean allowsDetailEdits();

    /** Priority may be recalculated or overridden. */
    boolean allowsReprioritisation();

    boolean acceptsNotes();

    boolean allowsEscalation();

    boolean acceptsFeedback();
}
