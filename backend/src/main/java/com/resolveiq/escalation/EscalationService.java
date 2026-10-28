package com.resolveiq.escalation;

import com.resolveiq.common.exception.BusinessRuleViolationException;
import com.resolveiq.incident.domain.Actor;
import com.resolveiq.incident.domain.EscalationLevel;
import com.resolveiq.incident.domain.Incident;
import com.resolveiq.settings.SettingKey;
import com.resolveiq.settings.SettingsService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * <b>Intelligent feature C - Automatic Escalation.</b> Builds the chain
 * Team Lead → System Administrator → IT Manager once, then runs incidents through it.
 */
@Service
public class EscalationService {

    private final EscalationHandler chain;
    private final List<EscalationHandler> handlers;
    private final SettingsService settings;

    public EscalationService(TeamLeadEscalationHandler teamLead, AdministratorEscalationHandler administrator,
                             ItManagerEscalationHandler manager, SettingsService settings) {
        teamLead.linkWith(administrator).linkWith(manager);
        this.chain = teamLead;
        this.handlers = List.of(teamLead, administrator, manager);
        this.settings = settings;
    }

    /** Automatic escalation, called by the SLA monitor. @return the decision applied, if any */
    public Optional<EscalationDecision> evaluate(Incident incident, Instant now) {
        if (!incident.isActive() || incident.getEscalationLevel().isTop() || !settings.isEnabled(SettingKey.AUTO_ESCALATION)) {
            return Optional.empty();
        }
        Optional<EscalationDecision> decision = chain.handle(incident, now);
        decision.ifPresent(d -> incident.escalate(d.level(), d.target(), d.reason(), Actor.SYSTEM, now));
        return decision;
    }

    /** Manual escalation by the assignee or a supervisor: one level up. */
    public EscalationDecision escalateManually(Incident incident, String reason, Actor actor, Instant now) {
        EscalationLevel current = incident.getEscalationLevel();
        if (current.isTop()) {
            throw new BusinessRuleViolationException(incident.getNumber() + " is already escalated to the IT manager");
        }
        EscalationLevel target = current.next();
        EscalationHandler handler = handlers.stream().filter(h -> h.level() == target).findFirst().orElseThrow();
        String why = reason == null || reason.isBlank() ? "Escalated manually by " + actor.name() : reason.trim();
        EscalationDecision decision = new EscalationDecision(target, handler.resolveTarget(incident).orElse(null), why);
        incident.escalate(decision.level(), decision.target(), decision.reason(), actor, now);
        return decision;
    }
}
