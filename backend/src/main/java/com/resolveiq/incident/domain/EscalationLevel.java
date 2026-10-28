package com.resolveiq.incident.domain;

/** Functional escalation hierarchy: Technician -> Team Lead -> System Administrator -> IT Manager. */
public enum EscalationLevel {
    NONE("Assigned technician"),
    TEAM_LEAD("Team lead"),
    ADMINISTRATOR("System administrator"),
    IT_MANAGER("IT manager");

    private final String label;

    EscalationLevel(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean isTop() {
        return this == IT_MANAGER;
    }

    public EscalationLevel next() {
        return isTop() ? this : values()[ordinal() + 1];
    }

    public boolean isBelow(EscalationLevel other) {
        return ordinal() < other.ordinal();
    }
}
