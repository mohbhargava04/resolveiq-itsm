package com.resolveiq.assignment;

import com.resolveiq.incident.domain.Priority;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * <b>Intelligent feature D - Smart Technician Recommendation.</b> A multi-factor weighted score:
 *
 * <pre>
 *   score =  40 × skill match (1 if skilled in the category, 0 otherwise)
 *          + 25 × (1 − utilisation)
 *          + 20 × min(resolvedOnService + resolvedInCategory, 10) / 10
 *          + 15 × seniority fit for the priority
 * </pre>
 *
 * Seniority fit rewards senior engineers on P1/P2 and juniors on P3/P4 (so seniors stay free for
 * critical work and juniors build experience). Every candidate comes with a plain-English explanation.
 */
@Component
public class SmartAssignmentStrategy extends AbstractAssignmentStrategy {

    public static final String KEY = "SMART";

    static final double W_SKILL = 40;
    static final double W_LOAD = 25;
    static final double W_HISTORY = 20;
    static final double W_SENIORITY = 15;

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String displayName() {
        return "Smart recommendation";
    }

    @Override
    public String description() {
        return "Weighted blend of skill match (40), availability (25), experience with this service/category (20) "
                + "and seniority fit for the priority (15)";
    }

    @Override
    protected Score score(AssignmentRequest request, TechnicianProfile p) {
        List<String> reasons = new ArrayList<>();
        boolean skilled = p.technician().hasSkill(request.categoryCode());
        double skill = skilled ? 1 : 0;
        reasons.add(skilled ? "Skilled in " + request.categoryCode() : "No " + request.categoryCode() + " skill");

        double load = 1 - p.utilisation();
        reasons.add(workloadText(p));

        int history = p.resolvedOnService() + p.resolvedInCategory();
        double experience = Math.min(history, 10) / 10.0;
        if (history > 0) {
            reasons.add(p.resolvedOnService() + " resolved on this service, " + p.resolvedInCategory()
                    + " in this category");
        }

        double seniorityFit = seniorityFit(request.priority(), p.technician().getExperienceLevel().weight());
        reasons.add(p.technician().getExperienceLevel() + " for a " + (request.priority() == null ? "?" : request.priority())
                + " incident");

        double score = W_SKILL * skill + W_LOAD * load + W_HISTORY * experience + W_SENIORITY * seniorityFit;
        return new Score(score, reasons);
    }

    /** 1.0 = perfect fit. Seniority weight is 1 (junior) .. 4 (expert). */
    static double seniorityFit(Priority priority, int seniority) {
        if (priority == null) {
            return 0.5;
        }
        return priority.isHighPriority()
                ? (seniority - 1) / 3.0          // P1/P2: more senior is better
                : 1 - (seniority - 1) / 3.0 * 0.5; // P3/P4: juniors preferred, seniors still acceptable
    }
}
