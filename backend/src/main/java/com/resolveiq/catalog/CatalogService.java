package com.resolveiq.catalog;

import com.resolveiq.audit.AuditAction;
import com.resolveiq.audit.AuditService;
import com.resolveiq.common.exception.BusinessRuleViolationException;
import com.resolveiq.common.exception.DuplicateResourceException;
import com.resolveiq.common.exception.ResourceNotFoundException;
import com.resolveiq.common.exception.ServiceNotFoundException;
import com.resolveiq.incident.domain.Priority;
import com.resolveiq.intelligence.CategoryClassifier;
import com.resolveiq.sla.SlaPolicy;
import com.resolveiq.sla.SlaPolicyRepository;
import com.resolveiq.user.domain.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/** Administration of IT services, their dependencies, incident categories and SLA policies. */
@Service
@Transactional
public class CatalogService {

    private final ItServiceRepository services;
    private final CategoryRepository categories;
    private final SlaPolicyRepository slaPolicies;
    private final CategoryClassifier classifier;
    private final AuditService audit;

    public CatalogService(ItServiceRepository services, CategoryRepository categories, SlaPolicyRepository slaPolicies,
                          CategoryClassifier classifier, AuditService audit) {
        this.services = services;
        this.categories = categories;
        this.slaPolicies = slaPolicies;
        this.classifier = classifier;
        this.audit = audit;
    }

    // ---- services ----------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ItService> listServices() {
        return services.findAllWithDependencies();
    }

    public ItService createService(String code, String name, String description, Criticality criticality,
                                   String ownerTeam, int userBase, List<String> dependsOn, User actor) {
        if (services.existsByCodeIgnoreCase(ItService.normaliseCode(code))) {
            throw new DuplicateResourceException("A service with code " + code + " already exists");
        }
        ItService service = services.save(new ItService(code, name, description, criticality, ownerTeam, userBase));
        setDependencies(service, dependsOn);
        audit.recordAdminAction(actor, AuditAction.SERVICE_CHANGED, null, service.getCode(),
                "Created service " + service.getName() + " (" + service.getCriticality() + ")");
        return service;
    }

    public ItService updateService(String code, String name, String description, Criticality criticality,
                                   String ownerTeam, int userBase, List<String> dependsOn, Boolean active, User actor) {
        ItService service = requireService(code);
        String before = service.getCriticality().name();
        service.update(name, description, criticality, ownerTeam, userBase);
        if (dependsOn != null) {
            setDependencies(service, dependsOn);
        }
        if (active != null) {
            if (active) {
                service.activate();
            } else {
                service.deactivate();
            }
        }
        audit.recordAdminAction(actor, AuditAction.SERVICE_CHANGED, before, service.getCriticality().name(),
                "Updated service " + service.getName());
        return service;
    }

    private void setDependencies(ItService service, List<String> codes) {
        service.clearDependencies();
        if (codes == null) {
            return;
        }
        for (String c : codes) {
            service.addDependency(requireService(c));
        }
    }

    public ItService requireService(String code) {
        return services.findByCodeIgnoreCase(code == null ? "" : code.trim())
                .orElseThrow(() -> new ServiceNotFoundException(code));
    }

    // ---- categories --------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<Category> listCategories(boolean includeInactive) {
        return includeInactive ? categories.findAllByOrderByName() : categories.findByActiveTrueOrderByName();
    }

    public Category createCategory(String code, String name, String description, Set<String> keywords,
                                   boolean securitySensitive, User actor) {
        if (categories.existsByCodeIgnoreCase(ItService.normaliseCode(code))) {
            throw new DuplicateResourceException("A category with code " + code + " already exists");
        }
        Category category = categories.save(new Category(code, name, description, keywords, securitySensitive));
        classifier.markStale();
        audit.recordAdminAction(actor, AuditAction.CATEGORY_CHANGED, null, category.getCode(),
                "Created category " + category.getName() + " - the classifier will learn it from its keywords");
        return category;
    }

    public Category updateCategory(String code, String name, String description, Set<String> keywords,
                                   boolean securitySensitive, Boolean active, User actor) {
        Category category = categories.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new ResourceNotFoundException("Category", code));
        category.update(name, description, keywords, securitySensitive);
        if (active != null) {
            if (active) {
                category.activate();
            } else {
                category.deactivate();
            }
        }
        classifier.markStale();
        audit.recordAdminAction(actor, AuditAction.CATEGORY_CHANGED, null, category.getCode(),
                "Updated category " + category.getName());
        return category;
    }

    // ---- SLA policies ------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<SlaPolicy> listSlaPolicies() {
        return slaPolicies.findAllOrdered();
    }

    public SlaPolicy createSlaPolicy(String name, Priority priority, String serviceCode, int response, int resolution,
                                     Integer warningPercent, User actor) {
        ItService service = serviceCode == null || serviceCode.isBlank() ? null : requireService(serviceCode);
        if (slaPolicies.existsFor(service == null ? null : service.getId(), priority)) {
            throw new DuplicateResourceException("An SLA policy for " + priority + " on "
                    + (service == null ? "all services" : service.getName()) + " already exists; edit it instead");
        }
        SlaPolicy policy = slaPolicies.save(new SlaPolicy(name, priority, service, response, resolution,
                warningPercent == null ? 75 : warningPercent));
        audit.recordAdminAction(actor, AuditAction.SLA_POLICY_CHANGED, null,
                response + "/" + resolution + " min", "Created SLA policy " + policy.getName());
        return policy;
    }

    public SlaPolicy updateSlaPolicy(Long id, String name, int response, int resolution, Integer warningPercent,
                                     Boolean active, User actor) {
        SlaPolicy policy = slaPolicies.findById(id).orElseThrow(() -> new ResourceNotFoundException("SLA policy", id));
        String before = policy.getResponseMinutes() + "/" + policy.getResolutionMinutes() + " min";
        if (Boolean.FALSE.equals(active) && policy.isDefaultPolicy()) {
            throw new BusinessRuleViolationException("Default SLA policies cannot be deactivated - every priority needs one");
        }
        policy.update(name, response, resolution, warningPercent == null ? policy.getWarningThresholdPercent() : warningPercent);
        if (active != null) {
            if (active) {
                policy.activate();
            } else {
                policy.deactivate();
            }
        }
        audit.recordAdminAction(actor, AuditAction.SLA_POLICY_CHANGED, before,
                response + "/" + resolution + " min", "Updated SLA policy " + policy.getName()
                        + " (applies to new incidents and re-prioritised ones)");
        return policy;
    }
}
