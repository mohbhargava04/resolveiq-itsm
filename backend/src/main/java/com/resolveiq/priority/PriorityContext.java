package com.resolveiq.priority;

import com.resolveiq.catalog.Criticality;
import com.resolveiq.incident.domain.Impact;
import com.resolveiq.incident.domain.Urgency;

/**
 * The inputs every priority strategy may use. Strategies receive plain values rather than the
 * incident entity, which keeps them pure functions that are trivial to unit-test.
 */
public record PriorityContext(Impact impact, Urgency urgency, Criticality criticality, int affectedUsers,
                              boolean securitySensitive, String serviceName, String categoryCode) {
}
