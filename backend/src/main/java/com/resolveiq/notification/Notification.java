package com.resolveiq.notification;

import com.resolveiq.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A persisted notification delivery. In-app rows are the user's notification inbox (with a read
 * flag); e-mail/SMS rows form the delivery log administrators can inspect.
 */
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id")
    private User recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ChannelType channel;

    @Column(name = "event_type", length = 40)
    private String eventType;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(nullable = false, length = 2000)
    private String message;

    @Column(name = "incident_number", length = 20)
    private String incidentNumber;

    @Column(nullable = false)
    private boolean urgent;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 12)
    private DeliveryStatus deliveryStatus;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    protected Notification() {
    }

    public Notification(NotificationMessage m, ChannelType channel, DeliveryStatus status, String error, Instant at) {
        this.recipient = m.recipient();
        this.channel = channel;
        this.eventType = m.eventType();
        this.subject = truncate(m.subject(), 200);
        this.message = truncate(m.body(), 2000);
        this.incidentNumber = m.incidentNumber();
        this.urgent = m.urgent();
        this.deliveryStatus = status;
        this.errorMessage = truncate(error, 500);
        this.createdAt = at;
        this.read = channel != ChannelType.IN_APP; // only in-app notifications have an unread state
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    public void markRead() {
        this.read = true;
    }

    public Long getId() {
        return id;
    }

    public User getRecipient() {
        return recipient;
    }

    public ChannelType getChannel() {
        return channel;
    }

    public String getEventType() {
        return eventType;
    }

    public String getSubject() {
        return subject;
    }

    public String getMessage() {
        return message;
    }

    public String getIncidentNumber() {
        return incidentNumber;
    }

    public boolean isUrgent() {
        return urgent;
    }

    public DeliveryStatus getDeliveryStatus() {
        return deliveryStatus;
    }

    public boolean isRead() {
        return read;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
