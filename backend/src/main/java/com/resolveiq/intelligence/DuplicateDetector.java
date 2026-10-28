package com.resolveiq.intelligence;

import com.resolveiq.catalog.ItService;
import com.resolveiq.incident.IncidentRepository;
import com.resolveiq.incident.domain.Incident;
import com.resolveiq.incident.domain.IncidentStatus;
import com.resolveiq.intelligence.text.SimilarityEngine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * <b>Intelligent feature A - Duplicate Incident Detection.</b>
 *
 * <pre>
 *   score = 0.75 × textSimilarity(title + description)        (TF-IDF cosine)
 *         + 0.25 × serviceAffinity                             (1 same service, 0.5 dependent service, 0 otherwise)
 * </pre>
 *
 * Only open incidents reported within the configured window are compared, and a minimum text
 * similarity is required so two unrelated problems on the same service are not merged.
 */
@Component
public class DuplicateDetector {

    static final double TEXT_WEIGHT = 0.75;
    static final double SERVICE_WEIGHT = 0.25;
    static final double MIN_TEXT_SIMILARITY = 0.25;

    /** A candidate duplicate with its scores. */
    public record SimilarIncident(Incident incident, double score, double textSimilarity, boolean sameService) {
    }

    private final IncidentRepository incidents;
    private final SimilarityEngine similarity;
    private final IntelligenceProperties properties;

    public DuplicateDetector(IncidentRepository incidents, SimilarityEngine similarity, IntelligenceProperties properties) {
        this.incidents = incidents;
        this.similarity = similarity;
        this.properties = properties;
    }

    public List<SimilarIncident> findSimilarOpenIncidents(String title, String description, ItService service,
                                                          Long excludeIncidentId, Instant now) {
        Instant since = now.minus(Duration.ofHours(properties.duplicateWindowHours()));
        List<Incident> candidates = incidents.findRecentInStatuses(IncidentStatus.ACTIVE, since).stream()
                .filter(i -> !Objects.equals(i.getId(), excludeIncidentId))
                .filter(i -> !i.isDuplicateConfirmed())
                .toList();
        String query = title + ". " + description;
        return similarity.rank(query, candidates, i -> i.getTitle() + ". " + i.getDescription()).stream()
                .filter(s -> s.similarity() >= MIN_TEXT_SIMILARITY)
                .map(s -> {
                    double affinity = serviceAffinity(service, s.item().getService());
                    double score = TEXT_WEIGHT * s.similarity() + SERVICE_WEIGHT * affinity;
                    return new SimilarIncident(s.item(), round(score), round(s.similarity()), affinity == 1.0);
                })
                .sorted(Comparator.comparingDouble(SimilarIncident::score).reversed())
                .limit(5)
                .toList();
    }

    public boolean isSuspectedDuplicate(SimilarIncident s) {
        return s.score() >= properties.duplicateSuspectThreshold();
    }

    public boolean isNearIdentical(SimilarIncident s) {
        return s.score() >= properties.duplicateBlockThreshold();
    }

    static double serviceAffinity(ItService a, ItService b) {
        if (a == null || b == null) {
            return 0;
        }
        if (a.getId() != null && a.getId().equals(b.getId())) {
            return 1.0;
        }
        return a.dependsOn(b) || b.dependsOn(a) ? 0.5 : 0;
    }

    private static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
