package com.resolveiq.catalog;

import com.resolveiq.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * An incident category (NETWORK, DATABASE, SECURITY, ...).
 *
 * <p>Categories are <b>data, not an enum</b>: an administrator can add "IOT_DEVICES" at runtime and
 * every other part of the system (skills, classifier, reports, filters) picks it up without a code
 * change. This is the open/closed principle applied to the requirement "new categories without
 * major changes to unrelated parts of the application".</p>
 */
@Entity
@Table(name = "categories")
public class Category extends BaseEntity {

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(length = 300)
    private String description;

    /** Comma-separated seed vocabulary used to train the category classifier. */
    @Column(length = 2000)
    private String keywords;

    /** Security-sensitive categories are never prioritised below P2 (business rule BR-PRI-3). */
    @Column(name = "security_sensitive", nullable = false)
    private boolean securitySensitive;

    @Column(nullable = false)
    private boolean active = true;

    protected Category() {
    }

    public Category(String code, String name, String description, Set<String> keywords, boolean securitySensitive) {
        this.code = ItService.normaliseCode(code);
        update(name, description, keywords, securitySensitive);
    }

    public void update(String name, String description, Set<String> keywords, boolean securitySensitive) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Category name is required");
        }
        this.name = name.trim();
        this.description = description;
        this.keywords = keywords == null ? "" : keywords.stream().map(String::trim).filter(k -> !k.isEmpty())
                .map(String::toLowerCase).distinct().collect(Collectors.joining(","));
        this.securitySensitive = securitySensitive;
    }

    public void activate() {
        active = true;
    }

    public void deactivate() {
        active = false;
    }

    public Set<String> keywordSet() {
        if (keywords == null || keywords.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(keywords.split(",")).map(String::trim).filter(k -> !k.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
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

    public boolean isSecuritySensitive() {
        return securitySensitive;
    }

    public boolean isActive() {
        return active;
    }
}
