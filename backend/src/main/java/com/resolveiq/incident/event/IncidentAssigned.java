package com.resolveiq.incident.event;

import com.resolveiq.incident.domain.Actor;
import com.resolveiq.incident.domain.Incident;

import java.time.Instant;

/** A technician was assigned or the incident was re-assigned. */
public record IncidentAssigned(Incident incident, Actor actor, Instant occurredAt, String previousAssignee, Long assigneeId, String assigneeName, String strategy) implements IncidentEvent {
}
