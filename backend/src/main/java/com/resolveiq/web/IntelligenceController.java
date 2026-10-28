package com.resolveiq.web;

import com.resolveiq.catalog.CatalogService;
import com.resolveiq.catalog.Category;
import com.resolveiq.catalog.CategoryRepository;
import com.resolveiq.catalog.ItService;
import com.resolveiq.incident.domain.Impact;
import com.resolveiq.incident.domain.Urgency;
import com.resolveiq.intelligence.CategoryClassifier;
import com.resolveiq.intelligence.CategorySuggestion;
import com.resolveiq.intelligence.DuplicateDetector;
import com.resolveiq.intelligence.IncidentCorrelator;
import com.resolveiq.intelligence.ResolutionRecommender;
import com.resolveiq.priority.PriorityAssessment;
import com.resolveiq.priority.PriorityEngine;
import com.resolveiq.security.CurrentUserService;
import com.resolveiq.sla.SlaPolicy;
import com.resolveiq.sla.SlaPolicyResolver;
import com.resolveiq.web.dto.IncidentDtos.SimilarIncidentView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.List;
import java.util.Map;

/**
 * Live "assistive" endpoints used while an incident form is being filled in: the UI calls them as the
 * user types to suggest a category, preview the priority and warn about possible duplicates
 * <em>before</em> the incident is submitted.
 */
@RestController
@RequestMapping("/api/intelligence")
@Transactional(readOnly = true)
@Tag(name = "Intelligence", description = "AI classification, duplicate detection, priority preview, correlation")
public class IntelligenceController {

    public record TextBody(@NotBlank @Size(max = 4200) String text) {
    }

    public record DraftBody(@Size(max = 160) String title, @Size(max = 4000) String description,
                            @NotBlank String serviceCode) {
    }

    public record PriorityPreviewBody(@NotNull Impact impact, @NotNull Urgency urgency,
                                      @Min(1) @Max(1_000_000) Integer affectedUsers, @NotBlank String serviceCode,
                                      String categoryCode) {
    }

    public record PriorityPreview(PriorityAssessment assessment, String slaPolicy, int responseMinutes,
                                  int resolutionMinutes) {
    }

    private final CategoryClassifier classifier;
    private final DuplicateDetector duplicates;
    private final IncidentCorrelator correlator;
    private final ResolutionRecommender resolutions;
    private final PriorityEngine priorityEngine;
    private final SlaPolicyResolver slaResolver;
    private final CatalogService catalog;
    private final CategoryRepository categories;
    private final IncidentMapper mapper;
    private final CurrentUserService currentUser;
    private final Clock clock;

    public IntelligenceController(CategoryClassifier classifier, DuplicateDetector duplicates,
                                  IncidentCorrelator correlator, ResolutionRecommender resolutions,
                                  PriorityEngine priorityEngine, SlaPolicyResolver slaResolver, CatalogService catalog,
                                  CategoryRepository categories, IncidentMapper mapper, CurrentUserService currentUser,
                                  Clock clock) {
        this.classifier = classifier;
        this.duplicates = duplicates;
        this.correlator = correlator;
        this.resolutions = resolutions;
        this.priorityEngine = priorityEngine;
        this.slaResolver = slaResolver;
        this.catalog = catalog;
        this.categories = categories;
        this.mapper = mapper;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @PostMapping("/classify")
    @Operation(summary = "Suggest a category for free text (Naive Bayes, with explanation)")
    public CategorySuggestion classify(@Valid @RequestBody TextBody body) {
        currentUser.require();
        return classifier.classify(body.text());
    }

    @PostMapping("/duplicates")
    @Operation(summary = "Check a draft incident against open incidents before submitting")
    public List<SimilarIncidentView> duplicates(@Valid @RequestBody DraftBody body) {
        currentUser.require();
        ItService service = catalog.requireService(body.serviceCode());
        return duplicates.findSimilarOpenIncidents(nz(body.title()), nz(body.description()), service, null,
                        clock.instant()).stream()
                .filter(duplicates::isSuspectedDuplicate)
                .map(mapper::toSimilar).toList();
    }

    @PostMapping("/priority-preview")
    @Operation(summary = "Preview the priority and SLA the incident would get")
    public PriorityPreview priorityPreview(@Valid @RequestBody PriorityPreviewBody body) {
        currentUser.require();
        ItService service = catalog.requireService(body.serviceCode());
        Category category = body.categoryCode() == null ? null
                : categories.findByCodeIgnoreCase(body.categoryCode()).orElse(null);
        PriorityAssessment a = priorityEngine.assess(body.impact(), body.urgency(),
                body.affectedUsers() == null ? 1 : body.affectedUsers(), service, category);
        SlaPolicy policy = slaResolver.resolve(service, a.priority());
        return new PriorityPreview(a, policy.getName(), policy.getResponseMinutes(), policy.getResolutionMinutes());
    }

    @PostMapping("/suggest-resolutions")
    @PreAuthorize("hasRole('TECHNICIAN')")
    @Operation(summary = "Search the knowledge base of past resolutions with free text")
    public List<ResolutionRecommender.SuggestedResolution> suggest(@Valid @RequestBody TextBody body) {
        return resolutions.suggest(body.text(), "", null, null, null, 5);
    }

    @GetMapping("/correlations")
    @PreAuthorize("hasRole('TECHNICIAN')")
    @Operation(summary = "Clusters of open incidents that probably share a root cause (service dependency graph)")
    public List<IncidentCorrelator.CorrelationCluster> correlations() {
        return correlator.findClusters(clock.instant());
    }

    @PostMapping("/model/retrain")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Retrain the category classifier now (it also retrains automatically)")
    public Map<String, Object> retrain() {
        classifier.retrain();
        return Map.of("model", classifier.modelName(), "status", "retrained");
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
