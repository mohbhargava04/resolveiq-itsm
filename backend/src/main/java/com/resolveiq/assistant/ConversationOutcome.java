package com.resolveiq.assistant;

/** How a conversation ended. */
public enum ConversationOutcome {
    /** Still in progress. */
    OPEN,
    /** An incident was created through the normal pipeline. */
    INCIDENT_CREATED,
    /** A knowledge-base article fixed the problem; no ticket was needed. */
    DEFLECTED,
    /** The person joined an ongoing major incident ("I'm affected too"). */
    JOINED_MAJOR_INCIDENT,
    /** The problem was already reported; no second ticket was created. */
    DUPLICATE_AVOIDED,
    CANCELLED;

    public boolean isFinal() {
        return this != OPEN;
    }
}
