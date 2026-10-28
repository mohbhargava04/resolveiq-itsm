package com.resolveiq.incident.event;

import com.resolveiq.incident.domain.Actor;
import com.resolveiq.incident.domain.Incident;
import com.resolveiq.incident.domain.NoteType;

import java.time.Instant;

/** A work-log entry was added. */
public record NoteAdded(Incident incident, Actor actor, Instant occurredAt, NoteType noteType, boolean internal, String excerpt) implements IncidentEvent {
}
