package com.resolveiq.priority;

import com.resolveiq.catalog.Criticality;
import com.resolveiq.incident.domain.Priority;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Alternative, rule-based algorithm modelled on the ITIL impact/urgency matrix, adjusted by one
 * level for business-critical (raise) or low-criticality (lower) services.
 *
 * <pre>
 *                 Urgency:  CRITICAL  HIGH  MEDIUM  LOW
 *   Impact CRITICAL            P1      P1     P2     P3
 *          HIGH                P1      P2     P3     P3
 *          MEDIUM              P2      P3     P3     P4
 *          LOW                 P3      P3     P4     P4
 * </pre>
 */
@Component
public class ItilMatrixPriorityStrategy implements PriorityStrategy {

    public static final String KEY = "ITIL_MATRIX";

    /** Rows = impact (LOW..CRITICAL), columns = urgency (LOW..CRITICAL). */
    private static final Priority[][] MATRIX = {
            {Priority.P4, Priority.P4, Priority.P3, Priority.P3},
            {Priority.P4, Priority.P3, Priority.P3, Priority.P2},
            {Priority.P3, Priority.P3, Priority.P2, Priority.P1},
            {Priority.P3, Priority.P2, Priority.P1, Priority.P1},
    };

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String displayName() {
        return "ITIL impact/urgency matrix";
    }

    @Override
    public String description() {
        return "Classic ITIL 4×4 matrix, raised one level for business-critical services and lowered for low-criticality ones";
    }

    @Override
    public PriorityAssessment assess(PriorityContext c) {
        Priority base = MATRIX[c.impact().ordinal()][c.urgency().ordinal()];
        List<String> factors = new ArrayList<>();
        factors.add("Matrix(" + c.impact() + " impact, " + c.urgency() + " urgency) = " + base);

        Priority adjusted = base;
        if (c.criticality() == Criticality.CRITICAL) {
            adjusted = base.raise();
            factors.add("Business-critical service: raised to " + adjusted);
        } else if (c.criticality() == Criticality.LOW) {
            adjusted = base.lower();
            factors.add("Low-criticality service: lowered to " + adjusted);
        }
        if (c.securitySensitive() && Priority.P2.isMoreUrgentThan(adjusted)) {
            adjusted = Priority.P2;
            factors.add("Raised to P2: security incidents are never lower than P2");
        }
        double score = (4 - adjusted.ordinal()) * 25.0; // 100 / 75 / 50 / 25 for display
        return new PriorityAssessment(adjusted, score, displayName(), factors);
    }
}
