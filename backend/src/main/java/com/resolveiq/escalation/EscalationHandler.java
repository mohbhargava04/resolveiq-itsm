package com.resolveiq.escalation;

import com.resolveiq.incident.domain.EscalationLevel;
import com.resolveiq.incident.domain.Incident;
import com.resolveiq.user.domain.User;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * <b>Chain of Responsibility pattern</b>: each handler is responsible for escalating <em>into</em> one
 * level of the hierarchy (Team Lead, System Administrator, IT Manager). An incident is passed along
 * the chain until the handler for its next level decides whether the trigger condition is met.
 *
 * <p>New levels (e.g. "CIO") are added by writing one handler and linking it into the chain - no
 * existing handler changes.</p>
 */
public abstract class EscalationHandler {

    private EscalationHandler next;

    /** Links the next handler and returns it, allowing fluent chain construction a.linkWith(b).linkWith(c). */
    public EscalationHandler linkWith(EscalationHandler next) {
        this.next = next;
        return next;
    }

    public final Optional<EscalationDecision> handle(Incident incident, Instant now) {
        if (incident.getEscalationLevel().next() == level() && incident.getEscalationLevel() != level()) {
            if (!cooledDown(incident, now)) {
                return Optional.empty();
            }
            Optional<String> reason = triggerReason(incident, now);
            if (reason.isPresent()) {
                return Optional.of(new EscalationDecision(level(), resolveTarget(incident).orElse(null), reason.get()));
            }
            return Optional.empty();
        }
        return next == null ? Optional.empty() : next.handle(incident, now);
    }

    /** Level this handler escalates into. */
    public abstract EscalationLevel level();

    /** Why the incident must escalate now, or empty if it need not. */
    protected abstract Optional<String> triggerReason(Incident incident, Instant now);

    /** Who receives the escalation at this level. */
    public abstract Optional<? extends User> resolveTarget(Incident incident);

    /** Avoids jumping several levels in consecutive monitor runs: wait one response window between levels. */
    private boolean cooledDown(Incident incident, Instant now) {
        if (incident.getEscalatedAt() == null) {
            return true;
        }
        return !now.isBefore(incident.getEscalatedAt().plus(responseWindow(incident)));
    }

    protected static Duration responseWindow(Incident incident) {
        Integer minutes = incident.getSla().getResponseMinutes();
        return Duration.ofMinutes(Math.max(5, minutes == null ? 15 : minutes));
    }

    protected static boolean isUnacknowledgedHighPriority(Incident incident) {
        return incident.getPriority() != null && incident.getPriority().isHighPriority()
                && incident.getSla().getRespondedAt() == null && incident.getSla().isStarted();
    }

    protected static Duration overdueBy(Instant due, Instant now) {
        return due == null || now.isBefore(due) ? Duration.ZERO : Duration.between(due, now);
    }
}
