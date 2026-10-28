package com.resolveiq.escalation;

import com.resolveiq.incident.domain.EscalationLevel;
import com.resolveiq.incident.domain.Incident;
import com.resolveiq.incident.domain.Priority;
import com.resolveiq.notification.RecipientDirectory;
import com.resolveiq.user.domain.User;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/** Level 2: still unacknowledged one response window after the deadline, or a P1 resolution breach. */
@Component
public class AdministratorEscalationHandler extends EscalationHandler {

    private final RecipientDirectory directory;

    public AdministratorEscalationHandler(RecipientDirectory directory) {
        this.directory = directory;
    }

    @Override
    public EscalationLevel level() {
        return EscalationLevel.ADMINISTRATOR;
    }

    @Override
    protected Optional<String> triggerReason(Incident i, Instant now) {
        if (isUnacknowledgedHighPriority(i)
                && overdueBy(i.getSla().getResponseDueAt(), now).compareTo(responseWindow(i)) >= 0) {
            return Optional.of("Still unacknowledged after team-lead escalation");
        }
        if (i.getPriority() == Priority.P1 && i.getSla().isResolutionBreached()) {
            return Optional.of("Critical incident has breached its resolution SLA");
        }
        return Optional.empty();
    }

    @Override
    public Optional<? extends User> resolveTarget(Incident incident) {
        return directory.systemAdministrators().stream().findFirst();
    }
}
