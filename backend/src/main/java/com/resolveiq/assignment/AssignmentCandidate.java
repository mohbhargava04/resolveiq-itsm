package com.resolveiq.assignment;

import java.util.List;

/** A ranked recommendation with the reasons behind its score (shown in the UI as "why this person"). */
public record AssignmentCandidate(TechnicianProfile profile, double score, List<String> reasons) {

    public AssignmentCandidate {
        reasons = List.copyOf(reasons);
    }
}
