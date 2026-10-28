package com.resolveiq.intelligence;

import com.resolveiq.incident.IncidentRepository;
import com.resolveiq.incident.domain.Incident;
import com.resolveiq.incident.domain.IncidentStatus;
import com.resolveiq.sla.SlaRecord;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <b>Intelligent feature E - Predictive SLA Risk Detection.</b> Goes beyond "how much time is left" by
 * estimating whether the remaining time is <em>enough</em>, using how long similar incidents
 * (same category) historically took to resolve.
 *
 * <pre>
 *   risk = 0.40 × elapsed fraction of the resolution window
 *        + 0.30 × prediction pressure   (expected remaining work ÷ time remaining, capped at 1)
 *        + 0.15 × ownership gap         (1 if unassigned, 0.5 if assigned but not acknowledged)
 *        + 0.15 × assignee utilisation
 *
 *   HIGH ≥ 0.70 · MEDIUM ≥ 0.45 · otherwise LOW
 * </pre>
 *
 * Historical medians are cached for five minutes since they change slowly.
 */
@Component
public class SlaRiskAssessor {

    public enum Level { NONE, LOW, MEDIUM, HIGH, BREACHED, PAUSED }

    public record SlaRisk(Level level, double score, Long minutesRemaining, Long expectedMinutesToResolve,
                          List<String> factors) {
    }

    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final IncidentRepository incidents;
    private final Clock clock;
    private volatile Map<String, Double> medianMinutesByCategory = Map.of();
    private volatile Instant cachedAt = Instant.EPOCH;

    public SlaRiskAssessor(IncidentRepository incidents, Clock clock) {
        this.incidents = incidents;
        this.clock = clock;
    }

    public SlaRisk assess(Incident incident, int assigneeActive, int assigneeCapacity, Instant now) {
        SlaRecord sla = incident.getSla();
        if (!incident.isActive() || !sla.isStarted()) {
            return new SlaRisk(Level.NONE, 0, null, null, List.of());
        }
        long remaining = sla.remaining(now).toMinutes();
        if (sla.isBreached()) {
            return new SlaRisk(Level.BREACHED, 1.0, remaining, null, List.of("SLA already breached"));
        }
        if (sla.isPaused()) {
            return new SlaRisk(Level.PAUSED, 0, remaining, null, List.of("Clock paused while waiting for the reporter"));
        }

        List<String> factors = new ArrayList<>();
        double elapsed = Math.min(1, sla.elapsedFraction(now));
        factors.add(String.format("%.0f%% of the resolution window used", elapsed * 100));

        Double median = incident.getCategory() == null ? null : medians().get(incident.getCategory().getCode());
        Long expectedRemaining = null;
        double pressure = 0;
        if (median != null) {
            long workedMinutes = Duration.between(sla.getStartedAt(), now).toMinutes() - sla.getPausedSeconds() / 60;
            expectedRemaining = Math.max(0, Math.round(median - workedMinutes));
            pressure = remaining <= 0 ? 1 : Math.min(1, expectedRemaining / (double) Math.max(1, remaining));
            factors.add(String.format("Similar %s incidents usually take %.0f min", incident.getCategory().getName(), median));
        }

        double ownership;
        if (incident.getAssignee() == null) {
            ownership = 1;
            factors.add("No technician assigned");
        } else if (sla.getRespondedAt() == null) {
            ownership = 0.5;
            factors.add("Not yet acknowledged");
        } else {
            ownership = 0;
        }

        double utilisation = assigneeCapacity <= 0 ? 0 : Math.min(1, assigneeActive / (double) assigneeCapacity);
        if (incident.getAssignee() != null && utilisation >= 0.8) {
            factors.add(String.format("Assignee is %.0f%% loaded", utilisation * 100));
        }

        double score = 0.40 * elapsed + 0.30 * pressure + 0.15 * ownership + 0.15 * utilisation;
        Level level = score >= 0.70 ? Level.HIGH : score >= 0.45 ? Level.MEDIUM : Level.LOW;
        return new SlaRisk(level, Math.round(score * 100.0) / 100.0, remaining, expectedRemaining, factors);
    }

    /** Median minutes-to-resolve per category, from resolved incidents (cached). */
    public Map<String, Double> medians() {
        Instant now = clock.instant();
        if (Duration.between(cachedAt, now).compareTo(CACHE_TTL) > 0) {
            Map<String, List<Long>> durations = new HashMap<>();
            for (Incident i : incidents.findResolvedKnowledge(IncidentStatus.DONE)) {
                Duration d = i.getSla().resolutionTime();
                if (i.getCategory() != null && d != null && !d.isNegative()) {
                    durations.computeIfAbsent(i.getCategory().getCode(), k -> new ArrayList<>()).add(d.toMinutes());
                }
            }
            medianMinutesByCategory = durations.entrySet().stream()
                    .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> median(e.getValue())));
            cachedAt = now;
        }
        return medianMinutesByCategory;
    }

    public void invalidate() {
        cachedAt = Instant.EPOCH;
    }

    static double median(List<Long> values) {
        List<Long> sorted = values.stream().sorted().toList();
        int n = sorted.size();
        if (n == 0) {
            return 0;
        }
        return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
    }
}
