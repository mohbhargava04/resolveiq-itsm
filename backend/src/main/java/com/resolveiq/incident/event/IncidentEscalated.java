package com.resolveiq.incident.event;

import com.resolveiq.incident.domain.Actor;
import com.resolveiq.incident.domain.EscalationLevel;
import com.resolveiq.incident.domain.Incident;

import java.time.Instant;

/** The incident moved up the escalation hierarchy. */
public record IncidentEscalated(Incident incident, Actor actor, Instant occurredAt, EscalationLevel from, EscalationLevel to, Long targetUserId, String targetName, String reason) implements IncidentEvent {
}
