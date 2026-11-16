package com.resolveiq.assistant;

/**
 * The steps of a "Report with assistant" conversation, in the order they are considered.
 * A step is skipped when it has nothing to ask (see {@code ConversationFlow}).
 */
public enum ConversationStep {
    /** "What's wrong?" - free text. */
    DESCRIBE_PROBLEM,
    /** Confirm (or choose) the affected service. */
    CONFIRM_SERVICE,
    /** An open major incident looks related: "I'm affected too" instead of a new report. */
    OFFER_MAJOR_INCIDENT,
    /** Knowledge-base articles that may fix it without a ticket (self-help deflection). */
    OFFER_SELF_HELP,
    /** Similar open incidents were found. */
    WARN_DUPLICATE,
    ASK_IMPACT,
    ASK_URGENCY,
    /** Only for hardware/network problems that do not name a device. */
    ASK_DEVICE,
    /** Summary to confirm before the incident is created. */
    CONFIRM_SUMMARY,
    /** Terminal: the conversation has ended (see {@link ConversationOutcome}). */
    FINISHED
}
