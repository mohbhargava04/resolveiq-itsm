package com.resolveiq.assistant;

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
import java.util.Objects;

/**
 * One line of a conversation transcript. Created only by {@link ChatSession} (package-private
 * constructor) and never changed afterwards, so the transcript is an append-only record.
 */
@Entity
@Table(name = "chat_messages")
public class ChatMessage {

    static final int MAX_LENGTH = 4000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private ChatSession session;

    @Column(name = "line_no", nullable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private MessageSender sender;

    @Column(name = "body", nullable = false, length = MAX_LENGTH)
    private String text;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    protected ChatMessage() {
        // for JPA
    }

    ChatMessage(ChatSession session, int position, MessageSender sender, String text, Instant sentAt) {
        this.session = Objects.requireNonNull(session);
        this.position = position;
        this.sender = Objects.requireNonNull(sender);
        this.text = text.length() > MAX_LENGTH ? text.substring(0, MAX_LENGTH) : text;
        this.sentAt = Objects.requireNonNull(sentAt);
    }

    public Long getId() {
        return id;
    }

    public int getPosition() {
        return position;
    }

    public MessageSender getSender() {
        return sender;
    }

    public String getText() {
        return text;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}
