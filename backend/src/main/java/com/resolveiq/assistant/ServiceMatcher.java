package com.resolveiq.assistant;

import com.resolveiq.catalog.ItService;
import com.resolveiq.catalog.ItServiceRepository;
import com.resolveiq.incident.IncidentRepository;
import com.resolveiq.incident.domain.Incident;
import com.resolveiq.intelligence.text.SimilarityEngine;
import com.resolveiq.intelligence.text.TextAnalyzer;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Works out which IT service a free-text problem is probably about, so the assistant can say
 * "It sounds like this is about Campus Wi-Fi - is that right?" instead of showing a long list.
 *
 * <p>Two independent signals are combined:</p>
 * <ol>
 *   <li><b>Catalogue wording</b> - how many of the service's own name/description words appear in the text
 *       (name words count double; an exact mention of the name settles it).</li>
 *   <li><b>History</b> - a k-nearest-neighbour vote: the services of the most similar past incidents
 *       (TF-IDF cosine similarity, the same engine as duplicate detection), weighted by similarity.</li>
 * </ol>
 * <pre>score = 0.6 × catalogue + 0.4 × history   (each normalised to 0..1)</pre>
 */
@Component
public class ServiceMatcher {

    static final double WORDING_WEIGHT = 0.6;
    static final double HISTORY_WEIGHT = 0.4;
    /** Below this the assistant does not guess; it asks. */
    static final double MIN_SCORE = 0.18;
    static final int NEIGHBOURS = 8;
    static final double MIN_NEIGHBOUR_SIMILARITY = 0.2;

    /** A service with how well it matches (0..1). */
    public record Candidate(ItService service, double score) {
    }

    private final ItServiceRepository services;
    private final IncidentRepository incidents;
    private final SimilarityEngine similarity;

    public ServiceMatcher(ItServiceRepository services, IncidentRepository incidents, SimilarityEngine similarity) {
        this.services = services;
        this.incidents = incidents;
        this.similarity = similarity;
    }

    /** Best matches first; empty when nothing matches well enough to suggest. */
    public List<Candidate> match(String text, int limit) {
        List<ItService> active = activeServices();
        Map<String, Double> history = historyVotes(text);
        Set<String> words = new HashSet<>(TextAnalyzer.analyze(text));
        String lower = " " + text.toLowerCase(Locale.ROOT) + " ";
        return active.stream()
                .map(s -> new Candidate(s, round(WORDING_WEIGHT * wordingScore(s, words, lower)
                        + HISTORY_WEIGHT * history.getOrDefault(s.getCode(), 0.0))))
                .filter(c -> c.score() >= MIN_SCORE)
                .sorted(Comparator.comparingDouble(Candidate::score).reversed()
                        .thenComparing(c -> c.service().getName()))
                .limit(limit)
                .toList();
    }

    public List<ItService> activeServices() {
        return services.findAll().stream().filter(ItService::isActive)
                .sorted(Comparator.comparing(ItService::getName)).toList();
    }

    /** Finds a service the person named or picked: its code, its exact name, or a clear match in the text. */
    public Optional<ItService> resolve(String codeOrText) {
        if (codeOrText == null || codeOrText.isBlank()) {
            return Optional.empty();
        }
        String wanted = codeOrText.trim();
        List<ItService> active = activeServices();
        Optional<ItService> exact = active.stream()
                .filter(s -> s.getCode().equalsIgnoreCase(wanted) || s.getName().equalsIgnoreCase(wanted)).findFirst();
        if (exact.isPresent()) {
            return exact;
        }
        Set<String> words = new HashSet<>(TextAnalyzer.analyze(wanted));
        String lower = " " + wanted.toLowerCase(Locale.ROOT) + " ";
        List<Candidate> ranked = active.stream().map(s -> new Candidate(s, wordingScore(s, words, lower)))
                .filter(c -> c.score() >= 0.5)
                .sorted(Comparator.comparingDouble(Candidate::score).reversed()).toList();
        // only when one service clearly stands out; otherwise the person is asked to pick
        if (ranked.isEmpty() || (ranked.size() > 1 && ranked.get(0).score() - ranked.get(1).score() < 0.15)) {
            return Optional.empty();
        }
        return Optional.of(ranked.get(0).service());
    }

    static double wordingScore(ItService service, Set<String> textWords, String lowerText) {
        if (lowerText.contains(" " + service.getName().toLowerCase(Locale.ROOT) + " ")) {
            return 1.0;
        }
        Set<String> nameWords = new HashSet<>(TextAnalyzer.analyze(service.getName()));
        Set<String> descriptionWords = new HashSet<>(TextAnalyzer.analyze(service.getDescription()));
        descriptionWords.removeAll(nameWords);
        if (nameWords.isEmpty() && descriptionWords.isEmpty()) {
            return 0;
        }
        double hit = 2.0 * nameWords.stream().filter(textWords::contains).count()
                + descriptionWords.stream().filter(textWords::contains).count();
        // the best possible score is "every name word, and a couple of description words"
        double best = 2.0 * nameWords.size() + Math.min(2, descriptionWords.size());
        return Math.min(1.0, hit / best);
    }

    private Map<String, Double> historyVotes(String text) {
        List<Incident> past = incidents.findAllCategorised();
        Map<String, Double> votes = new HashMap<>();
        double total = 0;
        for (SimilarityEngine.Scored<Incident> s : similarity
                .rank(text, past, i -> i.getTitle() + ". " + i.getDescription()).stream()
                .filter(x -> x.similarity() >= MIN_NEIGHBOUR_SIMILARITY).limit(NEIGHBOURS).toList()) {
            votes.merge(s.item().getService().getCode(), s.similarity(), Double::sum);
            total += s.similarity();
        }
        if (total > 0) {
            double sum = total;
            votes.replaceAll((code, v) -> v / sum);
        }
        return votes;
    }

    private static double round(double v) {
        return Math.round(v * 1000) / 1000.0;
    }
}
