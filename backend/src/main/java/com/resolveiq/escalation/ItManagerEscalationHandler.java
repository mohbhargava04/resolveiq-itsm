package com.resolveiq.escalation;

import com.resolveiq.incident.domain.EscalationLevel;
import com.resolveiq.incident.domain.Incident;
import com.resolveiq.incident.domain.Priority;
import com.resolveiq.notification.RecipientDirectory;
import com.resolveiq.user.domain.User;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** Level 3 (top): unacknowledged two windows past the deadline, or a P1 overdue by half its window. */
@Component
public class ItManagerEscalationHandler extends EscalationHandler {

    private final RecipientDirectory directory;

    public ItManagerEscalationHandler(RecipientDirectory directory) {
        this.directory = directory;
    }

    @Override
    public EscalationLevel level() {
        return EscalationLevel.IT_MANAGER;
    }

    @Override
    protected Optional<String> triggerReason(Incident i, Instant now) {
        if (isUnacknowledgedHighPriority(i)
                && overdueBy(i.getSla().getResponseDueAt(), now).compareTo(responseWindow(i).multipliedBy(2)) >= 0) {
            return Optional.of("No response after escalation to team lead and system administrator");
        }
        Integer resolution = i.getSla().getResolutionMinutes();
        if (i.getPriority() == Priority.P1 && resolution != null
                && overdueBy(i.getSla().getResolutionDueAt(), now).compareTo(Duration.ofMinutes(resolution / 2)) >= 0) {
            return Optional.of("Critical incident overdue by more than 50% of its resolution window");
        }
        return Optional.empty();
    }

    @Override
    public Optional<? extends User> resolveTarget(Incident incident) {
        return directory.managers().stream().findFirst();
    }
}
