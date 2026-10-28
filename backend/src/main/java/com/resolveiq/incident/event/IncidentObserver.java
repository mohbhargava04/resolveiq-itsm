package com.resolveiq.incident.event;

/**
 * <b>Observer pattern</b> - the observer side. Anything that wants to react to incident activity
 * (audit trail, notifications, live dashboards, the knowledge base) implements this interface and
 * is registered with the {@link IncidentEventPublisher}. The incident code never knows who is
 * listening, so new reactions can be added without touching it.
 */
public interface IncidentObserver {

    void onIncidentEvent(IncidentEvent event);

    /** Lower runs first. The audit trail runs first so it is never skipped. */
    default int order() {
        return 100;
    }

    /**
     * Critical observers (the audit trail) propagate failures so the whole transaction rolls back;
     * non-critical ones (notifications) must never break the business operation.
     */
    default boolean isCritical() {
        return false;
    }
}
