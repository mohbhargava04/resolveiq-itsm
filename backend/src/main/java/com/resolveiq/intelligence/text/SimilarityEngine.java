package com.resolveiq.intelligence.text;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Generic text-similarity search: "which of these items is most like this text?". Generic in
 * {@code T} so the same engine powers duplicate detection (T = open incident) and suggested
 * resolutions (T = resolved incident).
 */
@Component
public class SimilarityEngine {

    /** An item with its cosine similarity (0..1) to the query. */
    public record Scored<T>(T item, double similarity) {
    }

    public <T> List<Scored<T>> rank(String query, Collection<T> candidates, Function<T, String> textOf) {
        List<String> queryTokens = TextAnalyzer.analyze(query);
        if (queryTokens.isEmpty() || candidates.isEmpty()) {
            return List.of();
        }
        List<T> items = new ArrayList<>(candidates);
        List<List<String>> corpus = new ArrayList<>(items.size() + 1);
        for (T item : items) {
            corpus.add(TextAnalyzer.analyze(textOf.apply(item)));
        }
        corpus.add(queryTokens);
        TfIdfVectorizer vectorizer = TfIdfVectorizer.fit(corpus);
        Map<String, Double> queryVector = vectorizer.vectorize(queryTokens);

        List<Scored<T>> scored = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            double sim = TfIdfVectorizer.cosine(queryVector, vectorizer.vectorize(corpus.get(i)));
            if (sim > 0) {
                scored.add(new Scored<>(items.get(i), sim));
            }
        }
        scored.sort(Comparator.comparingDouble((Scored<T> s) -> s.similarity()).reversed());
        return scored;
    }
}
