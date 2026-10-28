package com.resolveiq.escalation;

import com.resolveiq.incident.domain.EscalationLevel;
import com.resolveiq.user.domain.User;

/** Outcome of the escalation chain: move to {@code level}, notify {@code target}, because {@code reason}. */
public record EscalationDecision(EscalationLevel level, User target, String reason) {
}
