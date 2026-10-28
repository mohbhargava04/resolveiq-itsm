package com.resolveiq.assignment;

import java.util.List;

/**
 * <b>Strategy pattern</b>: interchangeable ways of choosing a technician. The active strategy is a
 * runtime setting, so changing how work is distributed never requires redesigning the application.
 */
public interface AssignmentStrategy {

    String key();

    String displayName();

    String description();

    /**
     * Ranks eligible technicians, best first. Ineligible technicians (off duty, at capacity, or
     * excluded by the strategy's own rules) are not returned.
     */
    List<AssignmentCandidate> rank(AssignmentRequest request, List<TechnicianProfile> pool);
}
