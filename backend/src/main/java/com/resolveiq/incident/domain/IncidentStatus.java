package com.resolveiq.incident.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Persisted name of each lifecycle state. The <em>behaviour</em> of each state lives in the
 * {@link IncidentState} implementations; this enum is only the label stored in the database.
 */
public enum IncidentStatus {
    NEW,
    CLASSIFIED,
    ASSIGNED,
    ACKNOWLEDGED,
    IN_PROGRESS,
    AWAITING_INFO,
    RESOLVED,
    VERIFIED,
    CLOSED;

    /** States in which the incident still needs work from IT and the SLA clock matters. */
    public static final Set<IncidentStatus> ACTIVE =
            EnumSet.of(NEW, CLASSIFIED, ASSIGNED, ACKNOWLEDGED, IN_PROGRESS, AWAITING_INFO);

    /** States that count towards a technician's workload. */
    public static final Set<IncidentStatus> WORKLOAD =
            EnumSet.of(ASSIGNED, ACKNOWLEDGED, IN_PROGRESS, AWAITING_INFO);

    /** States after IT has delivered a fix. */
    public static final Set<IncidentStatus> DONE = EnumSet.of(RESOLVED, VERIFIED, CLOSED);

    public boolean isActive() {
        return ACTIVE.contains(this);
    }

    public String label() {
        String s = name().replace('_', ' ').toLowerCase();
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
