package com.resolveiq.incident.domain;

/** How quickly the business needs a fix. */
public enum Urgency {
    LOW(1, "Can wait; a workaround exists"),
    MEDIUM(2, "Needed within the working day"),
    HIGH(3, "Needed within hours; work is blocked"),
    CRITICAL(4, "Needed immediately; revenue or safety at stake");

    private final int weight;
    private final String description;

    Urgency(int weight, String description) {
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
