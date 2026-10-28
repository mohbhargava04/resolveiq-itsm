package com.resolveiq.incident.event;

import com.resolveiq.incident.domain.Actor;
import com.resolveiq.incident.domain.Incident;

import java.time.Instant;

/** The incident was confirmed as a duplicate and closed against its parent. */
public record LinkedAsDuplicate(Incident incident, Actor actor, Instant occurredAt, String parentNumber) implements IncidentEvent {
}
