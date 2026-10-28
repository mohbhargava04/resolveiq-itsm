package com.resolveiq.user.domain;

/** Seniority of a technician; used by the experience-based and smart assignment strategies. */
public enum ExperienceLevel {
    JUNIOR(1),
    MID(2),
    SENIOR(3),
    EXPERT(4);

    private final int weight;

    ExperienceLevel(int weight) {
        this.weight = weight;
    }

    public int weight() {
        return weight;
    }
}
