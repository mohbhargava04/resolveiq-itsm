package com.resolveiq.incident.domain;

import com.resolveiq.common.BaseEntity;
import com.resolveiq.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/** One public status update on a major incident ("Cause identified: storage controller failover..."). */
@Entity
@Table(name = "major_incident_updates")
public class MajorIncidentUpdate extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id")
    private Incident incident;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MajorStage stage;

    @Column(nullable = false, length = 2000)
    private String message;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private User author;

    @Column(name = "author_name", nullable = false, length = 120)
    private String authorName;

    protected MajorIncidentUpdate() {
    }

    MajorIncidentUpdate(Incident incident, MajorStage stage, String message, User author, String authorName, Instant at) {
        this.incident = incident;
        this.stage = stage;
        this.message = message;
        this.author = author;
        this.authorName = authorName;
        overrideCreatedAt(at);
    }

    public MajorStage getStage() {
        return stage;
    }

    public String getMessage() {
        return message;
    }

    public String getAuthorName() {
        return authorName;
    }
}
