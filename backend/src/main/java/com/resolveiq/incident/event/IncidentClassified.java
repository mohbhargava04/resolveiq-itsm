package com.resolveiq.incident.event;

import com.resolveiq.incident.domain.Actor;
import com.resolveiq.incident.domain.Incident;

import java.time.Instant;

/** The incident's category was set (manually or by the classifier). */
public record IncidentClassified(Incident incident, Actor actor, Instant occurredAt, String categoryCode, String categoryName, boolean automatic, Double confidence) implements IncidentEvent {
}
