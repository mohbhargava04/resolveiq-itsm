package com.resolveiq.notification.inbound;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Log of every e-mail received, and what was done with it. */
@Entity
@Table(name = "inbound_emails")
public class InboundEmail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "from_address", nullable = false, length = 160)
    private String fromAddress;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(name = "body_excerpt", length = 1000)
    private String bodyExcerpt;

    @Column(nullable = false, length = 30)
    private String outcome;

    @Column(name = "incident_number", length = 20)
    private String incidentNumber;

    @Column(length = 500)
    private String detail;

    protected InboundEmail() {
    }

    public InboundEmail(Instant receivedAt, String fromAddress, String subject, String bodyExcerpt, String outcome,
                        String incidentNumber, String detail) {
        this.receivedAt = receivedAt;
        this.fromAddress = cut(fromAddress, 160);
        this.subject = cut(subject == null ? "(no subject)" : subject, 200);
        this.bodyExcerpt = cut(bodyExcerpt, 1000);
        this.outcome = outcome;
        this.incidentNumber = incidentNumber;
        this.detail = cut(detail, 500);
    }

    private static String cut(String s, int max) {
        return s == null ? null : s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    public Long getId() {
        return id;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public String getFromAddress() {
        return fromAddress;
    }

    public String getSubject() {
        return subject;
    }

    public String getBodyExcerpt() {
        return bodyExcerpt;
    }

    public String getOutcome() {
        return outcome;
    }

    public String getIncidentNumber() {
        return incidentNumber;
    }

    public String getDetail() {
        return detail;
    }
}
