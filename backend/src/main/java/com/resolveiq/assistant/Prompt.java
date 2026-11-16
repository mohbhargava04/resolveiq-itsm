package com.resolveiq.assistant;

import java.util.ArrayList;
import java.util.List;

/**
 * What the assistant shows when it asks something: the question, tappable options, and optional
 * cards (knowledge-base articles, similar incidents, a major incident, the summary to confirm).
 * Immutable; built with {@link Builder} so each state only sets the parts it needs.
 */
public record Prompt(String text, List<Option> options, boolean allowText, String placeholder,
                     List<ArticleCard> articles, List<IncidentCard> incidents, MajorCard major, Summary summary) {

    /** A tappable answer. {@code value} is what comes back as {@link UserInput#choice()}. */
    public record Option(String value, String label, String hint) {
        public static Option of(String value, String label) {
            return new Option(value, label, null);
        }
    }

    /** A knowledge-base article shown inline, with its fix, so the person can try it without leaving the chat. */
    public record ArticleCard(Long id, String title, String symptoms, String solution, int deflections) {
    }

    public record IncidentCard(String number, String title, String status, String service, boolean mine) {
    }

    public record MajorCard(String number, String title, String service, String stage, String latestUpdate) {
    }

    /** The draft exactly as it will be submitted, plus the priority and SLA it is expected to get. */
    public record Summary(String title, String description, String service, String category,
                          Double categoryConfidence, String impact, String urgency, String device,
                          String priority, String priorityLabel, Integer responseMinutes, Integer resolutionMinutes) {
    }

    public static Builder ask(String text) {
        return new Builder(text);
    }

    public static final class Builder {
        private final String text;
        private final List<Option> options = new ArrayList<>();
        private boolean allowText;
        private String placeholder;
        private List<ArticleCard> articles = List.of();
        private List<IncidentCard> incidents = List.of();
        private MajorCard major;
        private Summary summary;

        private Builder(String text) {
            this.text = text;
        }

        public Builder option(String value, String label) {
            options.add(Option.of(value, label));
            return this;
        }

        public Builder option(String value, String label, String hint) {
            options.add(new Option(value, label, hint));
            return this;
        }

        /** The person may also type (or dictate) an answer. */
        public Builder freeText(String placeholder) {
            this.allowText = true;
            this.placeholder = placeholder;
            return this;
        }

        public Builder articles(List<ArticleCard> articles) {
            this.articles = List.copyOf(articles);
            return this;
        }

        public Builder incidents(List<IncidentCard> incidents) {
            this.incidents = List.copyOf(incidents);
            return this;
        }

        public Builder major(MajorCard major) {
            this.major = major;
            return this;
        }

        public Builder summary(Summary summary) {
            this.summary = summary;
            return this;
        }

        public Prompt build() {
            return new Prompt(text, List.copyOf(options), allowText, placeholder, articles, incidents, major, summary);
        }
    }
}
