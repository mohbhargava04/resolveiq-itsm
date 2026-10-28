package com.resolveiq.intelligence;

/**
 * <b>Intelligent feature F - Automatic Incident Categorisation.</b> An interface so the built-in
 * Naive Bayes model can later be swapped for (or combined with) an LLM-based classifier through an
 * adapter, without changing the incident workflow.
 */
public interface CategoryClassifier {

    CategorySuggestion classify(String text);

    /** Rebuilds the model from the current categories and historical incidents. */
    void retrain();

    /** Marks the model out of date (it is rebuilt lazily on the next classification). */
    void markStale();

    String modelName();
}
