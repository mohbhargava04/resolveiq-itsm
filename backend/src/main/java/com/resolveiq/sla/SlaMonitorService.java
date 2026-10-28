package com.resolveiq.sla;

import com.resolveiq.escalation.EscalationService;
import com.resolveiq.incident.IncidentRepository;
import com.resolveiq.incident.domain.Incident;
import com.resolveiq.incident.domain.IncidentStatus;
import com.resolveiq.incident.event.IncidentEvent;
import com.resolveiq.incident.event.IncidentEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Continuous SLA monitoring: scans every active incident, raises "approaching" warnings and breach
 * events exactly once each, and hands overdue incidents to the escalation chain.
 */
@Service
public class SlaMonitorService {

    private static final Logger log = LoggerFactory.getLogger(SlaMonitorService.class);

    /** Summary of one monitor run. */
    public record ScanResult(Instant ranAt, int scanned, int warnings, int breaches, int escalations) {
    }

    private final IncidentRepository incidents;
    private final EscalationService escalation;
    private final IncidentEventPublisher publisher;
    private final Clock clock;

    public SlaMonitorService(IncidentRepository incidents, EscalationService escalation,
                             IncidentEventPublisher publisher, Clock clock) {
        this.incidents = incidents;
        this.escalation = escalation;
        this.publisher = publisher;
        this.clock = clock;
    }

    @Transactional
    public ScanResult scan() {
        Instant now = clock.instant();
        int warnings = 0;
        int breaches = 0;
        int escalations = 0;
        List<IncidentEvent> events = new ArrayList<>();
        List<Incident> active = incidents.findByStatusIn(IncidentStatus.ACTIVE);
        for (Incident incident : active) {
            for (SlaRecord.Signal signal : incident.evaluateSla(now)) {
                if (signal.kind() == SlaRecord.Signal.Kind.BREACH) {
                    breaches++;
                } else {
                    warnings++;
                }
            }
            if (escalation.evaluate(incident, now).isPresent()) {
                escalations++;
            }
            events.addAll(incident.pullEvents());
        }
        publisher.publishAll(events);
        if (warnings + breaches + escalations > 0) {
            log.info("SLA monitor: {} active, {} warnings, {} breaches, {} escalations",
                    active.size(), warnings, breaches, escalations);
        }
        return new ScanResult(now, active.size(), warnings, breaches, escalations);
    }
}
