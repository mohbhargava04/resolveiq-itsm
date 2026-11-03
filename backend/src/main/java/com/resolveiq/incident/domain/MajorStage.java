package com.resolveiq.incident.domain;

/** Public stages of a major incident, as shown on status pages. */
public enum MajorStage {
    INVESTIGATING("Investigating"),
    IDENTIFIED("Cause identified"),
    MONITORING("Fix applied, monitoring"),
    RESOLVED("Resolved");

    private final String label;

    MajorStage(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
