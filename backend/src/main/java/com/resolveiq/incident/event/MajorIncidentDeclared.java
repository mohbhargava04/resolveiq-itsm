package com.resolveiq.incident.event;

import com.resolveiq.incident.domain.Actor;
import com.resolveiq.incident.domain.Incident;

import java.time.Instant;

/** A supervisor declared this a major incident; status updates go to everyone affected. */
public record MajorIncidentDeclared(Incident incident, Actor actor, Instant occurredAt, String summary) implements IncidentEvent {
}
