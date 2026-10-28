package com.resolveiq.escalation;

import com.resolveiq.incident.domain.EscalationLevel;
import com.resolveiq.incident.domain.Incident;
import com.resolveiq.notification.RecipientDirectory;
import com.resolveiq.user.domain.User;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/** Level 1: a P1/P2 not acknowledged by its response deadline, or any resolution breach. */
@Component
public class TeamLeadEscalationHandler extends EscalationHandler {

    private final RecipientDirectory directory;

    public TeamLeadEscalationHandler(RecipientDirectory directory) {
        this.directory = directory;
    }

    @Override
    public EscalationLevel level() {
        return EscalationLevel.TEAM_LEAD;
    }

    @Override
    protected Optional<String> triggerReason(Incident i, Instant now) {
        if (isUnacknowledgedHighPriority(i) && now.isAfter(i.getSla().getResponseDueAt())) {
            return Optional.of(i.getPriority() + " incident not acknowledged within its "
                    + i.getSla().getResponseMinutes() + "-minute response target");
        }
        if (i.getSla().isResolutionBreached()) {
            return Optional.of("Resolution SLA breached");
        }
        return Optional.empty();
    }

    @Override
    public Optional<? extends User> resolveTarget(Incident incident) {
        return directory.teamLeadFor(incident.getAssignee());
    }
}
