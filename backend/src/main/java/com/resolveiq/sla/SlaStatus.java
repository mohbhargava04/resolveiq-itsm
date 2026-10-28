package com.resolveiq.sla;

/** Health of an incident against its SLA, as shown in filters and dashboards. */
public enum SlaStatus {
    /** Not yet started (incident not classified). */
    NOT_STARTED,
    /** Running, well within target. */
    ON_TRACK,
    /** Running, past the warning threshold. */
    AT_RISK,
    /** Clock stopped while waiting on the reporter. */
    PAUSED,
    /** A response or resolution target was missed. */
    BREACHED,
    /** Resolved within target. */
    MET
}
