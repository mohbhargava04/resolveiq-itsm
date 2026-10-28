package com.resolveiq.web;

import com.resolveiq.catalog.CatalogService;
import com.resolveiq.catalog.ItService;
import com.resolveiq.incident.IncidentRepository;
import com.resolveiq.incident.domain.Incident;
import com.resolveiq.incident.domain.IncidentStatus;
import com.resolveiq.security.CurrentUserService;
import com.resolveiq.user.domain.Role;
import com.resolveiq.user.domain.User;
import com.resolveiq.web.dto.CatalogDtos.CategoryRequest;
import com.resolveiq.web.dto.CatalogDtos.CategoryView;
import com.resolveiq.web.dto.CatalogDtos.DependencyGraph;
import com.resolveiq.web.dto.CatalogDtos.ServiceRequest;
import com.resolveiq.web.dto.CatalogDtos.ServiceView;
import com.resolveiq.web.dto.CatalogDtos.SlaPolicyRequest;
import com.resolveiq.web.dto.CatalogDtos.SlaPolicyView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Service catalogue, categories and SLA policies: readable by everyone, writable by administrators. */
@RestController
@Transactional
@Tag(name = "Catalogue & configuration")
public class CatalogController {

    private final CatalogService catalog;
    private final IncidentRepository incidents;
    private final CurrentUserService currentUser;

    public CatalogController(CatalogService catalog, IncidentRepository incidents, CurrentUserService currentUser) {
        this.catalog = catalog;
        this.incidents = incidents;
        this.currentUser = currentUser;
    }

    // ---- read (all authenticated users) -------------------------------------------------------

    @GetMapping("/api/services")
    public List<ServiceView> services() {
        boolean staff = currentUser.require().getRole() != Role.EMPLOYEE;
        return catalog.listServices().stream().filter(s -> staff || s.isActive()).map(ServiceView::of).toList();
    }

    @GetMapping("/api/services/graph")
    @Operation(summary = "Service dependency graph with live open-incident counts (for the service health map)")
    public DependencyGraph graph() {
        Map<String, Integer> open = new HashMap<>();
        for (Incident i : incidents.findByStatusIn(IncidentStatus.ACTIVE)) {
            open.merge(i.getService().getCode(), 1, Integer::sum);
        }
        List<DependencyGraph.Node> nodes = new ArrayList<>();
        List<DependencyGraph.Edge> edges = new ArrayList<>();
        for (ItService s : catalog.listServices()) {
            nodes.add(new DependencyGraph.Node(s.getCode(), s.getName(), s.getCriticality().name(),
                    open.getOrDefault(s.getCode(), 0)));
            s.getDependencies().forEach(d -> edges.add(new DependencyGraph.Edge(s.getCode(), d.getCode())));
        }
        return new DependencyGraph(nodes, edges);
    }

    @GetMapping("/api/categories")
    public List<CategoryView> categories(@RequestParam(defaultValue = "false") boolean includeInactive) {
        boolean admin = currentUser.require().getRole().isAdministrative();
        return catalog.listCategories(includeInactive && admin).stream().map(CategoryView::of).toList();
    }

    @GetMapping("/api/sla-policies")
    public List<SlaPolicyView> slaPolicies() {
        return catalog.listSlaPolicies().stream().map(SlaPolicyView::of).toList();
    }

    // ---- write (administrators; enforced by the /api/admin/** URL rule) ----------------------

    @PostMapping("/api/admin/services")
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceView createService(@Valid @RequestBody ServiceRequest r) {
        User me = currentUser.require();
        return ServiceView.of(catalog.createService(r.code(), r.name(), r.description(), r.criticality(), r.ownerTeam(),
                r.userBase() == null ? 0 : r.userBase(), r.dependsOn(), me));
    }

    @PutMapping("/api/admin/services/{code}")
    public ServiceView updateService(@PathVariable String code, @Valid @RequestBody ServiceRequest r) {
        User me = currentUser.require();
        return ServiceView.of(catalog.updateService(code, r.name(), r.description(), r.criticality(), r.ownerTeam(),
                r.userBase() == null ? 0 : r.userBase(), r.dependsOn(), r.active(), me));
    }

    @PostMapping("/api/admin/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryView createCategory(@Valid @RequestBody CategoryRequest r) {
        User me = currentUser.require();
        return CategoryView.of(catalog.createCategory(r.code(), r.name(), r.description(), r.keywords(),
                r.securitySensitive(), me));
    }

    @PutMapping("/api/admin/categories/{code}")
    public CategoryView updateCategory(@PathVariable String code, @Valid @RequestBody CategoryRequest r) {
        User me = currentUser.require();
        return CategoryView.of(catalog.updateCategory(code, r.name(), r.description(), r.keywords(),
                r.securitySensitive(), r.active(), me));
    }

    @PostMapping("/api/admin/sla-policies")
    @ResponseStatus(HttpStatus.CREATED)
    public SlaPolicyView createSla(@Valid @RequestBody SlaPolicyRequest r) {
        User me = currentUser.require();
        return SlaPolicyView.of(catalog.createSlaPolicy(r.name(), r.priority(), r.serviceCode(), r.responseMinutes(),
                r.resolutionMinutes(), r.warningThresholdPercent(), me));
    }

    @PutMapping("/api/admin/sla-policies/{id}")
    public SlaPolicyView updateSla(@PathVariable Long id, @Valid @RequestBody SlaPolicyRequest r) {
        User me = currentUser.require();
        return SlaPolicyView.of(catalog.updateSlaPolicy(id, r.name(), r.responseMinutes(), r.resolutionMinutes(),
                r.warningThresholdPercent(), r.active(), me));
    }
}
