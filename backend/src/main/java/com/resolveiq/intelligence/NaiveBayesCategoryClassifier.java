package com.resolveiq.intelligence;

import com.resolveiq.catalog.Category;
import com.resolveiq.catalog.CategoryRepository;
import com.resolveiq.incident.IncidentRepository;
import com.resolveiq.incident.domain.Incident;
import com.resolveiq.intelligence.text.TextAnalyzer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A from-scratch <b>Multinomial Naive Bayes</b> text classifier (machine learning, no external library).
 *
 * <p><b>Training data</b>: each category's administrator-defined keywords (weighted ×3), its name and
 * description, plus every historical incident that has a category. The model therefore improves
 * automatically as more incidents are resolved - new categories work immediately from their keywords.</p>
 *
 * <pre>
 *   P(c | words) ∝ P(c) × Π P(w | c)
 *   P(w | c) = (count(w, c) + 1) / (totalWords(c) + |V|)        (Laplace smoothing)
 * </pre>
 *
 * Computation is done in log space to avoid underflow, and the posterior is normalised with a
 * softmax to give a confidence. The trained model is an immutable object swapped atomically
 * ({@code volatile}), so classification never sees a half-built model while retraining.
 */
@Service
@Transactional(readOnly = true)
public class NaiveBayesCategoryClassifier implements CategoryClassifier {

    private static final Logger log = LoggerFactory.getLogger(NaiveBayesCategoryClassifier.class);
    private static final int KEYWORD_WEIGHT = 3;
    private static final String MODEL = "Multinomial Naive Bayes";

    private final CategoryRepository categories;
    private final IncidentRepository incidents;

    private volatile Model model;
    private volatile boolean stale = true;

    public NaiveBayesCategoryClassifier(CategoryRepository categories, IncidentRepository incidents) {
        this.categories = categories;
        this.incidents = incidents;
    }

    /** Immutable trained parameters. */
    private record Model(Map<String, Double> logPrior, Map<String, Map<String, Double>> logLikelihood,
                         Map<String, Double> logUnseen, Set<String> vocabulary, Map<String, String> names,
                         int trainingDocuments) {
    }

    @Override
    public CategorySuggestion classify(String text) {
        Model m = currentModel();
        List<String> tokens = TextAnalyzer.analyze(text).stream().filter(m.vocabulary()::contains).toList();
        if (tokens.isEmpty() || m.logPrior().isEmpty()) {
            return CategorySuggestion.unknown(MODEL);
        }
        Map<String, Double> logScores = new LinkedHashMap<>();
        for (String c : m.logPrior().keySet()) {
            double score = m.logPrior().get(c);
            Map<String, Double> lik = m.logLikelihood().get(c);
            for (String t : tokens) {
                score += lik.getOrDefault(t, m.logUnseen().get(c));
            }
            logScores.put(c, score);
        }
        // Calibration: raw Naive Bayes posteriors are notoriously over-confident because each word is
        // treated as independent evidence. Scaling the evidence by √(token count) (a temperature)
        // gives probabilities that behave like real confidence scores.
        double temperature = Math.max(1, Math.sqrt(tokens.size()));
        logScores.replaceAll((c, v) -> v / temperature);
        Map<String, Double> probabilities = softmax(logScores);
        List<Map.Entry<String, Double>> ranked = probabilities.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed()).toList();

        String best = ranked.getFirst().getKey();
        List<CategorySuggestion.Alternative> alternatives = ranked.stream().skip(1).limit(2)
                .map(e -> new CategorySuggestion.Alternative(e.getKey(), m.names().get(e.getKey()), round(e.getValue())))
                .toList();
        return new CategorySuggestion(best, m.names().get(best), round(ranked.getFirst().getValue()), alternatives,
                topSignals(m, best, tokens), MODEL);
    }

    @Override
    public synchronized void retrain() {
        Map<String, Map<String, Integer>> wordCounts = new HashMap<>();
        Map<String, Integer> docCounts = new HashMap<>();
        Map<String, String> names = new HashMap<>();
        Set<String> vocabulary = new HashSet<>();

        List<Category> active = categories.findByActiveTrueOrderByName();
        for (Category c : active) {
            names.put(c.getCode(), c.getName());
            Map<String, Integer> counts = wordCounts.computeIfAbsent(c.getCode(), k -> new HashMap<>());
            for (String keyword : c.keywordSet()) {
                for (String t : TextAnalyzer.analyze(keyword)) {
                    counts.merge(t, KEYWORD_WEIGHT, Integer::sum);
                    vocabulary.add(t);
                }
            }
            for (String t : TextAnalyzer.analyze(c.getName() + " " + (c.getDescription() == null ? "" : c.getDescription()))) {
                counts.merge(t, 1, Integer::sum);
                vocabulary.add(t);
            }
            docCounts.put(c.getCode(), 1); // the keyword pseudo-document
        }

        int historical = 0;
        for (Incident i : incidents.findAllCategorised()) {
            String code = i.getCategory().getCode();
            if (!names.containsKey(code)) {
                continue; // category since deactivated
            }
            Map<String, Integer> counts = wordCounts.get(code);
            for (String t : TextAnalyzer.analyze(i.getTitle() + " " + i.getDescription())) {
                counts.merge(t, 1, Integer::sum);
                vocabulary.add(t);
            }
            docCounts.merge(code, 1, Integer::sum);
            historical++;
        }

        int totalDocs = docCounts.values().stream().mapToInt(Integer::intValue).sum();
        int v = Math.max(1, vocabulary.size());
        Map<String, Double> logPrior = new HashMap<>();
        Map<String, Map<String, Double>> logLik = new HashMap<>();
        Map<String, Double> logUnseen = new HashMap<>();
        for (String c : names.keySet()) {
            logPrior.put(c, Math.log(docCounts.get(c) / (double) totalDocs));
            Map<String, Integer> counts = wordCounts.get(c);
            int total = counts.values().stream().mapToInt(Integer::intValue).sum();
            double denominator = total + v;
            Map<String, Double> lik = new HashMap<>();
            counts.forEach((w, n) -> lik.put(w, Math.log((n + 1) / denominator)));
            logLik.put(c, lik);
            logUnseen.put(c, Math.log(1 / denominator));
        }
        this.model = new Model(Map.copyOf(logPrior), Map.copyOf(logLik), Map.copyOf(logUnseen),
                Set.copyOf(vocabulary), Map.copyOf(names), totalDocs);
        this.stale = false;
        log.info("Category classifier trained: {} categories, {} historical incidents, vocabulary {}",
                names.size(), historical, vocabulary.size());
    }

    @Override
    public void markStale() {
        stale = true;
    }

    @Override
    public String modelName() {
        return MODEL;
    }

    public int trainingDocuments() {
        return currentModel().trainingDocuments();
    }

    private Model currentModel() {
        if (stale || model == null) {
            retrain();
        }
        return model;
    }

    /** Words with the largest likelihood ratio in favour of the winning category. */
    private static List<String> topSignals(Model m, String best, List<String> tokens) {
        Map<String, Double> bestLik = m.logLikelihood().get(best);
        Map<String, Double> lift = new HashMap<>();
        for (String t : new HashSet<>(tokens)) {
            double b = bestLik.getOrDefault(t, m.logUnseen().get(best));
            double othersAvg = m.logLikelihood().entrySet().stream().filter(e -> !e.getKey().equals(best))
                    .mapToDouble(e -> e.getValue().getOrDefault(t, m.logUnseen().get(e.getKey()))).average().orElse(b);
            lift.put(t, b - othersAvg);
        }
        return lift.entrySet().stream().filter(e -> e.getValue() > 0)
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(4).map(Map.Entry::getKey).collect(Collectors.toList());
    }

    private static Map<String, Double> softmax(Map<String, Double> logScores) {
        double max = logScores.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
        Map<String, Double> exp = new LinkedHashMap<>();
        double sum = 0;
        for (Map.Entry<String, Double> e : logScores.entrySet()) {
            double x = Math.exp(e.getValue() - max);
            exp.put(e.getKey(), x);
            sum += x;
        }
        final double total = sum;
        exp.replaceAll((k, x) -> x / total);
        return exp;
    }

    private static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
