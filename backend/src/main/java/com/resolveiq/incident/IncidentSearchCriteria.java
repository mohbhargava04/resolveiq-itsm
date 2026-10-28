package com.resolveiq.incident;

import com.resolveiq.incident.domain.IncidentStatus;
import com.resolveiq.incident.domain.Priority;
import com.resolveiq.sla.SlaStatus;

import java.time.LocalDate;
import java.util.List;

/**
 * Search and filter criteria for the incident register. Every field is optional and they combine
 * with AND. Twelve criteria are supported (the brief asks for at least five).
 *
 * @param text        free text matched against number, title and description
 * @param number      exact incident number
 * @param statuses    one or more lifecycle states
 * @param priorities  one or more priorities
 * @param category    category code
 * @param service     service code
 * @param assigneeId  technician id
 * @param reporterId  reporter id
 * @param slaStatus   ON_TRACK, AT_RISK, BREACHED, MET, PAUSED
 * @param escalated   only escalated incidents
 * @param openOnly    only incidents still needing work
 * @param createdFrom created on or after this date
 * @param createdTo   created on or before this date
 */
public record IncidentSearchCriteria(String text, String number, List<IncidentStatus> statuses,
                                     List<Priority> priorities, String category, String service, Long assigneeId,
                                     Long reporterId, SlaStatus slaStatus, Boolean escalated, Boolean openOnly,
                                     LocalDate createdFrom, LocalDate createdTo) {

    public static IncidentSearchCriteria none() {
        return new IncidentSearchCriteria(null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    /** Returns a copy restricted to one reporter (used to scope employees to their own incidents). */
    public IncidentSearchCriteria restrictedToReporter(Long reporter) {
        return new IncidentSearchCriteria(text, number, statuses, priorities, category, service, assigneeId, reporter,
                slaStatus, escalated, openOnly, createdFrom, createdTo);
    }
}
