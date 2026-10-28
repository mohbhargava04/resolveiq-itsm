package com.resolveiq.incident.event;

import com.resolveiq.incident.domain.Actor;
import com.resolveiq.incident.domain.Incident;
import com.resolveiq.incident.domain.IncidentStatus;

import java.time.Instant;

/** A lifecycle transition happened. */
public record IncidentStatusChanged(Incident incident, Actor actor, Instant occurredAt, IncidentStatus from, IncidentStatus to, String comment) implements IncidentEvent {
}
