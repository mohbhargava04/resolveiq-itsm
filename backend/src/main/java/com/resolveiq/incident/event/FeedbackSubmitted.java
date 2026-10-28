package com.resolveiq.incident.event;

import com.resolveiq.incident.domain.Actor;
import com.resolveiq.incident.domain.Incident;

import java.time.Instant;

/** The reporter rated the resolution. */
public record FeedbackSubmitted(Incident incident, Actor actor, Instant occurredAt, int rating, String comment) implements IncidentEvent {
}
