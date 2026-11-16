package com.resolveiq.assistant;

import com.resolveiq.common.BaseEntity;
import com.resolveiq.user.domain.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * One "Report with assistant" conversation: who is talking, which step it has reached, what has
 * been learnt ({@link IncidentDraft}) and the transcript.
 *
 * <p><b>Aggregate root</b>: the transcript lines ({@link ChatMessage}) are owned by the session
 * (composition - cascade and orphan removal) and can only be added through it. The session is the
 * <i>context</i> of the State pattern: it holds the current {@link ConversationStep}, and the
 * matching {@code ConversationState} object decides how an answer is handled.</p>
 */
@Entity
@Table(name = "chat_sessions")
public class ChatSession extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User owner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ConversationStep step = ConversationStep.DESCRIBE_PROBLEM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ConversationOutcome outcome = ConversationOutcome.OPEN;

    @Embedded
    private IncidentDraft draft = IncidentDraft.empty();

    /** The incident created or joined, or the existing one this turned out to duplicate. */
    @Column(name = "incident_number", length = 20)
    private String incidentNumber;

    /** The knowledge-base article that solved the problem, when the outcome is DEFLECTED. */
    @Column(name = "deflected_article_id")
    private Long deflectedArticleId;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<ChatMessage> messages = new ArrayList<>();

    protected ChatSession() {
        // for JPA
    }

    public ChatSession(User owner) {
        this.owner = Objects.requireNonNull(owner, "owner");
    }

    // ---- behaviour -----------------------------------------------------------------------------

    public boolean isOwnedBy(User user) {
        return user != null && owner.getId() != null && owner.getId().equals(user.getId());
    }

    public void say(MessageSender sender, String text, Instant now) {
        if (text == null || text.isBlank()) {
            return;
        }
        messages.add(new ChatMessage(this, messages.size(), sender, text.trim(), now));
    }

    /** Moves to another step. A finished conversation never moves again. */
    public void moveTo(ConversationStep next) {
        requireOpen();
        if (next == ConversationStep.FINISHED) {
            throw new IllegalArgumentException("Use finish(...) to end a conversation");
        }
        this.step = next;
    }

    /** "Start over": forget everything learnt so far but keep the transcript. */
    public void restart() {
        requireOpen();
        this.draft = IncidentDraft.empty();
        this.step = ConversationStep.DESCRIBE_PROBLEM;
    }

    public void finishWithIncident(String number, Instant now) {
        finish(ConversationOutcome.INCIDENT_CREATED, number, null, now);
    }

    public void finishJoiningMajor(String number, Instant now) {
        finish(ConversationOutcome.JOINED_MAJOR_INCIDENT, number, null, now);
    }

    public void finishDeflected(Long articleId, Instant now) {
        finish(ConversationOutcome.DEFLECTED, null, Objects.requireNonNull(articleId), now);
    }

    public void finishAsDuplicate(String existingNumber, Instant now) {
        finish(ConversationOutcome.DUPLICATE_AVOIDED, existingNumber, null, now);
    }

    public void cancel(Instant now) {
        finish(ConversationOutcome.CANCELLED, null, null, now);
    }

    private void finish(ConversationOutcome how, String number, Long articleId, Instant now) {
        requireOpen();
        this.outcome = how;
        this.step = ConversationStep.FINISHED;
        this.incidentNumber = number;
        this.deflectedArticleId = articleId;
        this.finishedAt = now;
    }

    private void requireOpen() {
        if (isFinished()) {
            throw new IllegalStateException("This conversation has already ended");
        }
    }

    // ---- accessors -----------------------------------------------------------------------------

    public boolean isFinished() {
        return outcome.isFinal();
    }

    public User getOwner() {
        return owner;
    }

    public ConversationStep getStep() {
        return step;
    }

    public ConversationOutcome getOutcome() {
        return outcome;
    }

    public IncidentDraft getDraft() {
        return draft;
    }

    public String getIncidentNumber() {
        return incidentNumber;
    }

    public Long getDeflectedArticleId() {
        return deflectedArticleId;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    /** Read-only view: lines are added with {@link #say}. */
    public List<ChatMessage> getMessages() {
        return Collections.unmodifiableList(messages);
    }
}
