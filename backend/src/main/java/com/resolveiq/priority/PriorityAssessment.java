package com.resolveiq.priority;

import com.resolveiq.incident.domain.Priority;

import java.util.List;

/**
 * Result of a priority calculation, including an explanation of every factor so the UI can show
 * "why is this P1?" (explainable prioritisation).
 */
public record PriorityAssessment(Priority priority, double score, String strategy, List<String> factors) {

    public PriorityAssessment {
        factors = List.copyOf(factors);
    }

    public String rationale() {
        return String.join("; ", factors);
    }
}
