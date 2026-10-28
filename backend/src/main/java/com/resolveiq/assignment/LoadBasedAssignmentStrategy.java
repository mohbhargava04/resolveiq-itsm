package com.resolveiq.assignment;

import org.springframework.stereotype.Component;

import java.util.List;

/** Balances work evenly: the technician with the lowest utilisation gets the incident. */
@Component
public class LoadBasedAssignmentStrategy extends AbstractAssignmentStrategy {

    public static final String KEY = "LOAD";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String displayName() {
        return "Load-based";
    }

    @Override
    public String description() {
        return "Chooses the technician with the lowest current utilisation (active incidents ÷ capacity)";
    }

    @Override
    protected Score score(AssignmentRequest request, TechnicianProfile p) {
        double score = (1 - p.utilisation()) * 100;
        return new Score(score, List.of(String.format("%.0f%% utilised", p.utilisation() * 100), workloadText(p)));
    }
}
