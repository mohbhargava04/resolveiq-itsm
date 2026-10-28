package com.resolveiq.incident.event;

import com.resolveiq.incident.domain.Actor;
import com.resolveiq.incident.domain.Incident;

import java.time.Instant;

/** The similarity engine thinks this incident may duplicate another one. */
public record DuplicateSuspected(Incident incident, Actor actor, Instant occurredAt, String candidateNumber, double score) implements IncidentEvent {
}
