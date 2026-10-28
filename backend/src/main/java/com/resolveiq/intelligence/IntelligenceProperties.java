package com.resolveiq.intelligence;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tunable thresholds for the intelligent features (application.yml → {@code itsm.intelligence.*}).
 *
 * @param duplicateSuspectThreshold combined score above which an incident is flagged as a possible duplicate
 * @param duplicateBlockThreshold   score above which the <em>same reporter's</em> submission is rejected
 * @param duplicateWindowHours      only incidents reported within this window are compared
 * @param correlationWindowMinutes  incidents within this window are considered for correlation
 * @param correlationMinIncidents   minimum incidents for a correlation cluster
 */
@ConfigurationProperties(prefix = "itsm.intelligence")
public record IntelligenceProperties(double duplicateSuspectThreshold, double duplicateBlockThreshold,
                                     int duplicateWindowHours, int correlationWindowMinutes,
                                     int correlationMinIncidents) {

    public IntelligenceProperties {
        if (duplicateSuspectThreshold <= 0) {
            duplicateSuspectThreshold = 0.45;
        }
        if (duplicateBlockThreshold <= 0) {
            duplicateBlockThreshold = 0.85;
        }
        if (duplicateWindowHours <= 0) {
            duplicateWindowHours = 72;
        }
        if (correlationWindowMinutes <= 0) {
            correlationWindowMinutes = 180;
        }
        if (correlationMinIncidents <= 1) {
            correlationMinIncidents = 3;
        }
    }
}
