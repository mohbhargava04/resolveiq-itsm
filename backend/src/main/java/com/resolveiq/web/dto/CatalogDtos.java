package com.resolveiq.web.dto;

import com.resolveiq.catalog.Category;
import com.resolveiq.catalog.Criticality;
import com.resolveiq.catalog.ItService;
import com.resolveiq.incident.domain.Priority;
import com.resolveiq.sla.SlaPolicy;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Set;

/** Service catalogue, category and SLA policy bodies. */
public final class CatalogDtos {

    private CatalogDtos() {
    }

    public record ServiceRequest(@NotBlank @Size(max = 40) String code, @NotBlank @Size(max = 100) String name,
                                 @Size(max = 500) String description, @NotNull Criticality criticality,
                                 @Size(max = 100) String ownerTeam, @Min(0) @Max(10_000_000) Integer userBase,
                                 List<String> dependsOn, Boolean active) {
    }

    public record CategoryRequest(@NotBlank @Size(max = 40) String code, @NotBlank @Size(max = 80) String name,
                                  @Size(max = 300) String description, Set<String> keywords,
                                  boolean securitySensitive, Boolean active) {
    }

    public record SlaPolicyRequest(@Size(max = 100) String name, @NotNull Priority priority, String serviceCode,
                                   @NotNull @Min(1) @Max(100_000) Integer responseMinutes,
                                   @NotNull @Min(1) @Max(100_000) Integer resolutionMinutes,
                                   @Min(10) @Max(95) Integer warningThresholdPercent, Boolean active) {
    }

    public record ServiceView(Long id, String code, String name, String description, String criticality,
                              String ownerTeam, int userBase, boolean active, List<String> dependsOn) {

        public static ServiceView of(ItService s) {
            return new ServiceView(s.getId(), s.getCode(), s.getName(), s.getDescription(), s.getCriticality().name(),
                    s.getOwnerTeam(), s.getUserBase(), s.isActive(),
                    s.getDependencies().stream().map(ItService::getCode).sorted().toList());
        }
    }

    public record CategoryView(Long id, String code, String name, String description, List<String> keywords,
                               boolean securitySensitive, boolean active) {

        public static CategoryView of(Category c) {
            return new CategoryView(c.getId(), c.getCode(), c.getName(), c.getDescription(),
                    List.copyOf(c.keywordSet()), c.isSecuritySensitive(), c.isActive());
        }
    }

    public record SlaPolicyView(Long id, String name, String priority, String serviceCode, String serviceName,
                                int responseMinutes, int resolutionMinutes, int warningThresholdPercent,
                                boolean active) {

        public static SlaPolicyView of(SlaPolicy p) {
            return new SlaPolicyView(p.getId(), p.getName(), p.getPriority().name(),
                    p.getService() == null ? null : p.getService().getCode(),
                    p.getService() == null ? "All services (default)" : p.getService().getName(),
                    p.getResponseMinutes(), p.getResolutionMinutes(), p.getWarningThresholdPercent(), p.isActive());
        }
    }

    /** Graph for visualising the service dependency map. */
    public record DependencyGraph(List<Node> nodes, List<Edge> edges) {
        public record Node(String code, String name, String criticality, int openIncidents) {
        }

        public record Edge(String from, String to) {
        }
    }
}
