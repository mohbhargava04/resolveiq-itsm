package com.resolveiq.intelligence.text;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * TF-IDF (term frequency × inverse document frequency) vectoriser with cosine similarity.
 *
 * <pre>
 *   tf(t, d)  = 1 + ln(count of t in d)                 (sub-linear, so repeating a word helps less)
 *   idf(t)    = ln((1 + N) / (1 + df(t))) + 1           (smoothed; rare words weigh more)
 *   weight    = tf × idf, then the vector is L2-normalised
 *   cosine(a, b) = a · b                                 (both are unit vectors)
 * </pre>
 *
 * Words that appear in every incident ("error", "outage") therefore matter less than distinctive
 * ones ("payroll", "vpn"). Vectors are sparse {@code Map<String, Double>} because each incident
 * uses only a handful of the vocabulary.
 */
public final class TfIdfVectorizer {

    private final Map<String, Integer> documentFrequency;
    private final int documentCount;

    private TfIdfVectorizer(Map<String, Integer> documentFrequency, int documentCount) {
        this.documentFrequency = documentFrequency;
        this.documentCount = documentCount;
    }

    public static TfIdfVectorizer fit(Collection<List<String>> corpus) {
        Map<String, Integer> df = new HashMap<>();
        for (List<String> doc : corpus) {
            for (String term : new HashSet<>(doc)) {
                df.merge(term, 1, Integer::sum);
            }
        }
        return new TfIdfVectorizer(df, corpus.size());
    }

    public Map<String, Double> vectorize(List<String> tokens) {
        Map<String, Integer> counts = new HashMap<>();
        tokens.forEach(t -> counts.merge(t, 1, Integer::sum));
        Map<String, Double> vector = new HashMap<>();
        double norm = 0;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            double tf = 1 + Math.log(e.getValue());
            double idf = Math.log((1.0 + documentCount) / (1.0 + documentFrequency.getOrDefault(e.getKey(), 0))) + 1;
            double w = tf * idf;
            vector.put(e.getKey(), w);
            norm += w * w;
        }
        if (norm > 0) {
            double n = Math.sqrt(norm);
            vector.replaceAll((k, v) -> v / n);
        }
        return vector;
    }

    public static double cosine(Map<String, Double> a, Map<String, Double> b) {
        Map<String, Double> small = a.size() <= b.size() ? a : b;
        Map<String, Double> large = small == a ? b : a;
        double dot = 0;
        for (Map.Entry<String, Double> e : small.entrySet()) {
            Double other = large.get(e.getKey());
            if (other != null) {
                dot += e.getValue() * other;
            }
        }
        return Math.max(0, Math.min(1, dot));
    }
}
