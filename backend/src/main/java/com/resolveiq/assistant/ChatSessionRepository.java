package com.resolveiq.assistant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    long countByOutcomeAndCreatedAtAfter(ConversationOutcome outcome, Instant since);
}
