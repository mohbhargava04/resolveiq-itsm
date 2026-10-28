package com.resolveiq.incident.domain;

import com.resolveiq.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

/** Value object describing how an incident was fixed. Becomes part of the knowledge base. */
@Embeddable
public class Resolution {

    @Column(name = "resolution_summary", length = 2000)
    private String summary;

    @Column(name = "resolution_root_cause", length = 1000)
    private String rootCause;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by_id")
    private User resolvedBy;

    protected Resolution() {
    }

    public Resolution(String summary, String rootCause, User resolvedBy) {
        if (summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("Resolution details are required");
        }
        this.summary = summary.trim();
        this.rootCause = rootCause == null || rootCause.isBlank() ? null : rootCause.trim();
        this.resolvedBy = resolvedBy;
    }

    public String getSummary() {
        return summary;
    }

    public String getRootCause() {
        return rootCause;
    }

    public User getResolvedBy() {
        return resolvedBy;
    }
}
