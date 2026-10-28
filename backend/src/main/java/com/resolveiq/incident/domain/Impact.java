package com.resolveiq.incident.domain;

/** How widely the business is affected. */
public enum Impact {
    LOW(1, "Single user or minor inconvenience"),
    MEDIUM(2, "A team or department is affected"),
    HIGH(3, "Several departments or a key business process"),
    CRITICAL(4, "Organisation-wide or customer-facing outage");

    private final int weight;
    private final String description;

    Impact(int weight, String description) {
        this.weight = weight;
        this.description = description;
    }

    public int weight() {
        return weight;
    }

    public String description() {
        return description;
    }
}
