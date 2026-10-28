package com.resolveiq.assignment;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Prefers seniority and proven experience with the affected service - useful for critical
 * incidents where getting it right first time matters more than spreading load.
 */
@Component
public class ExperienceBasedAssignmentStrategy extends AbstractAssignmentStrategy {

    public static final String KEY = "EXPERIENCE";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String displayName() {
        return "Experience-based";
    }

    @Override
    public String description() {
        return "Ranks by seniority and number of incidents previously resolved on the same service";
    }

    @Override
    protected Score score(AssignmentRequest request, TechnicianProfile p) {
        int seniority = p.technician().getExperienceLevel().weight();
        double score = seniority * 20 + Math.min(p.resolvedOnService(), 10) * 4;
        return new Score(score, List.of(p.technician().getExperienceLevel() + " engineer",
                p.resolvedOnService() + " past incidents on " + request.serviceCode()));
    }
}
