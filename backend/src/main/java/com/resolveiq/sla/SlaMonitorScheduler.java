package com.resolveiq.sla;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Background job that runs the SLA monitor on a fixed delay (default every 30 s). Disabled in tests,
 * which call {@link SlaMonitorService#scan()} directly with a controllable clock.
 */
@Component
@ConditionalOnProperty(name = "itsm.sla.monitor-enabled", havingValue = "true", matchIfMissing = true)
public class SlaMonitorScheduler {

    private static final Logger log = LoggerFactory.getLogger(SlaMonitorScheduler.class);

    private final SlaMonitorService monitor;

    public SlaMonitorScheduler(SlaMonitorService monitor) {
        this.monitor = monitor;
    }

    @Scheduled(fixedDelayString = "${itsm.sla.monitor-interval-ms:30000}", initialDelay = 10_000)
    public void run() {
        try {
            monitor.scan();
        } catch (RuntimeException ex) {
            // never let one failed run stop future runs
            log.error("SLA monitor run failed", ex);
        }
    }
}
