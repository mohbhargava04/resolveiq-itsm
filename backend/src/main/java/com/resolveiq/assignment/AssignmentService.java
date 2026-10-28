package com.resolveiq.assignment;

import com.resolveiq.common.exception.BusinessRuleViolationException;
import com.resolveiq.common.exception.ResourceNotFoundException;
import com.resolveiq.common.exception.TechnicianUnavailableException;
import com.resolveiq.common.exception.WorkloadExceededException;
import com.resolveiq.incident.IncidentRepository;
import com.resolveiq.incident.domain.Actor;
import com.resolveiq.incident.domain.Incident;
import com.resolveiq.incident.domain.IncidentStatus;
import com.resolveiq.settings.SettingKey;
import com.resolveiq.settings.SettingsService;
import com.resolveiq.user.UserRepository;
import com.resolveiq.user.domain.Technician;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Context of the assignment Strategy pattern. Builds technician profiles from live data, delegates
 * ranking to the active (or explicitly requested) strategy, and enforces the assignment business
 * rules: technician must be active, on duty and below capacity.
 */
@Service
public class AssignmentService {

    public static final String MANUAL = "Manual assignment";

    private final Map<String, AssignmentStrategy> strategies = new LinkedHashMap<>();
    private final UserRepository users;
    private final IncidentRepository incidents;
    private final SettingsService settings;

    public AssignmentService(List<AssignmentStrategy> strategies, UserRepository users, IncidentRepository incidents,
                             SettingsService settings) {
        strategies.forEach(s -> this.strategies.put(s.key(), s));
        this.users = users;
        this.incidents = incidents;
        this.settings = settings;
    }

    public Collection<AssignmentStrategy> available() {
        return strategies.values();
    }

    public AssignmentStrategy activeStrategy() {
        return strategies.getOrDefault(settings.get(SettingKey.ASSIGNMENT_STRATEGY),
                strategies.get(SmartAssignmentStrategy.KEY));
    }

    public AssignmentStrategy require(String key) {
        AssignmentStrategy s = strategies.get(key == null ? "" : key.toUpperCase());
        if (s == null) {
            throw new BusinessRuleViolationException("Unknown assignment strategy '" + key + "'. Available: "
                    + strategies.keySet());
        }
        return s;
    }

    /** Ranked recommendations for an incident (does not assign). */
    public List<AssignmentCandidate> recommend(Incident incident, String strategyKey, Instant now) {
        AssignmentStrategy strategy = strategyKey == null ? activeStrategy() : require(strategyKey);
        return strategy.rank(requestFor(incident, now), buildPool(incident));
    }

    /**
     * Assigns using the active strategy. If it finds nobody (e.g. nobody has the skill), falls back to
     * load-based so a critical incident is never left unowned while someone is free.
     */
    public Technician autoAssign(Incident incident, Instant now) {
        AssignmentStrategy strategy = activeStrategy();
        List<TechnicianProfile> pool = buildPool(incident);
        AssignmentRequest request = requestFor(incident, now);
        List<AssignmentCandidate> ranked = strategy.rank(request, pool);
        String used = strategy.displayName();
        if (ranked.isEmpty() && !LoadBasedAssignmentStrategy.KEY.equals(strategy.key())) {
            ranked = strategies.get(LoadBasedAssignmentStrategy.KEY).rank(request, pool);
            used = strategy.displayName() + " → fallback load-based";
        }
        if (ranked.isEmpty()) {
            throw noOneAvailable(pool);
        }
        Technician chosen = ranked.getFirst().profile().technician();
        incident.assignTo(chosen, used, Actor.SYSTEM, now);
        return chosen;
    }

    /** Manual assignment by a team lead / administrator, with explicit rule checks. */
    public Technician assignManually(Incident incident, Long technicianId, Actor actor, Instant now) {
        Technician technician = users.findTechnicianById(technicianId)
                .orElseThrow(() -> new ResourceNotFoundException("Technician", technicianId));
        if (!technician.isActive()) {
            throw new TechnicianUnavailableException(technician.getFullName() + "'s account is deactivated");
        }
        if (!technician.isAvailable()) {
            throw new TechnicianUnavailableException(technician.getFullName() + " is currently off duty");
        }
        int active = (int) incidents.countByAssigneeIdAndStatusIn(technician.getId(), IncidentStatus.WORKLOAD);
        boolean alreadyTheirs = technician.equals(incident.getAssignee());
        if (!alreadyTheirs && !technician.hasCapacityFor(active)) {
            throw new WorkloadExceededException(technician.getFullName(), active, technician.getMaxActiveIncidents());
        }
        incident.assignTo(technician, MANUAL, actor, now);
        return technician;
    }

    public int activeIncidentCount(Long technicianId) {
        return (int) incidents.countByAssigneeIdAndStatusIn(technicianId, IncidentStatus.WORKLOAD);
    }

    /** Live workload of every technician, keyed by technician id. */
    public Map<Long, Integer> workloads() {
        return toCountMap(incidents.countActiveByAssignee(IncidentStatus.WORKLOAD));
    }

    List<TechnicianProfile> buildPool(Incident incident) {
        Map<Long, Integer> active = workloads();
        Map<Long, Integer> onService = incident.getService() == null ? Map.of()
                : toCountMap(incidents.countResolvedByAssigneeForService(incident.getService().getId(), IncidentStatus.DONE));
        Map<Long, Integer> inCategory = incident.getCategory() == null ? Map.of()
                : toCountMap(incidents.countResolvedByAssigneeForCategory(incident.getCategory().getId(), IncidentStatus.DONE));
        return users.findActiveTechnicians().stream()
                .map(t -> new TechnicianProfile(t, active.getOrDefault(t.getId(), 0),
                        onService.getOrDefault(t.getId(), 0), inCategory.getOrDefault(t.getId(), 0)))
                .toList();
    }

    private static AssignmentRequest requestFor(Incident i, Instant now) {
        return new AssignmentRequest(i.getNumber(), i.getCategory() == null ? null : i.getCategory().getCode(),
                i.getService().getCode(), i.getPriority(), now);
    }

    private static RuntimeException noOneAvailable(List<TechnicianProfile> pool) {
        boolean anyOnDuty = pool.stream().anyMatch(p -> p.technician().isAssignable());
        if (anyOnDuty) {
            TechnicianProfile least = pool.stream().filter(p -> p.technician().isAssignable())
                    .min((a, b) -> Double.compare(a.utilisation(), b.utilisation())).orElseThrow();
            return new WorkloadExceededException("Every on-duty technician (least busy: "
                    + least.technician().getFullName() + ")", least.activeIncidents(), least.capacity());
        }
        return new TechnicianUnavailableException("No technicians are on duty right now");
    }

    private static Map<Long, Integer> toCountMap(List<Object[]> rows) {
        Map<Long, Integer> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put(((Number) row[0]).longValue(), ((Number) row[1]).intValue());
        }
        return map;
    }
}
