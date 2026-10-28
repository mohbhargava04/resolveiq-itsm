package com.resolveiq.assignment;

import com.resolveiq.incident.domain.Priority;

import java.time.Instant;

/** The incident characteristics an assignment strategy may consider. */
public record AssignmentRequest(String incidentNumber, String categoryCode, String serviceCode, Priority priority,
                                Instant requestedAt) {
}
