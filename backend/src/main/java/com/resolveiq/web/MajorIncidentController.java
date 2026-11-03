package com.resolveiq.web;

import com.resolveiq.incident.BulkActionService;
import com.resolveiq.incident.IncidentService;
import com.resolveiq.security.CurrentUserService;
import com.resolveiq.user.domain.User;
import com.resolveiq.web.dto.IncidentDtos.AffectedRequest;
import com.resolveiq.web.dto.IncidentDtos.BulkRequest;
import com.resolveiq.web.dto.IncidentDtos.DeclareMajorRequest;
import com.resolveiq.web.dto.IncidentDtos.IncidentDetail;
import com.resolveiq.web.dto.IncidentDtos.LinkMajorRequest;
import com.resolveiq.web.dto.IncidentDtos.MajorIncidentBanner;
import com.resolveiq.web.dto.IncidentDtos.MajorUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Major-incident coordination, the status banner every user sees, and bulk actions. */
@RestController
@Tag(name = "Major incidents & bulk actions")
public class MajorIncidentController {

    private final IncidentService incidents;
    private final BulkActionService bulk;
    private final IncidentMapper mapper;
    private final CurrentUserService currentUser;

    public MajorIncidentController(IncidentService incidents, BulkActionService bulk, IncidentMapper mapper,
                                   CurrentUserService currentUser) {
        this.incidents = incidents;
        this.bulk = bulk;
        this.mapper = mapper;
        this.currentUser = currentUser;
    }

    @GetMapping("/api/major-incidents")
    @Transactional(readOnly = true)
    @Operation(summary = "Open major incidents (status banner shown to everyone)")
    public List<MajorIncidentBanner> active() {
        User me = currentUser.require();
        return incidents.activeMajorIncidents().stream().map(i -> mapper.toBanner(i, me)).toList();
    }

    @PostMapping("/api/incidents/{number}/major")
    @PreAuthorize("hasRole('TEAM_LEAD')")
    @Transactional
    @Operation(summary = "Declare a major incident, optionally linking related incidents at once")
    public IncidentDetail declare(@PathVariable String number, @Valid @RequestBody DeclareMajorRequest r) {
        User me = currentUser.require();
        return mapper.toDetail(incidents.declareMajor(number, r.summary(), r.link(), me), me);
    }

    @PostMapping("/api/incidents/{number}/major/updates")
    @PreAuthorize("hasRole('TECHNICIAN')")
    @Transactional
    @Operation(summary = "Post a status update to everyone affected")
    public IncidentDetail postUpdate(@PathVariable String number, @Valid @RequestBody MajorUpdateRequest r) {
        User me = currentUser.require();
        return mapper.toDetail(incidents.postMajorUpdate(number, r.stage(), r.message(), me), me);
    }

    @PostMapping("/api/incidents/{number}/link-major")
    @PreAuthorize("hasRole('TECHNICIAN')")
    @Transactional
    public IncidentDetail link(@PathVariable String number, @Valid @RequestBody LinkMajorRequest r) {
        User me = currentUser.require();
        return mapper.toDetail(incidents.linkToMajor(number, r.majorNumber(), me), me);
    }

    @PostMapping("/api/major-incidents/{number}/affected")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    @Operation(summary = "\"I'm affected too\": open a linked incident for yourself")
    public IncidentDetail affected(@PathVariable String number, @Valid @RequestBody AffectedRequest r) {
        User me = currentUser.require();
        return mapper.toDetail(incidents.reportAffected(number, r.details(), me), me);
    }

    /** Not transactional on purpose: each incident is its own transaction inside the service. */
    @PostMapping("/api/incidents/bulk")
    @PreAuthorize("hasRole('TEAM_LEAD')")
    @Operation(summary = "Apply one action (assign, link to major, mark duplicate, resolve, close) to many incidents")
    public List<BulkActionService.ItemResult> bulk(@Valid @RequestBody BulkRequest r) {
        return bulk.apply(r.numbers(), r.action(), r.params(), currentUser.require());
    }
}
