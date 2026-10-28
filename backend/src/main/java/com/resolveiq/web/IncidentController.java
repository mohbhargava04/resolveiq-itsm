package com.resolveiq.web;

import com.resolveiq.assignment.AssignmentService;
import com.resolveiq.audit.AuditService;
import com.resolveiq.incident.IncidentSearchCriteria;
import com.resolveiq.incident.IncidentService;
import com.resolveiq.incident.ReportIncidentCommand;
import com.resolveiq.incident.domain.Incident;
import com.resolveiq.incident.domain.IncidentStatus;
import com.resolveiq.incident.domain.Priority;
import com.resolveiq.intelligence.DuplicateDetector;
import com.resolveiq.intelligence.ResolutionRecommender;
import com.resolveiq.intelligence.SlaRiskAssessor;
import com.resolveiq.security.CurrentUserService;
import com.resolveiq.sla.SlaStatus;
import com.resolveiq.user.domain.User;
import com.resolveiq.web.dto.CommonDtos.PageResponse;
import com.resolveiq.web.dto.IncidentDtos.AssignRequest;
import com.resolveiq.web.dto.IncidentDtos.AuditEntry;
import com.resolveiq.web.dto.IncidentDtos.DuplicateRequest;
import com.resolveiq.web.dto.IncidentDtos.FeedbackRequest;
import com.resolveiq.web.dto.IncidentDtos.IncidentDetail;
import com.resolveiq.web.dto.IncidentDtos.IncidentSummary;
import com.resolveiq.web.dto.IncidentDtos.NoteRequest;
import com.resolveiq.web.dto.IncidentDtos.OptionalTextRequest;
import com.resolveiq.web.dto.IncidentDtos.ReportIncidentRequest;
import com.resolveiq.web.dto.IncidentDtos.ReportResponse;
import com.resolveiq.web.dto.IncidentDtos.ReprioritiseRequest;
import com.resolveiq.web.dto.IncidentDtos.ResolveRequest;
import com.resolveiq.web.dto.IncidentDtos.SimilarIncidentView;
import com.resolveiq.web.dto.IncidentDtos.TextRequest;
import com.resolveiq.web.dto.IncidentDtos.UpdateIncidentRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * REST API for the incident lifecycle. Lifecycle operations are modelled as action sub-resources
 * ({@code POST /incidents/{number}/acknowledge}) because each is a business command with its own
 * rules, not a generic field update.
 */
@RestController
@RequestMapping("/api/incidents")
@Tag(name = "Incidents", description = "Report, search and work incidents through their lifecycle")
@Transactional // the command and the mapping of its result share one transaction (lazy associations stay loadable)
public class IncidentController {

    private static final Set<String> SORTABLE = Set.of("createdAt", "updatedAt", "priority", "status", "number");

    private final IncidentService service;
    private final CurrentUserService currentUser;
    private final IncidentMapper mapper;
    private final AuditService audit;
    private final AssignmentService assignment;
    private final DuplicateDetector duplicates;
    private final ResolutionRecommender resolutions;
    private final SlaRiskAssessor slaRisk;
    private final Clock clock;

    public IncidentController(IncidentService service, CurrentUserService currentUser, IncidentMapper mapper,
                              AuditService audit, AssignmentService assignment, DuplicateDetector duplicates,
                              ResolutionRecommender resolutions, SlaRiskAssessor slaRisk, Clock clock) {
        this.service = service;
        this.currentUser = currentUser;
        this.mapper = mapper;
        this.audit = audit;
        this.assignment = assignment;
        this.duplicates = duplicates;
        this.resolutions = resolutions;
        this.slaRisk = slaRisk;
        this.clock = clock;
    }

    @PostMapping
    @Operation(summary = "Report an incident (auto-classified, prioritised, SLA started, auto-assigned)")
    public ResponseEntity<ReportResponse> report(@Valid @RequestBody ReportIncidentRequest r) {
        User me = currentUser.require();
        IncidentService.ReportOutcome outcome = service.report(new ReportIncidentCommand(r.title(), r.description(),
                r.serviceCode(), r.categoryCode(), r.impact(), r.urgency(), r.affectedUsers(),
                r.acknowledgeDuplicates()), me);
        List<SimilarIncidentView> similar = outcome.similarIncidents().stream().map(mapper::toSimilar).toList();
        return ResponseEntity.status(HttpStatus.CREATED).body(new ReportResponse(
                mapper.toDetail(outcome.incident(), me), similar, outcome.categorySuggestion(),
                outcome.assignmentMessage()));
    }

    @GetMapping
    @Transactional(readOnly = true)
    @Operation(summary = "Search and filter incidents (employees only see their own)")
    public PageResponse<IncidentSummary> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String number,
            @RequestParam(required = false) List<IncidentStatus> status,
            @RequestParam(required = false) List<Priority> priority,
            @RequestParam(required = false) String category,
            @RequestParam(name = "service", required = false) String serviceCode,
            @RequestParam(required = false) Long assigneeId,
            @RequestParam(required = false) Long reporterId,
            @RequestParam(required = false) SlaStatus slaStatus,
            @RequestParam(required = false) Boolean escalated,
            @RequestParam(required = false) Boolean openOnly,
            @RequestParam(required = false) Boolean mine,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        User me = currentUser.require();
        Long assignee = Boolean.TRUE.equals(mine) && me.canHandleIncidents() ? me.getId() : assigneeId;
        IncidentSearchCriteria criteria = new IncidentSearchCriteria(q, number, status, priority, category, serviceCode,
                assignee, reporterId, slaStatus, escalated, openOnly, from, to);
        String sortField = SORTABLE.contains(sort) ? sort : "createdAt";
        Sort order = Sort.by("asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC, sortField);
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100), order);
        return PageResponse.of(service.search(criteria, pageable, me), mapper::toSummary);
    }

    @GetMapping("/{number}")
    @Transactional(readOnly = true)
    public IncidentDetail get(@PathVariable String number) {
        User me = currentUser.require();
        return mapper.toDetail(service.get(number, me), me);
    }

    @GetMapping("/{number}/audit")
    @Transactional(readOnly = true)
    @Operation(summary = "Complete audit history of one incident")
    public List<AuditEntry> history(@PathVariable String number) {
        User me = currentUser.require();
        Incident incident = service.get(number, me);
        return audit.forIncident(incident.getNumber()).stream().map(IncidentMapper::toAudit).toList();
    }

    @PatchMapping("/{number}")
    public IncidentDetail update(@PathVariable String number, @Valid @RequestBody UpdateIncidentRequest r) {
        return act(me -> service.updateDetails(number, r.title(), r.description(), r.impact(), r.urgency(),
                r.affectedUsers(), me));
    }

    @PostMapping("/{number}/assign")
    @PreAuthorize("hasRole('TEAM_LEAD')")
    @Operation(summary = "Assign to a chosen technician, or by a strategy (SKILL, LOAD, ROUND_ROBIN, EXPERIENCE, SMART)")
    public IncidentDetail assign(@PathVariable String number, @RequestBody AssignRequest r) {
        return act(me -> service.assign(number, r.technicianId(), r.strategy(), me));
    }

    @PostMapping("/{number}/acknowledge")
    @PreAuthorize("hasRole('TECHNICIAN')")
    public IncidentDetail acknowledge(@PathVariable String number) {
        return act(me -> service.acknowledge(number, me));
    }

    @PostMapping("/{number}/start")
    @PreAuthorize("hasRole('TECHNICIAN')")
    public IncidentDetail start(@PathVariable String number) {
        return act(me -> service.startWork(number, me));
    }

    @PostMapping("/{number}/request-info")
    @PreAuthorize("hasRole('TECHNICIAN')")
    public IncidentDetail requestInfo(@PathVariable String number, @Valid @RequestBody TextRequest r) {
        return act(me -> service.requestInformation(number, r.text(), me));
    }

    @PostMapping("/{number}/provide-info")
    public IncidentDetail provideInfo(@PathVariable String number, @Valid @RequestBody TextRequest r) {
        return act(me -> service.provideInformation(number, r.text(), me));
    }

    @PostMapping("/{number}/resolve")
    @PreAuthorize("hasRole('TECHNICIAN')")
    public IncidentDetail resolve(@PathVariable String number, @Valid @RequestBody ResolveRequest r) {
        return act(me -> service.resolve(number, r.summary(), r.rootCause(), me));
    }

    @PostMapping("/{number}/verify")
    public IncidentDetail verify(@PathVariable String number) {
        return act(me -> service.verify(number, me));
    }

    @PostMapping("/{number}/reopen")
    public IncidentDetail reopen(@PathVariable String number, @Valid @RequestBody TextRequest r) {
        return act(me -> service.reopen(number, r.text(), me));
    }

    @PostMapping("/{number}/close")
    public IncidentDetail close(@PathVariable String number, @Valid @RequestBody(required = false) OptionalTextRequest r) {
        return act(me -> service.close(number, r == null ? null : r.text(), me));
    }

    @PostMapping("/{number}/notes")
    public IncidentDetail addNote(@PathVariable String number, @Valid @RequestBody NoteRequest r) {
        return act(me -> service.addNote(number, r.type(), r.content(), r.internal(), me));
    }

    @PostMapping("/{number}/escalate")
    @PreAuthorize("hasRole('TECHNICIAN')")
    public IncidentDetail escalate(@PathVariable String number, @Valid @RequestBody(required = false) OptionalTextRequest r) {
        return act(me -> service.escalate(number, r == null ? null : r.text(), me));
    }

    @PostMapping("/{number}/reprioritise")
    @PreAuthorize("hasRole('TECHNICIAN')")
    @Operation(summary = "Recalculate priority, or override it (reason required)")
    public IncidentDetail reprioritise(@PathVariable String number, @Valid @RequestBody ReprioritiseRequest r) {
        return act(me -> service.reprioritise(number, r.priority(), r.reason(), me));
    }

    @PostMapping("/{number}/feedback")
    public IncidentDetail feedback(@PathVariable String number, @Valid @RequestBody FeedbackRequest r) {
        return act(me -> service.submitFeedback(number, r.rating(), r.comment(), me));
    }

    @PostMapping("/{number}/duplicate")
    @PreAuthorize("hasRole('TECHNICIAN')")
    @Operation(summary = "Confirm as duplicate of another incident and close it")
    public IncidentDetail markDuplicate(@PathVariable String number, @Valid @RequestBody DuplicateRequest r) {
        return act(me -> service.markDuplicate(number, r.parentNumber(), me));
    }

    @PostMapping("/{number}/duplicate/dismiss")
    @PreAuthorize("hasRole('TECHNICIAN')")
    public IncidentDetail dismissDuplicate(@PathVariable String number) {
        return act(me -> service.dismissDuplicate(number, me));
    }

    // ---- intelligence for one incident ---------------------------------------------------------

    @GetMapping("/{number}/similar")
    @Transactional(readOnly = true)
    @Operation(summary = "Open incidents that may describe the same problem")
    public List<SimilarIncidentView> similar(@PathVariable String number) {
        Incident i = service.get(number, currentUser.require());
        return duplicates.findSimilarOpenIncidents(i.getTitle(), i.getDescription(), i.getService(), i.getId(),
                clock.instant()).stream().map(mapper::toSimilar).toList();
    }

    @GetMapping("/{number}/suggested-resolutions")
    @PreAuthorize("hasRole('TECHNICIAN')")
    @Transactional(readOnly = true)
    @Operation(summary = "Fixes from similar past incidents (knowledge base)")
    public List<ResolutionRecommender.SuggestedResolution> suggestedResolutions(@PathVariable String number) {
        Incident i = service.get(number, currentUser.require());
        return resolutions.suggest(i.getTitle(), i.getDescription(), i.getService(), i.getCategory(), i.getNumber(), 3);
    }

    @GetMapping("/{number}/recommendations")
    @PreAuthorize("hasRole('TEAM_LEAD')")
    @Transactional(readOnly = true)
    @Operation(summary = "Ranked technician recommendations with explanations")
    public List<Map<String, Object>> recommendations(@PathVariable String number,
                                                     @RequestParam(required = false) String strategy) {
        Incident i = service.get(number, currentUser.require());
        return assignment.recommend(i, strategy, clock.instant()).stream().limit(5).map(c -> Map.<String, Object>of(
                "technician", IncidentMapper.person(c.profile().technician()),
                "score", c.score(),
                "activeIncidents", c.profile().activeIncidents(),
                "capacity", c.profile().capacity(),
                "skills", c.profile().technician().getSkills(),
                "reasons", c.reasons())).toList();
    }

    @GetMapping("/{number}/sla-risk")
    @PreAuthorize("hasRole('TECHNICIAN')")
    @Transactional(readOnly = true)
    @Operation(summary = "Predicted SLA risk with contributing factors")
    public SlaRiskAssessor.SlaRisk risk(@PathVariable String number) {
        Incident i = service.get(number, currentUser.require());
        int active = i.getAssignee() == null ? 0 : assignment.activeIncidentCount(i.getAssignee().getId());
        int capacity = i.getAssignee() == null ? 1 : i.getAssignee().getMaxActiveIncidents();
        return slaRisk.assess(i, active, capacity, clock.instant());
    }

    private IncidentDetail act(Function<User, Incident> command) {
        User me = currentUser.require();
        return mapper.toDetail(command.apply(me), me);
    }
}
