package com.resolveiq.intelligence;

import com.resolveiq.catalog.ItService;
import com.resolveiq.catalog.ItServiceRepository;
import com.resolveiq.incident.IncidentRepository;
import com.resolveiq.incident.domain.Incident;
import com.resolveiq.incident.domain.IncidentStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <b>Intelligent feature B - Incident Correlation</b> using the service dependency graph.
 *
 * <p>When Payment Gateway, HR Portal and ERP all report failures within a short window and all three
 * depend on Core Database, the most likely explanation is a single upstream problem. The algorithm:</p>
 * <ol>
 *   <li>Take open incidents reported within the correlation window.</li>
 *   <li>For each service R, collect incidents whose service <em>is</em> R or depends (transitively) on R.</li>
 *   <li>R is a candidate root cause if it explains at least N incidents across 2+ services
 *       (or N incidents on R itself).</li>
 *   <li>Score each candidate by <em>explanatory power</em>: the share of R's dependent services that are
 *       currently failing (+ a bonus if R itself has direct reports). This prefers the most specific
 *       common ancestor rather than a root everything depends on (e.g. the core network).</li>
 *   <li>Greedily take the best root, remove the incidents it explains, repeat.</li>
 * </ol>
 */
@Component
public class IncidentCorrelator {

    static final double MIN_COVERAGE = 0.3;
    static final double DIRECT_EVIDENCE_BONUS = 0.15;
    static final double SINGLE_SERVICE_WEIGHT = 0.5;

    public record CorrelationCluster(String rootServiceCode, String rootServiceName, boolean rootHasDirectReports,
                                     List<String> incidentNumbers, List<String> affectedServices, double confidence,
                                     String summary, Instant firstReportedAt, Instant lastReportedAt) {
    }

    private final IncidentRepository incidents;
    private final ItServiceRepository services;
    private final IntelligenceProperties properties;

    public IncidentCorrelator(IncidentRepository incidents, ItServiceRepository services,
                              IntelligenceProperties properties) {
        this.incidents = incidents;
        this.services = services;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public List<CorrelationCluster> findClusters(Instant now) {
        Instant since = now.minus(Duration.ofMinutes(properties.correlationWindowMinutes()));
        List<Incident> recent = incidents.findRecentInStatuses(IncidentStatus.ACTIVE, since).stream()
                .filter(i -> !i.isDuplicateConfirmed()).toList();
        return correlate(recent, services.findAllWithDependencies(), properties.correlationMinIncidents());
    }

    /** Pure algorithm (package-visible for unit tests). */
    static List<CorrelationCluster> correlate(List<Incident> open, List<ItService> catalog, int minIncidents) {
        // service code -> itself + everything upstream of it
        Map<String, Set<String>> upstreamOf = new HashMap<>();
        Map<String, ItService> byCode = new HashMap<>();
        for (ItService s : catalog) {
            byCode.put(s.getCode(), s);
            Set<String> up = new LinkedHashSet<>();
            up.add(s.getCode());
            s.transitiveDependencies().forEach(d -> up.add(d.getCode()));
            upstreamOf.put(s.getCode(), up);
        }
        // root code -> how many services (including itself) would be affected if it failed
        Map<String, Integer> blastRadius = new HashMap<>();
        for (String root : byCode.keySet()) {
            blastRadius.put(root, (int) upstreamOf.values().stream().filter(u -> u.contains(root)).count());
        }

        List<Incident> remaining = new ArrayList<>(open);
        List<CorrelationCluster> clusters = new ArrayList<>();
        while (remaining.size() >= minIncidents) {
            CorrelationCluster best = null;
            List<Incident> bestCovered = List.of();
            double bestScore = 0;
            for (String root : byCode.keySet()) {
                List<Incident> covered = remaining.stream()
                        .filter(i -> upstreamOf.getOrDefault(i.getService().getCode(), Set.of(i.getService().getCode()))
                                .contains(root))
                        .toList();
                if (covered.size() < minIncidents) {
                    continue;
                }
                Set<String> servicesHit = new HashSet<>();
                covered.forEach(i -> servicesHit.add(i.getService().getCode()));
                boolean direct = servicesHit.contains(root);
                if (servicesHit.size() < 2 && !direct) {
                    continue;
                }
                // Explanatory power: what share of the services that depend on this root are failing?
                // This favours the most specific common ancestor and stops "everything depends on the
                // network" from swallowing unrelated incidents.
                double coverage = servicesHit.size() / (double) blastRadius.get(root);
                if (coverage < MIN_COVERAGE && !(direct && servicesHit.size() == 1)) {
                    continue;
                }
                // A single-service cluster ("3 reports about Payment") is weaker evidence than a pattern
                // across several services, so it only wins when no broader explanation exists.
                double breadth = servicesHit.size() >= 2 ? 1.0 : SINGLE_SERVICE_WEIGHT;
                double score = coverage * breadth + (direct ? DIRECT_EVIDENCE_BONUS : 0) + covered.size() * 0.01;
                if (score > bestScore) {
                    bestScore = score;
                    bestCovered = covered;
                    best = buildCluster(byCode.get(root), covered, servicesHit, direct, coverage, byCode);
                }
            }
            if (best == null) {
                break;
            }
            clusters.add(best);
            remaining.removeAll(bestCovered);
        }
        return clusters;
    }

    private static CorrelationCluster buildCluster(ItService root, List<Incident> covered, Set<String> servicesHit,
                                                   boolean direct, double coverage, Map<String, ItService> byCode) {
        List<Incident> sorted = covered.stream().sorted(Comparator.comparing(Incident::getCreatedAt)).toList();
        List<String> serviceNames = servicesHit.stream().map(c -> byCode.containsKey(c) ? byCode.get(c).getName() : c)
                .sorted().toList();
        double confidence = Math.min(0.95, 0.3 + 0.5 * coverage + (direct ? 0.1 : 0) + 0.02 * covered.size());
        String summary;
        if (servicesHit.size() == 1) {
            summary = covered.size() + " reports about " + root.getName() + " — likely a single outage";
        } else {
            List<String> dependents = serviceNames.stream().filter(n -> !n.equals(root.getName())).toList();
            summary = covered.size() + " incidents across " + String.join(", ", dependents)
                    + (direct ? " and " + root.getName() + " itself" : "")
                    + " — all depend on " + root.getName() + ", the probable common root cause";
        }
        return new CorrelationCluster(root.getCode(), root.getName(), direct,
                sorted.stream().map(Incident::getNumber).toList(), serviceNames,
                Math.round(confidence * 100.0) / 100.0, summary,
                sorted.getFirst().getCreatedAt(), sorted.getLast().getCreatedAt());
    }
}
