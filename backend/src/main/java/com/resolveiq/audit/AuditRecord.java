package com.resolveiq.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

/**
 * One immutable, append-only line of the audit trail: timestamp, user, action, incident, previous
 * state, new state and description (exactly the fields the brief asks for).
 *
 * <p>{@code @Immutable} tells Hibernate never to issue UPDATEs for this entity, and the class has
 * no setters - once written, an audit record cannot be altered through the application.</p>
 */
@Entity
@Immutable
@Table(name = "audit_records")
public class AuditRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "actor_name", nullable = false, length = 120)
    private String actorName;

    @Column(name = "actor_role", length = 30)
    private String actorRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AuditAction action;

    @Column(name = "incident_id")
    private Long incidentId;

    @Column(name = "incident_number", length = 20)
    private String incidentNumber;

    @Column(name = "previous_state", length = 60)
    private String previousState;

    @Column(name = "new_state", length = 60)
    private String newState;

    @Column(nullable = false, length = 1000)
    private String description;

    protected AuditRecord() {
    }

    private AuditRecord(Builder b) {
        this.occurredAt = b.occurredAt;
        this.actorId = b.actorId;
        this.actorName = b.actorName == null ? "System" : b.actorName;
        this.actorRole = b.actorRole;
        this.action = b.action;
        this.incidentId = b.incidentId;
        this.incidentNumber = b.incidentNumber;
        this.previousState = b.previousState;
        this.newState = b.newState;
        String d = b.description == null ? b.action.name() : b.description;
        this.description = d.length() > 1000 ? d.substring(0, 999) + "…" : d;
    }

    /** <b>Builder pattern</b>: audit records have many optional fields; a builder keeps creation readable. */
    public static Builder builder(AuditAction action, Instant occurredAt) {
        return new Builder(action, occurredAt);
    }

    public static final class Builder {
        private final AuditAction action;
        private final Instant occurredAt;
        private Long actorId;
        private String actorName;
        private String actorRole;
        private Long incidentId;
        private String incidentNumber;
        private String previousState;
        private String newState;
        private String description;

        private Builder(AuditAction action, Instant occurredAt) {
            this.action = action;
            this.occurredAt = occurredAt;
        }

        public Builder actor(Long id, String name, String role) {
            this.actorId = id;
            this.actorName = name;
            this.actorRole = role;
            return this;
        }

        public Builder incident(Long id, String number) {
            this.incidentId = id;
            this.incidentNumber = number;
            return this;
        }

        public Builder states(Object previous, Object next) {
            this.previousState = previous == null ? null : previous.toString();
            this.newState = next == null ? null : next.toString();
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public AuditRecord build() {
            return new AuditRecord(this);
        }
    }

    public Long getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Long getActorId() {
        return actorId;
    }

    public String getActorName() {
        return actorName;
    }

    public String getActorRole() {
        return actorRole;
    }

    public AuditAction getAction() {
        return action;
    }

    public Long getIncidentId() {
        return incidentId;
    }

    public String getIncidentNumber() {
        return incidentNumber;
    }

    public String getPreviousState() {
        return previousState;
    }

    public String getNewState() {
        return newState;
    }

    public String getDescription() {
        return description;
    }
}
