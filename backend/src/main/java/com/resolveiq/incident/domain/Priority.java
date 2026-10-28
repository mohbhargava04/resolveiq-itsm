package com.resolveiq.incident.domain;

/** Incident priority. P1 is the most urgent. */
public enum Priority {
    P1("Critical"),
    P2("High"),
    P3("Medium"),
    P4("Low");

    private final String label;

    Priority(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** One level more urgent (P3 -> P2); P1 stays P1. */
    public Priority raise() {
        return this == P1 ? P1 : values()[ordinal() - 1];
    }

    /** One level less urgent (P2 -> P3); P4 stays P4. */
    public Priority lower() {
        return this == P4 ? P4 : values()[ordinal() + 1];
    }

    public boolean isMoreUrgentThan(Priority other) {
        return ordinal() < other.ordinal();
    }

    /** Returns whichever of the two is more urgent. */
    public static Priority mostUrgent(Priority a, Priority b) {
        return a.isMoreUrgentThan(b) ? a : b;
    }

    public boolean isHighPriority() {
        return this == P1 || this == P2;
    }
}
