package com.resolveiq.incident.event;

import com.resolveiq.incident.domain.Actor;
import com.resolveiq.incident.domain.Incident;
import com.resolveiq.incident.domain.Priority;

import java.time.Instant;

/** Priority was (re)calculated. {@code previous} is null on first assessment. */
public record PriorityAssessed(Incident incident, Actor actor, Instant occurredAt, Priority previous, Priority current, double score, String rationale, String strategy, String reason) implements IncidentEvent {
}
