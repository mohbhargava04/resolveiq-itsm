package com.resolveiq.catalog;

import com.resolveiq.common.BaseEntity;
import com.resolveiq.common.exception.BusinessRuleViolationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * An IT service offered to the organisation (Payment Gateway, Email, Core Database, ...).
 *
 * <p>Services form a <b>dependency graph</b> (a self many-to-many association): Payment Gateway
 * depends on Core Database and Identity &amp; SSO. The incident correlation engine walks this
 * graph to suggest a common root cause when several dependent services fail together.</p>
 */
@Entity
@Table(name = "it_services")
public class ItService extends BaseEntity {

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Criticality criticality;

    @Column(name = "owner_team", length = 100)
    private String ownerTeam;

    /** Approximate number of people who use this service; informs the impact estimate. */
    @Column(name = "user_base", nullable = false)
    private int userBase;

    @Column(nullable = false)
    private boolean active = true;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "service_dependencies",
            joinColumns = @JoinColumn(name = "service_id"),
            inverseJoinColumns = @JoinColumn(name = "depends_on_id"))
    private Set<ItService> dependencies = new LinkedHashSet<>();

    protected ItService() {
    }

    public ItService(String code, String name, String description, Criticality criticality,
                     String ownerTeam, int userBase) {
        this.code = normaliseCode(code);
        update(name, description, criticality, ownerTeam, userBase);
    }

    public static String normaliseCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Service code is required");
        }
        return code.trim().toUpperCase().replaceAll("[^A-Z0-9]+", "_");
    }

    public void update(String name, String description, Criticality criticality, String ownerTeam, int userBase) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Service name is required");
        }
        this.name = name.trim();
        this.description = description;
        this.criticality = criticality == null ? Criticality.MEDIUM : criticality;
        this.ownerTeam = ownerTeam;
        this.userBase = Math.max(0, userBase);
    }

    /** Adds a dependency, rejecting self-loops and cycles so the graph stays a DAG. */
    public void addDependency(ItService upstream) {
        if (upstream == null) {
            return;
        }
        if (upstream == this || upstream.transitiveDependencies().contains(this)) {
            throw new BusinessRuleViolationException(
                    "Adding " + upstream.getName() + " as a dependency of " + name + " would create a cycle");
        }
        dependencies.add(upstream);
    }

    public void removeDependency(ItService upstream) {
        dependencies.remove(upstream);
    }

    public void clearDependencies() {
        dependencies.clear();
    }

    /** All services this one relies on, directly or indirectly (breadth-first traversal). */
    public Set<ItService> transitiveDependencies() {
        Set<ItService> visited = new LinkedHashSet<>();
        Deque<ItService> queue = new ArrayDeque<>(dependencies);
        while (!queue.isEmpty()) {
            ItService next = queue.poll();
            if (visited.add(next)) {
                queue.addAll(next.dependencies);
            }
        }
        return visited;
    }

    public boolean dependsOn(ItService other) {
        return transitiveDependencies().contains(other);
    }

    public void activate() {
        active = true;
    }

    public void deactivate() {
        active = false;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Criticality getCriticality() {
        return criticality;
    }

    public String getOwnerTeam() {
        return ownerTeam;
    }

    public int getUserBase() {
        return userBase;
    }

    public boolean isActive() {
        return active;
    }

    public Set<ItService> getDependencies() {
        return Collections.unmodifiableSet(dependencies);
    }

    public Set<String> dependencyCodes() {
        Set<String> codes = new HashSet<>();
        dependencies.forEach(d -> codes.add(d.getCode()));
        return codes;
    }
}
