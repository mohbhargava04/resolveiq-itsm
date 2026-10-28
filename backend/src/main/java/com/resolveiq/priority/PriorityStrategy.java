package com.resolveiq.priority;

/**
 * <b>Strategy pattern</b>: interchangeable priority algorithms. The administrator chooses the active
 * one at runtime; adding a new algorithm (e.g. an ML model) means adding one class that implements
 * this interface - nothing else changes.
 */
public interface PriorityStrategy {

    /** Stable key stored in the settings table, e.g. {@code WEIGHTED}. */
    String key();

    String displayName();

    String description();

    PriorityAssessment assess(PriorityContext context);
}
