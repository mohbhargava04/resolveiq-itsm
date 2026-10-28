package com.resolveiq.assignment;

import com.resolveiq.user.domain.Technician;

/**
 * A technician plus the live statistics strategies need. Computed once per assignment request by
 * {@link AssignmentService}, so strategies themselves stay free of database access.
 *
 * @param activeIncidents     incidents currently assigned and not yet resolved
 * @param resolvedOnService   incidents this technician has resolved on the incident's service
 * @param resolvedInCategory  incidents this technician has resolved in the incident's category
 */
public record TechnicianProfile(Technician technician, int activeIncidents, int resolvedOnService,
                                int resolvedInCategory) {

    public int capacity() {
        return technician.getMaxActiveIncidents();
    }

    /** 0.0 = idle, 1.0 = at capacity. */
    public double utilisation() {
        return Math.min(1.0, activeIncidents / (double) capacity());
    }

    public boolean hasCapacity() {
        return activeIncidents < capacity();
    }
}
