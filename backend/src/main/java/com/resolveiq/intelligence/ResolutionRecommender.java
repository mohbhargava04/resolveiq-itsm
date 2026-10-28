package com.resolveiq.intelligence;

import com.resolveiq.catalog.Category;
import com.resolveiq.catalog.ItService;
import com.resolveiq.incident.IncidentRepository;
import com.resolveiq.incident.domain.Incident;
import com.resolveiq.incident.domain.IncidentStatus;
import com.resolveiq.intelligence.text.SimilarityEngine;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * <b>Intelligent feature G - Suggested Resolution.</b> Treats every resolved incident as a knowledge
 * base article and recommends the fixes of the most similar past incidents.
 *
 * <pre>
 *   relevance = 0.8 × textSimilarity + 0.1 × sameService + 0.1 × sameCategory
 * </pre>
 */
@Component
public class ResolutionRecommender {

    static final double MIN_TEXT_SIMILARITY = 0.15;

    public record SuggestedResolution(String incidentNumber, String title, String serviceName, String categoryName,
                                      String summary, String rootCause, double relevance, Instant resolvedAt) {
    }

    private final IncidentRepository incidents;
    private final SimilarityEngine similarity;

    public ResolutionRecommender(IncidentRepository incidents, SimilarityEngine similarity) {
        this.incidents = incidents;
        this.similarity = similarity;
    }

    public List<SuggestedResolution> suggest(String title, String description, ItService service, Category category,
                                             String excludeNumber, int limit) {
        List<Incident> knowledge = incidents.findResolvedKnowledge(IncidentStatus.DONE).stream()
                .filter(i -> !i.isDuplicateConfirmed())
                .filter(i -> !Objects.equals(i.getNumber(), excludeNumber))
                .toList();
        return similarity.rank(title + ". " + description, knowledge, i -> i.getTitle() + ". " + i.getDescription())
                .stream()
                .filter(s -> s.similarity() >= MIN_TEXT_SIMILARITY)
                .map(s -> {
                    Incident i = s.item();
                    boolean sameService = service != null && service.getId() != null
                            && service.getId().equals(i.getService().getId());
                    boolean sameCategory = category != null && i.getCategory() != null
                            && category.getId() != null && category.getId().equals(i.getCategory().getId());
                    double relevance = 0.8 * s.similarity() + (sameService ? 0.1 : 0) + (sameCategory ? 0.1 : 0);
                    return new SuggestedResolution(i.getNumber(), i.getTitle(), i.getService().getName(),
                            i.getCategory() == null ? null : i.getCategory().getName(),
                            i.getResolution().getSummary(), i.getResolution().getRootCause(),
                            Math.round(relevance * 1000.0) / 1000.0, i.getResolvedAt());
                })
                .sorted(Comparator.comparingDouble(SuggestedResolution::relevance).reversed())
                .limit(limit)
                .toList();
    }
}
