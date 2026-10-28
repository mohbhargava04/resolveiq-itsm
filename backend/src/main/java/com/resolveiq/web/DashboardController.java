package com.resolveiq.web;

import com.resolveiq.assignment.AssignmentService;
import com.resolveiq.audit.AuditRepository;
import com.resolveiq.incident.IncidentRepository;
import com.resolveiq.incident.domain.Incident;
import com.resolveiq.incident.domain.IncidentStatus;
import com.resolveiq.intelligence.IncidentCorrelator;
import com.resolveiq.intelligence.SlaRiskAssessor;
import com.resolveiq.intelligence.SlaRiskAssessor.Level;
import com.resolveiq.report.ReportService;
import com.resolveiq.security.CurrentUserService;
import com.resolveiq.user.domain.Role;
import com.resolveiq.user.domain.Technician;
import com.resolveiq.user.domain.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One call that returns everything a role's home screen needs. Each role sees a different
 * dashboard (the brief requires distinct experiences per user type).
 */
@RestController
@Transactional(readOnly = true)
@Tag(name = "Dashboard")
public class DashboardController {

    private final CurrentUserService currentUser;
    private final IncidentRepository incidents;
    private final IncidentMapper mapper;
    private final ReportService reports;
    private final IncidentCorrelator correlator;
    private final SlaRiskAssessor risk;
    private final AssignmentService assignment;
    private final AuditRepository audit;
    private final Clock clock;

    public DashboardController(CurrentUserService currentUser, IncidentRepository incidents, IncidentMapper mapper,
                               ReportService reports, IncidentCorrelator correlator, SlaRiskAssessor risk,
                               AssignmentService assignment, AuditRepository audit, Clock clock) {
        this.currentUser = currentUser;
        this.incidents = incidents;
        this.mapper = mapper;
        this.reports = reports;
        this.correlator = correlator;
        this.risk = risk;
        this.assignment = assignment;
        this.audit = audit;
        this.clock = clock;
    }

    @GetMapping("/api/dashboard")
    @Operation(summary = "Role-specific dashboard data for the signed-in user")
    public Map<String, Object> dashboard() {
        User me = currentUser.require();
        Instant now = clock.instant();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("role", me.getRole().name());

        if (me.getRole() == Role.EMPLOYEE) {
            List<Incident> mine = incidents.findCreatedSince(Instant.EPOCH).stream()
                    .filter(i -> i.getReporter().getId().equals(me.getId()))
                    .sorted(Comparator.comparing(Incident::getCreatedAt).reversed()).toList();
            result.put("open", mine.stream().filter(Incident::isActive).count());
            result.put("needsMyAction", mine.stream().filter(i -> i.getStatus() == IncidentStatus.AWAITING_INFO
                    || i.getStatus() == IncidentStatus.RESOLVED).map(mapper::toSummary).toList());
            result.put("resolved", mine.stream().filter(i -> IncidentStatus.DONE.contains(i.getStatus())).count());
            result.put("recent", mine.stream().limit(6).map(mapper::toSummary).toList());
            return result;
        }

        List<Incident> active = incidents.findByStatusIn(IncidentStatus.ACTIVE);
        Map<Long, Integer> workloads = assignment.workloads();

        if (me instanceof Technician t) {
            List<Incident> queue = active.stream()
                    .filter(i -> i.getAssignee() != null && Objects.equals(i.getAssignee().getId(), me.getId()))
                    .sorted(Comparator.comparing(i -> i.getSla().getResolutionDueAt() == null ? Instant.MAX
                            : i.getSla().getResolutionDueAt()))
                    .toList();
            int load = workloads.getOrDefault(me.getId(), 0);
            result.put("available", t.isAvailable());
            result.put("activeIncidents", load);
            result.put("capacity", t.getMaxActiveIncidents());
            result.put("toAcknowledge", queue.stream().filter(i -> i.getStatus() == IncidentStatus.ASSIGNED).count());
            result.put("myQueue", queue.stream().map(i -> withRisk(i, load, t.getMaxActiveIncidents(), now)).toList());
        }

        if (me.getRole().isManagement()) {
            result.put("overview", reports.overview(null));
            result.put("unassigned", active.stream().filter(i -> i.getAssignee() == null)
                    .map(mapper::toSummary).toList());
            result.put("atRisk", active.stream()
                    .map(i -> {
                        int load = i.getAssignee() == null ? 0 : workloads.getOrDefault(i.getAssignee().getId(), 0);
                        int cap = i.getAssignee() == null ? 1 : i.getAssignee().getMaxActiveIncidents();
                        return Map.entry(i, risk.assess(i, load, cap, now));
                    })
                    .filter(e -> e.getValue().level() == Level.HIGH || e.getValue().level() == Level.BREACHED
                            || e.getValue().level() == Level.MEDIUM)
                    .sorted(Comparator.comparingDouble((Map.Entry<Incident, SlaRiskAssessor.SlaRisk> e) -> e.getValue().score())
                            .reversed())
                    .limit(8)
                    .map(e -> Map.of("incident", mapper.toSummary(e.getKey()), "risk", e.getValue()))
                    .toList());
            result.put("correlations", correlator.findClusters(now));
            result.put("byPriority", reports.byPriority(null));
            result.put("recentActivity", audit.findTop15ByIncidentNumberIsNotNullOrderByOccurredAtDescIdDesc().stream()
                    .map(IncidentMapper::toAudit).toList());
        }
        return result;
    }

    private Map<String, Object> withRisk(Incident i, int load, int capacity, Instant now) {
        return Map.of("incident", mapper.toSummary(i), "risk", risk.assess(i, load, capacity, now));
    }
}
