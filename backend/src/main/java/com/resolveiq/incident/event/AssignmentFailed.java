package com.resolveiq.incident.event;

import com.resolveiq.incident.domain.Actor;
import com.resolveiq.incident.domain.Incident;

import java.time.Instant;

/** Automatic assignment could not find a technician. */
public record AssignmentFailed(Incident incident, Actor actor, Instant occurredAt, String reason) implements IncidentEvent {
}
