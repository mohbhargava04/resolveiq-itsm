package com.resolveiq.incident.event;

import com.resolveiq.incident.domain.Actor;
import com.resolveiq.incident.domain.Incident;

import java.time.Instant;

/** A new incident was reported. */
public record IncidentReported(Incident incident, Actor actor, Instant occurredAt) implements IncidentEvent {
}
