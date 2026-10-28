package com.resolveiq.sla;

import com.resolveiq.catalog.ItService;
import com.resolveiq.common.exception.MissingSlaConfigurationException;
import com.resolveiq.incident.domain.Priority;
import org.springframework.stereotype.Component;

/**
 * Finds the SLA policy that applies to an incident: the service-specific policy for its priority if
 * one exists, otherwise the organisation-wide default for that priority (business rule BR-SLA-1).
 */
@Component
public class SlaPolicyResolver {

    private final SlaPolicyRepository repository;

    public SlaPolicyResolver(SlaPolicyRepository repository) {
        this.repository = repository;
    }

    public SlaPolicy resolve(ItService service, Priority priority) {
        return repository.findActiveForService(service.getId(), priority)
                .or(() -> repository.findActiveDefault(priority))
                .orElseThrow(() -> new MissingSlaConfigurationException(service.getName(), priority.name()));
    }
}
