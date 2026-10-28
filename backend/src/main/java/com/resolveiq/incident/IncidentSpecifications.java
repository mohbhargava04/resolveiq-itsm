package com.resolveiq.incident;

import com.resolveiq.incident.domain.EscalationLevel;
import com.resolveiq.incident.domain.Incident;
import com.resolveiq.incident.domain.IncidentStatus;
import com.resolveiq.sla.SlaStatus;
import jakarta.persistence.criteria.Path;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Translates {@link IncidentSearchCriteria} into a JPA Criteria query. Each criterion is a small
 * composable {@link Specification}; they are AND-ed together, so adding a new filter is one method.
 */
public final class IncidentSpecifications {

    private IncidentSpecifications() {
    }

    public static Specification<Incident> from(IncidentSearchCriteria c, Instant now, ZoneId zone) {
        List<Specification<Incident>> specs = new ArrayList<>();
        if (notBlank(c.text())) {
            String like = "%" + c.text().trim().toLowerCase() + "%";
            specs.add((r, q, cb) -> cb.or(
                    cb.like(cb.lower(r.get("number")), like),
                    cb.like(cb.lower(r.get("title")), like),
                    cb.like(cb.lower(r.get("description")), like)));
        }
        if (notBlank(c.number())) {
            specs.add((r, q, cb) -> cb.equal(cb.upper(r.get("number")), c.number().trim().toUpperCase()));
        }
        if (c.statuses() != null && !c.statuses().isEmpty()) {
            specs.add((r, q, cb) -> r.get("status").in(c.statuses()));
        }
        if (c.priorities() != null && !c.priorities().isEmpty()) {
            specs.add((r, q, cb) -> r.get("priority").in(c.priorities()));
        }
        if (notBlank(c.category())) {
            specs.add((r, q, cb) -> cb.equal(cb.upper(r.join("category").get("code")), c.category().trim().toUpperCase()));
        }
        if (notBlank(c.service())) {
            specs.add((r, q, cb) -> cb.equal(cb.upper(r.join("service").get("code")), c.service().trim().toUpperCase()));
        }
        if (c.assigneeId() != null) {
            specs.add((r, q, cb) -> cb.equal(r.get("assignee").get("id"), c.assigneeId()));
        }
        if (c.reporterId() != null) {
            specs.add((r, q, cb) -> cb.equal(r.get("reporter").get("id"), c.reporterId()));
        }
        if (Boolean.TRUE.equals(c.escalated())) {
            specs.add((r, q, cb) -> cb.notEqual(r.get("escalationLevel"), EscalationLevel.NONE));
        }
        if (Boolean.TRUE.equals(c.openOnly())) {
            specs.add((r, q, cb) -> r.get("status").in(IncidentStatus.ACTIVE));
        }
        if (c.createdFrom() != null) {
            Instant from = startOf(c.createdFrom(), zone);
            specs.add((r, q, cb) -> cb.greaterThanOrEqualTo(r.get("createdAt"), from));
        }
        if (c.createdTo() != null) {
            Instant to = startOf(c.createdTo().plusDays(1), zone);
            specs.add((r, q, cb) -> cb.lessThan(r.get("createdAt"), to));
        }
        if (c.slaStatus() != null) {
            specs.add(slaStatus(c.slaStatus(), now));
        }
        return Specification.allOf(specs);
    }

    /** SLA status is derived, so it is expressed in terms of the stored SLA columns. */
    static Specification<Incident> slaStatus(SlaStatus status, Instant now) {
        return (r, q, cb) -> {
            Path<Object> sla = r.get("sla");
            var responseBreached = cb.isTrue(sla.get("responseBreached"));
            var resolutionBreached = cb.isTrue(sla.get("resolutionBreached"));
            var breached = cb.or(responseBreached, resolutionBreached);
            var notBreached = cb.not(breached);
            var active = r.get("status").in(IncidentStatus.ACTIVE);
            return switch (status) {
                case BREACHED -> breached;
                case MET -> cb.and(notBreached, cb.isNotNull(sla.get("resolvedAt")));
                case PAUSED -> cb.and(notBreached, cb.isNotNull(sla.get("pausedAt")));
                case AT_RISK -> cb.and(active, notBreached, cb.isTrue(sla.get("warningIssued")),
                        cb.isNull(sla.get("pausedAt")));
                case ON_TRACK -> cb.and(active, notBreached, cb.isFalse(sla.get("warningIssued")),
                        cb.isNotNull(sla.get("startedAt")), cb.isNull(sla.get("pausedAt")));
                case NOT_STARTED -> cb.isNull(sla.get("startedAt"));
            };
        };
    }

    private static Instant startOf(LocalDate date, ZoneId zone) {
        return date.atStartOfDay(zone).toInstant();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
