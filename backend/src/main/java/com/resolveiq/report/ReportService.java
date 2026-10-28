package com.resolveiq.report;

import com.resolveiq.incident.IncidentRepository;
import com.resolveiq.incident.domain.EscalationLevel;
import com.resolveiq.incident.domain.Incident;
import com.resolveiq.incident.domain.IncidentStatus;
import com.resolveiq.incident.domain.Priority;
import com.resolveiq.user.UserRepository;
import com.resolveiq.user.domain.Technician;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Operational analytics: KPIs (MTTA, MTTR, SLA compliance, CSAT), workload, breakdowns by service /
 * category / priority / status, daily trends and CSV export.
 *
 * <p>Computed with Java streams over the incident set, which is simple and fast for thousands of
 * incidents; at larger scale these become SQL aggregate queries or a reporting replica (see
 * docs/technology.md, "scaling").</p>
 */
@Service
@Transactional(readOnly = true)
public class ReportService {

    public record Overview(long totalIncidents, long openIncidents, long criticalOpen, long resolvedIncidents,
                           long closedIncidents, long slaBreaches, double slaCompliancePercent, Double mttaMinutes,
                           Double mttrMinutes, Double averageCsat, long escalatedOpen, long unassignedOpen,
                           long awaitingInfo, double autoClassifiedPercent, long duplicatesDetected) {
    }

    public record CountRow(String key, String label, long total, long open, long breached) {
    }

    public record TechnicianLoad(Long id, String name, String role, boolean available, int activeIncidents,
                                 int capacity, double utilisationPercent, long resolvedLast30Days,
                                 Double avgResolutionMinutes, Double csat, long slaBreaches, List<String> skills) {
    }

    public record DayRow(LocalDate date, long created, long resolved, long breached) {
    }

    public record SlaRow(String priority, long total, long met, long breached, double compliancePercent,
                         Double avgResponseMinutes, Double avgResolutionMinutes) {
    }

    private final IncidentRepository incidents;
    private final UserRepository users;
    private final Clock clock;
    private final ZoneId zone;

    public ReportService(IncidentRepository incidents, UserRepository users, Clock clock,
                         @Value("${itsm.display-zone:Asia/Kolkata}") String zone) {
        this.incidents = incidents;
        this.users = users;
        this.clock = clock;
        this.zone = ZoneId.of(zone);
    }

    public Overview overview(Instant from) {
        List<Incident> all = load(from);
        List<Incident> open = all.stream().filter(Incident::isActive).toList();
        List<Incident> withSla = all.stream().filter(i -> i.getSla().isStarted()).toList();
        long breached = withSla.stream().filter(i -> i.getSla().isBreached()).count();
        double compliance = withSla.isEmpty() ? 100 : pct(withSla.size() - breached, withSla.size());
        return new Overview(all.size(), open.size(),
                open.stream().filter(i -> i.getPriority() == Priority.P1).count(),
                all.stream().filter(i -> IncidentStatus.DONE.contains(i.getStatus())).count(),
                all.stream().filter(i -> i.getStatus() == IncidentStatus.CLOSED).count(),
                breached, round(compliance),
                avgMinutes(all, i -> i.getSla().responseTime()),
                avgMinutes(all, i -> i.getSla().resolutionTime()),
                avg(all.stream().filter(i -> i.getFeedback() != null && i.getFeedback().getRating() != null)
                        .mapToDouble(i -> i.getFeedback().getRating()).average()),
                open.stream().filter(i -> i.getEscalationLevel() != EscalationLevel.NONE).count(),
                open.stream().filter(i -> i.getAssignee() == null).count(),
                open.stream().filter(i -> i.getStatus() == IncidentStatus.AWAITING_INFO).count(),
                round(all.isEmpty() ? 0 : pct(all.stream().filter(Incident::isAutoClassified).count(), all.size())),
                all.stream().filter(i -> i.getDuplicateOf() != null).count());
    }

    public List<TechnicianLoad> technicianWorkload() {
        List<Incident> all = load(null);
        Instant monthAgo = clock.instant().minus(Duration.ofDays(30));
        List<TechnicianLoad> rows = new ArrayList<>();
        for (Technician t : users.findAllTechnicians()) {
            List<Incident> mine = all.stream().filter(i -> i.getAssignee() != null
                    && Objects.equals(i.getAssignee().getId(), t.getId())).toList();
            int active = (int) mine.stream().filter(i -> IncidentStatus.WORKLOAD.contains(i.getStatus())).count();
            List<Incident> resolved = mine.stream().filter(i -> i.getResolvedAt() != null).toList();
            rows.add(new TechnicianLoad(t.getId(), t.getFullName(), t.getRole().name(), t.isAvailable() && t.isActive(),
                    active, t.getMaxActiveIncidents(), round(pct(active, t.getMaxActiveIncidents())),
                    resolved.stream().filter(i -> i.getResolvedAt().isAfter(monthAgo)).count(),
                    avgMinutes(resolved, i -> i.getSla().resolutionTime()),
                    avg(mine.stream().filter(i -> i.getFeedback() != null && i.getFeedback().getRating() != null)
                            .mapToDouble(i -> i.getFeedback().getRating()).average()),
                    mine.stream().filter(i -> i.getSla().isBreached()).count(),
                    List.copyOf(t.getSkills())));
        }
        rows.sort(Comparator.comparingDouble(TechnicianLoad::utilisationPercent).reversed());
        return rows;
    }

    public List<CountRow> byService(Instant from) {
        return breakdown(load(from), i -> i.getService().getCode(), i -> i.getService().getName());
    }

    public List<CountRow> byCategory(Instant from) {
        return breakdown(load(from), i -> i.getCategory() == null ? "UNCATEGORISED" : i.getCategory().getCode(),
                i -> i.getCategory() == null ? "Uncategorised" : i.getCategory().getName());
    }

    public List<CountRow> byPriority(Instant from) {
        List<CountRow> rows = breakdown(load(from), i -> i.getPriority() == null ? "NONE" : i.getPriority().name(),
                i -> i.getPriority() == null ? "Not prioritised" : i.getPriority() + " " + i.getPriority().label());
        rows.sort(Comparator.comparing(CountRow::key));
        return rows;
    }

    public List<CountRow> byStatus(Instant from) {
        List<Incident> all = load(from);
        List<CountRow> rows = new ArrayList<>();
        for (IncidentStatus s : IncidentStatus.values()) {
            long count = all.stream().filter(i -> i.getStatus() == s).count();
            rows.add(new CountRow(s.name(), s.label(), count, s.isActive() ? count : 0,
                    all.stream().filter(i -> i.getStatus() == s && i.getSla().isBreached()).count()));
        }
        return rows;
    }

    public List<DayRow> trend(int days) {
        int n = Math.max(1, Math.min(days, 180));
        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate start = today.minusDays(n - 1L);
        List<Incident> all = load(start.atStartOfDay(zone).toInstant());
        Map<LocalDate, long[]> buckets = new LinkedHashMap<>();
        for (int d = 0; d < n; d++) {
            buckets.put(start.plusDays(d), new long[3]);
        }
        for (Incident i : all) {
            long[] created = buckets.get(LocalDate.ofInstant(i.getCreatedAt(), zone));
            if (created != null) {
                created[0]++;
                if (i.getSla().isBreached()) {
                    created[2]++;
                }
            }
            if (i.getResolvedAt() != null) {
                long[] resolved = buckets.get(LocalDate.ofInstant(i.getResolvedAt(), zone));
                if (resolved != null) {
                    resolved[1]++;
                }
            }
        }
        return buckets.entrySet().stream().map(e -> new DayRow(e.getKey(), e.getValue()[0], e.getValue()[1],
                e.getValue()[2])).toList();
    }

    public List<SlaRow> slaPerformance(Instant from) {
        List<Incident> all = load(from).stream().filter(i -> i.getSla().isStarted()).toList();
        List<SlaRow> rows = new ArrayList<>();
        for (Priority p : Priority.values()) {
            List<Incident> group = all.stream().filter(i -> i.getPriority() == p).toList();
            long breached = group.stream().filter(i -> i.getSla().isBreached()).count();
            long met = group.stream().filter(i -> !i.getSla().isBreached() && i.getResolvedAt() != null).count();
            rows.add(new SlaRow(p.name(), group.size(), met, breached,
                    round(group.isEmpty() ? 100 : pct(group.size() - breached, group.size())),
                    avgMinutes(group, i -> i.getSla().responseTime()),
                    avgMinutes(group, i -> i.getSla().resolutionTime())));
        }
        return rows;
    }

    /** RFC 4180 CSV of the incident register, for Excel. */
    public String exportCsv(Instant from) {
        StringBuilder sb = new StringBuilder(
                "Number,Title,Status,Priority,Category,Service,Reporter,Assignee,Created,Resolved,SLA status,Escalation\n");
        Instant now = clock.instant();
        for (Incident i : load(from)) {
            sb.append(csv(i.getNumber())).append(',').append(csv(i.getTitle())).append(',')
                    .append(i.getStatus()).append(',').append(i.getPriority() == null ? "" : i.getPriority()).append(',')
                    .append(csv(i.getCategory() == null ? "" : i.getCategory().getName())).append(',')
                    .append(csv(i.getService().getName())).append(',')
                    .append(csv(i.getReporter().getFullName())).append(',')
                    .append(csv(i.getAssignee() == null ? "" : i.getAssignee().getFullName())).append(',')
                    .append(i.getCreatedAt()).append(',')
                    .append(i.getResolvedAt() == null ? "" : i.getResolvedAt()).append(',')
                    .append(i.slaStatus(now)).append(',').append(i.getEscalationLevel()).append('\n');
        }
        return sb.toString();
    }

    // ---- helpers -----------------------------------------------------------------------------

    private List<Incident> load(Instant from) {
        return incidents.findCreatedSince(from == null ? Instant.EPOCH : from);
    }

    private static List<CountRow> breakdown(List<Incident> all, Function<Incident, String> key,
                                            Function<Incident, String> label) {
        Map<String, List<Incident>> groups = all.stream().collect(Collectors.groupingBy(key, LinkedHashMap::new,
                Collectors.toList()));
        List<CountRow> rows = new ArrayList<>();
        groups.forEach((k, list) -> rows.add(new CountRow(k, label.apply(list.getFirst()), list.size(),
                count(list, Incident::isActive), count(list, i -> i.getSla().isBreached()))));
        rows.sort(Comparator.comparingLong(CountRow::total).reversed());
        return rows;
    }

    private static long count(List<Incident> list, Predicate<Incident> p) {
        return list.stream().filter(p).count();
    }

    private static Double avgMinutes(List<Incident> list, Function<Incident, Duration> f) {
        return avg(list.stream().map(f).filter(Objects::nonNull).filter(d -> !d.isNegative())
                .mapToDouble(d -> d.toSeconds() / 60.0).average());
    }

    private static Double avg(OptionalDouble value) {
        return value.isPresent() ? round(value.getAsDouble()) : null;
    }

    private static double pct(double part, double whole) {
        return whole == 0 ? 0 : part * 100.0 / whole;
    }

    private static double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        String v = value.replace("\"", "\"\"");
        // neutralise spreadsheet formula injection
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        return "\"" + v + "\"";
    }
}
