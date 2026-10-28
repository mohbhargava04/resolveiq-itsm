package com.resolveiq.assignment;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Takes turns: whoever received an incident longest ago goes next. Stateless - the "turn" is derived
 * from each technician's last assignment time, so it survives restarts and works across servers.
 * Score = minutes waited since the last assignment (never assigned = top of the queue).
 */
@Component
public class RoundRobinAssignmentStrategy extends AbstractAssignmentStrategy {

    public static final String KEY = "ROUND_ROBIN";
    static final double NEVER_ASSIGNED_SCORE = 1_000_000;

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String displayName() {
        return "Round-robin";
    }

    @Override
    public String description() {
        return "Rotates through available technicians; the one who waited longest since their last assignment goes next";
    }

    @Override
    protected Score score(AssignmentRequest request, TechnicianProfile p) {
        Instant last = p.technician().getLastAssignedAt();
        if (last == null) {
            return new Score(NEVER_ASSIGNED_SCORE, List.of("Has not been assigned an incident yet"));
        }
        long minutes = Math.max(0, Duration.between(last, request.requestedAt()).toMinutes());
        return new Score(Math.min(minutes, NEVER_ASSIGNED_SCORE - 1),
                List.of("Waiting " + minutes + " min since last assignment"));
    }
}
