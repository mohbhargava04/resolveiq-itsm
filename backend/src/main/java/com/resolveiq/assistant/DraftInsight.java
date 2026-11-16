package com.resolveiq.assistant;

import com.resolveiq.catalog.Category;
import com.resolveiq.catalog.CategoryRepository;
import com.resolveiq.catalog.ItService;
import com.resolveiq.catalog.ItServiceRepository;
import com.resolveiq.incident.IncidentService;
import com.resolveiq.incident.domain.Incident;
import com.resolveiq.intelligence.CategoryClassifier;
import com.resolveiq.intelligence.CategorySuggestion;
import com.resolveiq.intelligence.DuplicateDetector;
import com.resolveiq.intelligence.DuplicateDetector.SimilarIncident;
import com.resolveiq.knowledge.KnowledgeService;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

/**
 * Everything the existing intelligence already knows about a draft, in one place for the
 * conversation states: its service and category, related major incidents, self-help articles and
 * likely duplicates. A <b>facade</b> over the classifier, duplicate detector and knowledge base -
 * the assistant adds no second copy of any rule, it only asks the same components the form uses.
 */
@Component
public class DraftInsight {

    static final int MAX_ARTICLES = 3;

    private final ItServiceRepository services;
    private final CategoryRepository categories;
    private final CategoryClassifier classifier;
    private final DuplicateDetector duplicates;
    private final KnowledgeService knowledge;
    private final IncidentService incidents;
    private final Clock clock;

    public DraftInsight(ItServiceRepository services, CategoryRepository categories, CategoryClassifier classifier,
                        DuplicateDetector duplicates, KnowledgeService knowledge, IncidentService incidents,
                        Clock clock) {
        this.services = services;
        this.categories = categories;
        this.classifier = classifier;
        this.duplicates = duplicates;
        this.knowledge = knowledge;
        this.incidents = incidents;
        this.clock = clock;
    }

    public Optional<ItService> service(IncidentDraft draft) {
        return draft.getServiceCode() == null ? Optional.empty()
                : services.findByCodeIgnoreCase(draft.getServiceCode()).filter(ItService::isActive);
    }

    /** The classifier's opinion on exactly the text the pipeline will classify. */
    public CategorySuggestion categorySuggestion(IncidentDraft draft) {
        return classifier.classify(draft.getTitle() + " " + draft.fullDescription());
    }

    /** The category the incident will get: the suggestion if confident enough, else the pipeline's fallback. */
    public Optional<Category> category(IncidentDraft draft) {
        CategorySuggestion s = categorySuggestion(draft);
        String code = s.isConfident(IncidentService.AUTO_CATEGORY_MIN_CONFIDENCE) ? s.categoryCode()
                : IncidentService.FALLBACK_CATEGORY;
        return categories.findByCodeIgnoreCase(code);
    }

    /** Open major incidents on the draft's service, or on something that service depends on. */
    public List<Incident> relatedMajorIncidents(IncidentDraft draft) {
        Optional<ItService> service = service(draft);
        if (service.isEmpty()) {
            return List.of();
        }
        ItService mine = service.get();
        return incidents.activeMajorIncidents().stream()
                .filter(m -> m.getService().getId().equals(mine.getId()) || mine.dependsOn(m.getService()))
                .toList();
    }

    public List<KnowledgeService.Scored> selfHelp(IncidentDraft draft) {
        return knowledge.suggest(draft.getTitle() + " " + draft.getDescription(), draft.getServiceCode(), MAX_ARTICLES);
    }

    /** Open incidents similar enough to be flagged as probable duplicates. */
    public List<SimilarIncident> likelyDuplicates(IncidentDraft draft) {
        Optional<ItService> service = service(draft);
        if (service.isEmpty()) {
            return List.of();
        }
        return duplicates.findSimilarOpenIncidents(draft.getTitle(), draft.fullDescription(), service.get(), null,
                clock.instant()).stream().filter(duplicates::isSuspectedDuplicate).toList();
    }
}
