package com.resolveiq.catalog;

/** Business criticality of an IT service; the third factor of the priority formula. */
public enum Criticality {
    LOW(1, "Low"),
    MEDIUM(2, "Medium"),
    HIGH(3, "High"),
    CRITICAL(4, "Business critical");

    private final int weight;
    private final String label;

    Criticality(int weight, String label) {
        this.weight = weight;
        this.label = label;
    }

    public int weight() {
        return weight;
    }

    public String label() {
        return label;
    }
}
