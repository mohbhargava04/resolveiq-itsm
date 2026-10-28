package com.resolveiq.priority;

import com.resolveiq.incident.domain.Priority;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Default algorithm: a weighted multiplicative score.
 *
 * <pre>
 *   score = Impact(1-4) × Urgency(1-4) × Criticality(1-4) × UserMultiplier
 *
 *   UserMultiplier: ≤10 users → 1.0, ≤100 → 1.25, ≤1000 → 1.5, &gt;1000 → 2.0
 *
 *   P1 if score ≥ 48 | P2 if ≥ 24 | P3 if ≥ 10 | otherwise P4      (maximum score = 128)
 * </pre>
 *
 * <p>Multiplication (rather than addition) means one low factor pulls the result down sharply: a
 * total outage of a low-criticality printer service is not treated like a payment outage. The
 * user multiplier is logarithmic so 5,000 users does not dwarf every other factor.</p>
 *
 * <p>Business rule BR-PRI-3: security-sensitive categories are never below P2.</p>
 */
@Component
public class WeightedScoringPriorityStrategy implements PriorityStrategy {

    public static final String KEY = "WEIGHTED";
    static final double P1_THRESHOLD = 48;
    static final double P2_THRESHOLD = 24;
    static final double P3_THRESHOLD = 10;

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String displayName() {
        return "Weighted scoring";
    }

    @Override
    public String description() {
        return "Impact × Urgency × Business criticality × affected-user multiplier; thresholds 48/24/10";
    }

    @Override
    public PriorityAssessment assess(PriorityContext c) {
        double multiplier = userMultiplier(c.affectedUsers());
        double score = c.impact().weight() * c.urgency().weight() * c.criticality().weight() * multiplier;

        List<String> factors = new ArrayList<>();
        factors.add("Impact " + c.impact() + " (" + c.impact().weight() + ")");
        factors.add("Urgency " + c.urgency() + " (" + c.urgency().weight() + ")");
        factors.add("Service criticality " + c.criticality() + " (" + c.criticality().weight() + ")");
        factors.add(c.affectedUsers() + " affected users (×" + multiplier + ")");
        factors.add(String.format("Score %.1f", score));

        Priority priority = score >= P1_THRESHOLD ? Priority.P1
                : score >= P2_THRESHOLD ? Priority.P2
                : score >= P3_THRESHOLD ? Priority.P3
                : Priority.P4;

        if (c.securitySensitive() && Priority.P2.isMoreUrgentThan(priority)) {
            priority = Priority.P2;
            factors.add("Raised to P2: security incidents are never lower than P2");
        }
        return new PriorityAssessment(priority, score, displayName(), factors);
    }

    static double userMultiplier(int affectedUsers) {
        if (affectedUsers <= 10) {
            return 1.0;
        }
        if (affectedUsers <= 100) {
            return 1.25;
        }
        if (affectedUsers <= 1000) {
            return 1.5;
        }
        return 2.0;
    }
}
