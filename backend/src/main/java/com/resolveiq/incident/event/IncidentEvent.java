package com.resolveiq.incident.event;

import com.resolveiq.incident.domain.Actor;
import com.resolveiq.incident.domain.Incident;

import java.time.Instant;

/**
 * Domain events raised by the {@link Incident} aggregate whenever something noteworthy happens.
 *
 * <p>A <b>sealed interface</b> (Java 17+) closes the set of event types, so observers can use an
 * exhaustive {@code switch} with pattern matching and the compiler tells us if a new event type
 * is not handled. Each event is an immutable record.</p>
 *
 * <p>Events are the "subject notifications" of the <b>Observer pattern</b>: the incident never
 * calls the audit log or the notification service directly; it only records what happened.</p>
 */
public sealed interface IncidentEvent permits
        IncidentReported,
        IncidentClassified,
        PriorityAssessed,
        IncidentAssigned,
        IncidentStatusChanged,
        NoteAdded,
        SlaWarningRaised,
        SlaBreached,
        IncidentEscalated,
        DuplicateSuspected,
        LinkedAsDuplicate,
        FeedbackSubmitted,
        AssignmentFailed,
        IncidentUpdated {

    Incident incident();

    Actor actor();

    Instant occurredAt();

    /** Short type name used in logs, audit and notification records. */
    default String type() {
        return getClass().getSimpleName();
    }
}
