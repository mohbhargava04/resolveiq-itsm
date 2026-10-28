package com.resolveiq.incident;

import com.resolveiq.common.exception.IncidentNotFoundException;
import com.resolveiq.incident.domain.Incident;
import com.resolveiq.user.domain.Role;
import com.resolveiq.user.domain.User;
import org.springframework.stereotype.Component;

/**
 * Row-level authorisation: employees may only see incidents they reported; IT staff see all.
 * An employee asking for someone else's incident gets "not found" rather than "forbidden", so
 * incident numbers of other people cannot be probed.
 */
@Component
public class IncidentAccessPolicy {

    public boolean canView(User viewer, Incident incident) {
        if (viewer.getRole() != Role.EMPLOYEE) {
            return true;
        }
        return incident.getReporter().getId().equals(viewer.getId());
    }

    public void requireView(User viewer, Incident incident) {
        if (!canView(viewer, incident)) {
            throw new IncidentNotFoundException(incident.getNumber());
        }
    }

    /** Employees never see internal work notes. */
    public boolean canSeeInternalNotes(User viewer) {
        return viewer.getRole() != Role.EMPLOYEE;
    }
}
