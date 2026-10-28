package com.resolveiq.incident.domain;

import com.resolveiq.catalog.Category;
import com.resolveiq.priority.PriorityAssessment;
import com.resolveiq.sla.SlaPolicy;

import java.util.Objects;

/**
 * Everything decided during classification: category (manual or AI-suggested), calculated priority
 * and the SLA policy that applies. Bundled as one value object so the NEW -> CLASSIFIED transition
 * is atomic.
 */
public record Classification(Category category, boolean automatic, Double confidence,
                             PriorityAssessment assessment, SlaPolicy slaPolicy) {

    public Classification {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(assessment, "assessment");
        Objects.requireNonNull(slaPolicy, "slaPolicy");
    }
}
