package com.resolveiq.sla;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The SLA "stopwatch" of one incident, embedded in the incident row (<b>composition</b>: it has no
 * identity or life of its own).
 *
 * <p>It snapshots the targets of the policy that applied when the clock started, so later edits
 * to the policy do not silently move the deadlines of incidents already in flight. It supports
 * pausing while IT waits for the reporter (ITIL "pending customer"), which pushes the resolution
 * deadline back by the paused time.</p>
 */
@Embeddable
public class SlaRecord {

    @Column(name = "sla_policy_name", length = 100)
    private String policyName;

    @Column(name = "sla_response_minutes")
    private Integer responseMinutes;

    @Column(name = "sla_resolution_minutes")
    private Integer resolutionMinutes;

    @Column(name = "sla_warning_percent")
    private Integer warningPercent;

    @Column(name = "sla_started_at")
    private Instant startedAt;

    @Column(name = "sla_response_due_at")
    private Instant responseDueAt;

    @Column(name = "sla_resolution_due_at")
    private Instant resolutionDueAt;

    @Column(name = "sla_responded_at")
    private Instant respondedAt;

    @Column(name = "sla_resolved_at")
    private Instant resolvedAt;

    @Column(name = "sla_response_breached", nullable = false)
    private boolean responseBreached;

    @Column(name = "sla_resolution_breached", nullable = false)
    private boolean resolutionBreached;

    @Column(name = "sla_warning_issued", nullable = false)
    private boolean warningIssued;

    @Column(name = "sla_paused_at")
    private Instant pausedAt;

    @Column(name = "sla_paused_seconds", nullable = false)
    private long pausedSeconds;

    /** Something the SLA monitor should tell people about. */
    public record Signal(Kind kind, SlaTarget target, Instant dueAt) {
        public enum Kind { WARNING, BREACH }
    }

    public SlaRecord() {
    }

    // ---- clock control -----------------------------------------------------------------------

    public void start(SlaPolicy policy, Instant from) {
        this.policyName = policy.getName();
        this.responseMinutes = policy.getResponseMinutes();
        this.resolutionMinutes = policy.getResolutionMinutes();
        this.warningPercent = policy.getWarningThresholdPercent();
        this.startedAt = from;
        this.responseDueAt = from.plus(policy.responseTarget());
        this.resolutionDueAt = from.plus(policy.resolutionTarget());
        this.respondedAt = null;
        this.resolvedAt = null;
        this.responseBreached = false;
        this.resolutionBreached = false;
        this.warningIssued = false;
        this.pausedAt = null;
        this.pausedSeconds = 0;
    }

    /**
     * Re-targets the clock after a priority change. The original start time is kept (the customer
     * has been waiting since they reported), only the targets move.
     */
    public void retarget(SlaPolicy policy) {
        if (startedAt == null) {
            start(policy, Instant.now());
            return;
        }
        this.policyName = policy.getName();
        this.responseMinutes = policy.getResponseMinutes();
        this.resolutionMinutes = policy.getResolutionMinutes();
        this.warningPercent = policy.getWarningThresholdPercent();
        this.responseDueAt = startedAt.plus(policy.responseTarget());
        this.resolutionDueAt = startedAt.plus(policy.resolutionTarget()).plusSeconds(pausedSeconds);
        this.warningIssued = false;
    }

    /** @return true if the response was late (a new response breach) */
    public boolean markResponded(Instant at) {
        if (respondedAt != null || startedAt == null) {
            return false;
        }
        respondedAt = at;
        if (at.isAfter(responseDueAt) && !responseBreached) {
            responseBreached = true;
            return true;
        }
        return false;
    }

    /** @return true if the resolution was late (a new resolution breach) */
    public boolean markResolved(Instant at) {
        if (startedAt == null) {
            return false;
        }
        resume(at);
        resolvedAt = at;
        if (at.isAfter(resolutionDueAt) && !resolutionBreached) {
            resolutionBreached = true;
            return true;
        }
        return false;
    }

    /** The reporter rejected the fix: the resolution clock runs again. */
    public void reopen() {
        resolvedAt = null;
    }

    public void pause(Instant at) {
        if (pausedAt == null && startedAt != null && resolvedAt == null) {
            pausedAt = at;
        }
    }

    public void resume(Instant at) {
        if (pausedAt != null) {
            long seconds = Math.max(0, Duration.between(pausedAt, at).getSeconds());
            pausedSeconds += seconds;
            resolutionDueAt = resolutionDueAt.plusSeconds(seconds);
            pausedAt = null;
        }
    }

    /**
     * Checks the clocks against {@code now} and returns only <em>new</em> signals, so the monitor
     * can run every few seconds without spamming duplicate warnings.
     */
    public List<Signal> evaluate(Instant now) {
        List<Signal> signals = new ArrayList<>();
        if (startedAt == null) {
            return signals;
        }
        if (respondedAt == null && !responseBreached && now.isAfter(responseDueAt)) {
            responseBreached = true;
            signals.add(new Signal(Signal.Kind.BREACH, SlaTarget.RESPONSE, responseDueAt));
        }
        if (resolvedAt == null && pausedAt == null) {
            if (!resolutionBreached && now.isAfter(resolutionDueAt)) {
                resolutionBreached = true;
                warningIssued = true;
                signals.add(new Signal(Signal.Kind.BREACH, SlaTarget.RESOLUTION, resolutionDueAt));
            } else if (!warningIssued && !resolutionBreached && elapsedFraction(now) >= warningPercent / 100.0) {
                warningIssued = true;
                signals.add(new Signal(Signal.Kind.WARNING, SlaTarget.RESOLUTION, resolutionDueAt));
            }
        }
        return signals;
    }

    // ---- queries -----------------------------------------------------------------------------

    public boolean isStarted() {
        return startedAt != null;
    }

    public boolean isPaused() {
        return pausedAt != null;
    }

    public boolean isBreached() {
        return responseBreached || resolutionBreached;
    }

    /** Fraction (0..1+) of the resolution window used up, excluding paused time. */
    public double elapsedFraction(Instant now) {
        if (startedAt == null || resolutionMinutes == null) {
            return 0;
        }
        Instant end = resolvedAt != null ? resolvedAt : (pausedAt != null ? pausedAt : now);
        long worked = Duration.between(startedAt, end).getSeconds() - pausedSeconds;
        return Math.max(0, worked) / (resolutionMinutes * 60.0);
    }

    /** Time left until the resolution deadline (negative when overdue). */
    public Duration remaining(Instant now) {
        if (resolutionDueAt == null) {
            return Duration.ZERO;
        }
        Instant reference = pausedAt != null ? pausedAt : now;
        return Duration.between(reference, resolutionDueAt);
    }

    public SlaStatus status(Instant now) {
        if (startedAt == null) {
            return SlaStatus.NOT_STARTED;
        }
        if (isBreached()) {
            return SlaStatus.BREACHED;
        }
        if (resolvedAt != null) {
            return SlaStatus.MET;
        }
        if (pausedAt != null) {
            return SlaStatus.PAUSED;
        }
        if (now.isAfter(resolutionDueAt) || (respondedAt == null && now.isAfter(responseDueAt))) {
            return SlaStatus.BREACHED;
        }
        return elapsedFraction(now) >= (warningPercent == null ? 75 : warningPercent) / 100.0
                ? SlaStatus.AT_RISK : SlaStatus.ON_TRACK;
    }

    public Duration responseTime() {
        return respondedAt == null || startedAt == null ? null : Duration.between(startedAt, respondedAt);
    }

    public Duration resolutionTime() {
        return resolvedAt == null || startedAt == null ? null
                : Duration.between(startedAt, resolvedAt).minusSeconds(pausedSeconds);
    }

    public String getPolicyName() {
        return policyName;
    }

    public Integer getResponseMinutes() {
        return responseMinutes;
    }

    public Integer getResolutionMinutes() {
        return resolutionMinutes;
    }

    public Integer getWarningPercent() {
        return warningPercent;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getResponseDueAt() {
        return responseDueAt;
    }

    public Instant getResolutionDueAt() {
        return resolutionDueAt;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public boolean isResponseBreached() {
        return responseBreached;
    }

    public boolean isResolutionBreached() {
        return resolutionBreached;
    }

    public boolean isWarningIssued() {
        return warningIssued;
    }

    public Instant getPausedAt() {
        return pausedAt;
    }

    public long getPausedSeconds() {
        return pausedSeconds;
    }
}
