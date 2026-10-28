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

/**
 * One entry in an incident's work log. Owned by its incident (<b>composition</b>: cascade-all with
 * orphan removal - a note never exists without its incident).
 */
@Entity
@Table(name = "incident_notes")
public class IncidentNote extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id")
    private Incident incident;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private User author;

    @Column(name = "author_name", nullable = false, length = 120)
    private String authorName;

    @Enumerated(EnumType.STRING)
    @Column(name = "note_type", nullable = false, length = 20)
    private NoteType type;

    /** Internal notes are visible to IT staff only, never to the reporting employee. */
    @Column(nullable = false)
    private boolean internal;

    @Column(nullable = false, length = 4000)
    private String content;

    protected IncidentNote() {
    }

    IncidentNote(Incident incident, User author, String authorName, NoteType type, boolean internal,
                 String content, Instant at) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Note content is required");
        }
        if (content.length() > 4000) {
            throw new IllegalArgumentException("Note content must be at most 4000 characters");
        }
        this.incident = incident;
        this.author = author;
        this.authorName = authorName;
        this.type = type;
        this.internal = internal;
        this.content = content.trim();
        overrideCreatedAt(at);
    }

    public Incident getIncident() {
        return incident;
    }

    public User getAuthor() {
        return author;
    }

    public String getAuthorName() {
        return authorName;
    }

    public NoteType getType() {
        return type;
    }

    public boolean isInternal() {
        return internal;
    }

    public String getContent() {
        return content;
    }
}
