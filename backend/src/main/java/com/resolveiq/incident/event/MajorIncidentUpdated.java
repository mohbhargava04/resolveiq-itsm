package com.resolveiq.incident.event;

import com.resolveiq.incident.domain.Actor;
import com.resolveiq.incident.domain.Incident;
import com.resolveiq.incident.domain.MajorStage;

import java.time.Instant;

/** A status update was posted on a major incident. */
public record MajorIncidentUpdated(Incident incident, Actor actor, Instant occurredAt, MajorStage stage, String message) implements IncidentEvent {
}
