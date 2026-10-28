package com.resolveiq.assignment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * <b>Template Method pattern</b>: the ranking algorithm skeleton - filter out unavailable or
 * overloaded technicians, apply the strategy's own eligibility rule, score, sort - is fixed here.
 * Subclasses only override the two "hook" steps: {@link #isEligible} and {@link #score}.
 */
public abstract class AbstractAssignmentStrategy implements AssignmentStrategy {

    /** A scored technician; {@code reasons} explains the score. */
    protected record Score(double value, List<String> reasons) {
    }

    @Override
    public final List<AssignmentCandidate> rank(AssignmentRequest request, List<TechnicianProfile> pool) {
        List<AssignmentCandidate> ranked = new ArrayList<>();
        for (TechnicianProfile p : pool) {
            if (!p.technician().isAssignable() || !p.hasCapacity() || !isEligible(request, p)) {
                continue;
            }
            Score s = score(request, p);
            ranked.add(new AssignmentCandidate(p, round(s.value()), s.reasons()));
        }
        ranked.sort(Comparator.comparingDouble(AssignmentCandidate::score).reversed()
                .thenComparingInt(c -> c.profile().activeIncidents())
                .thenComparing(c -> c.profile().technician().getFullName()));
        return ranked;
    }

    public Optional<AssignmentCandidate> best(AssignmentRequest request, List<TechnicianProfile> pool) {
        return rank(request, pool).stream().findFirst();
    }

    /** Strategy-specific eligibility (default: everyone available is eligible). */
    protected boolean isEligible(AssignmentRequest request, TechnicianProfile profile) {
        return true;
    }

    protected abstract Score score(AssignmentRequest request, TechnicianProfile profile);

    protected static String workloadText(TechnicianProfile p) {
        return p.activeIncidents() + "/" + p.capacity() + " active incidents";
    }

    private static double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
