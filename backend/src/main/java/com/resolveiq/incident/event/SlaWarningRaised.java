package com.resolveiq.incident.event;

import com.resolveiq.incident.domain.Actor;
import com.resolveiq.incident.domain.Incident;
import com.resolveiq.sla.SlaTarget;

import java.time.Instant;

/** An SLA target is about to be missed. */
public record SlaWarningRaised(Incident incident, Actor actor, Instant occurredAt, SlaTarget target, Instant dueAt) implements IncidentEvent {
}
