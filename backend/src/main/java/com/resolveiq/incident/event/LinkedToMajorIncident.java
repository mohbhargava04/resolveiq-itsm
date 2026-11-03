package com.resolveiq.incident.event;

import com.resolveiq.incident.domain.Actor;
import com.resolveiq.incident.domain.Incident;

import java.time.Instant;

/** This incident was linked to a major incident and now receives its updates. */
public record LinkedToMajorIncident(Incident incident, Actor actor, Instant occurredAt, String majorNumber) implements IncidentEvent {
}
