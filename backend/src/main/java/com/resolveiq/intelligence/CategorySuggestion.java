package com.resolveiq.intelligence;

import java.util.List;

/**
 * Output of the category classifier.
 *
 * @param categoryCode best category, or null if the text had no recognisable words
 * @param confidence   posterior probability of the best category (0..1)
 * @param alternatives runner-up categories with their probabilities
 * @param signals      the words that most influenced the decision (explainability)
 */
public record CategorySuggestion(String categoryCode, String categoryName, double confidence,
                                 List<Alternative> alternatives, List<String> signals, String model) {

    public record Alternative(String categoryCode, String categoryName, double probability) {
    }

    public static CategorySuggestion unknown(String model) {
        return new CategorySuggestion(null, null, 0, List.of(), List.of(), model);
    }

    public boolean isConfident(double threshold) {
        return categoryCode != null && confidence >= threshold;
    }
}
