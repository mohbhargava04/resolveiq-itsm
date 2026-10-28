package com.resolveiq.sla;

import com.resolveiq.catalog.ItService;
import com.resolveiq.common.BaseEntity;
import com.resolveiq.incident.domain.Priority;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Duration;

/**
 * A Service Level Agreement target for one priority, optionally specific to one IT service.
 *
 * <p>Resolution rule: a service-specific policy wins over the default (service = null) policy for
 * the same priority. This lets the Payment Gateway promise a 20-minute P1 fix while everything
 * else uses the organisation-wide 30-minute target.</p>
 */
@Entity
@Table(name = "sla_policies")
public class SlaPolicy extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 5)
    private Priority priority;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id")
    private ItService service;

    @Column(name = "response_minutes", nullable = false)
    private int responseMinutes;

    @Column(name = "resolution_minutes", nullable = false)
    private int resolutionMinutes;

    /** Percentage of the resolution window after which an "SLA approaching" warning is raised. */
    @Column(name = "warning_threshold_percent", nullable = false)
    private int warningThresholdPercent = 75;

    @Column(nullable = false)
    private boolean active = true;

    protected SlaPolicy() {
    }

    public SlaPolicy(String name, Priority priority, ItService service, int responseMinutes,
                     int resolutionMinutes, int warningThresholdPercent) {
        this.priority = priority;
        this.service = service;
        update(name, responseMinutes, resolutionMinutes, warningThresholdPercent);
    }

    public void update(String name, int responseMinutes, int resolutionMinutes, int warningThresholdPercent) {
        if (responseMinutes <= 0 || resolutionMinutes <= 0) {
            throw new IllegalArgumentException("SLA targets must be positive");
        }
        if (responseMinutes > resolutionMinutes) {
            throw new IllegalArgumentException("Response target cannot exceed the resolution target");
        }
        if (warningThresholdPercent < 10 || warningThresholdPercent > 95) {
            throw new IllegalArgumentException("Warning threshold must be between 10% and 95%");
        }
        this.name = name == null || name.isBlank() ? defaultName() : name.trim();
        this.responseMinutes = responseMinutes;
        this.resolutionMinutes = resolutionMinutes;
        this.warningThresholdPercent = warningThresholdPercent;
    }

    private String defaultName() {
        return (service == null ? "Default" : service.getName()) + " " + priority;
    }

    public void activate() {
        active = true;
    }

    public void deactivate() {
        active = false;
    }

    public boolean isDefaultPolicy() {
        return service == null;
    }

    public Duration responseTarget() {
        return Duration.ofMinutes(responseMinutes);
    }

    public Duration resolutionTarget() {
        return Duration.ofMinutes(resolutionMinutes);
    }

    public String getName() {
        return name;
    }

    public Priority getPriority() {
        return priority;
    }

    public ItService getService() {
        return service;
    }

    public int getResponseMinutes() {
        return responseMinutes;
    }

    public int getResolutionMinutes() {
        return resolutionMinutes;
    }

    public int getWarningThresholdPercent() {
        return warningThresholdPercent;
    }

    public boolean isActive() {
        return active;
    }
}
