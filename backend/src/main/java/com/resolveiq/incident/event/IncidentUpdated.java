package com.resolveiq.incident.event;

import com.resolveiq.incident.domain.Actor;
import com.resolveiq.incident.domain.Incident;

import java.time.Instant;

/** Descriptive fields of the incident were edited. */
public record IncidentUpdated(Incident incident, Actor actor, Instant occurredAt, String changes) implements IncidentEvent {
}
