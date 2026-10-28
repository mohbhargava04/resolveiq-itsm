package com.resolveiq.assignment;

import org.springframework.stereotype.Component;

import java.util.List;

/** Only technicians skilled in the incident's category; among them, the least busy wins. */
@Component
public class SkillBasedAssignmentStrategy extends AbstractAssignmentStrategy {

    public static final String KEY = "SKILL";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String displayName() {
        return "Skill-based";
    }

    @Override
    public String description() {
        return "Restricts to technicians skilled in the incident category, then prefers the lowest workload";
    }

    @Override
    protected boolean isEligible(AssignmentRequest request, TechnicianProfile p) {
        return p.technician().hasSkill(request.categoryCode());
    }

    @Override
    protected Score score(AssignmentRequest request, TechnicianProfile p) {
        double score = 100 - p.utilisation() * 50;
        return new Score(score, List.of("Skilled in " + request.categoryCode(), workloadText(p)));
    }
}
