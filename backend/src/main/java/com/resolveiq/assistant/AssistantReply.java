package com.resolveiq.assistant;

import java.util.List;

/**
 * One turn of the assistant, as sent to the browser: what it says, what it is asking now (if the
 * conversation is still open) and, once finished, how it ended.
 */
public record AssistantReply(Long sessionId, ConversationStep step, boolean done, ConversationOutcome outcome,
                             List<String> messages, Prompt prompt, String incidentNumber, Long articleId) {

    static AssistantReply of(ChatSession session, List<String> messages, Prompt prompt) {
        return new AssistantReply(session.getId(), session.getStep(), session.isFinished(), session.getOutcome(),
                List.copyOf(messages), session.isFinished() ? null : prompt, session.getIncidentNumber(),
                session.getDeflectedArticleId());
    }
}
