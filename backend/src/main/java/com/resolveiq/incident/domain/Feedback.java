package com.resolveiq.incident.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.time.Instant;

/** Customer satisfaction feedback (1-5 stars) left by the reporter after resolution. */
@Embeddable
public class Feedback {

    @Column(name = "feedback_rating")
    private Integer rating;

    @Column(name = "feedback_comment", length = 1000)
    private String comment;

    @Column(name = "feedback_submitted_at")
    private Instant submittedAt;

    protected Feedback() {
    }

    public Feedback(int rating, String comment, Instant submittedAt) {
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5");
        }
        this.rating = rating;
        this.comment = comment == null || comment.isBlank() ? null : comment.trim();
        this.submittedAt = submittedAt;
    }

    public Integer getRating() {
        return rating;
    }

    public String getComment() {
        return comment;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }
}
