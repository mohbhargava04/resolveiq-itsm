package com.resolveiq.web;

import com.resolveiq.assignment.AssignmentService;
import com.resolveiq.assignment.AssignmentStrategy;
import com.resolveiq.audit.AuditAction;
import com.resolveiq.audit.AuditService;
import com.resolveiq.common.exception.BusinessRuleViolationException;
import com.resolveiq.notification.ChannelType;
import com.resolveiq.notification.Notification;
import com.resolveiq.notification.NotificationChannelFactory;
import com.resolveiq.notification.NotificationRepository;
import com.resolveiq.priority.PriorityEngine;
import com.resolveiq.priority.PriorityStrategy;
import com.resolveiq.security.CurrentUserService;
import com.resolveiq.settings.SettingKey;
import com.resolveiq.settings.SettingsService;
import com.resolveiq.sla.SlaMonitorService;
import com.resolveiq.user.domain.User;
import com.resolveiq.web.dto.CommonDtos.PageResponse;
import com.resolveiq.web.dto.IncidentDtos.AuditEntry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * System configuration: strategies and settings, the audit log, the notification delivery log, and
 * a manual trigger for the SLA monitor (useful in demos).
 */
@RestController
@Transactional
@Tag(name = "Administration")
public class AdminController {

    private final SettingsService settings;
    private final PriorityEngine priorityEngine;
    private final AssignmentService assignment;
    private final AuditService audit;
    private final NotificationRepository notifications;
    private final NotificationChannelFactory channels;
    private final SlaMonitorService slaMonitor;
    private final CurrentUserService currentUser;
    private final com.resolveiq.security.session.SessionService sessions;
    private final com.resolveiq.user.UserRepository users;
    private final com.resolveiq.security.oauth.IdentityProviders identityProviders;
    private final com.resolveiq.security.EmailDomainPolicy domains;
    private final java.time.Clock clock;

    public AdminController(SettingsService settings, PriorityEngine priorityEngine, AssignmentService assignment,
                           AuditService audit, NotificationRepository notifications, NotificationChannelFactory channels,
                           SlaMonitorService slaMonitor, CurrentUserService currentUser,
                           com.resolveiq.security.session.SessionService sessions, com.resolveiq.user.UserRepository users,
                           com.resolveiq.security.oauth.IdentityProviders identityProviders,
                           com.resolveiq.security.EmailDomainPolicy domains, java.time.Clock clock) {
        this.sessions = sessions;
        this.users = users;
        this.identityProviders = identityProviders;
        this.domains = domains;
        this.clock = clock;
        this.settings = settings;
        this.priorityEngine = priorityEngine;
        this.assignment = assignment;
        this.audit = audit;
        this.notifications = notifications;
        this.channels = channels;
        this.slaMonitor = slaMonitor;
        this.currentUser = currentUser;
    }

    public record StrategyView(String key, String name, String description, boolean active) {
    }

    @GetMapping("/api/admin/settings")
    public Map<String, Object> getSettings() {
        Map<String, Object> result = new LinkedHashMap<>();
        settings.all().forEach((k, v) -> result.put(k.name(), v));
        String activePriority = priorityEngine.activeStrategy().key();
        String activeAssignment = assignment.activeStrategy().key();
        result.put("priorityStrategies", priorityEngine.available().stream()
                .map(s -> view(s, activePriority)).toList());
        result.put("assignmentStrategies", assignment.available().stream()
                .map(s -> view(s, activeAssignment)).toList());
        result.put("notificationChannels", channels.enabledChannels());
        return result;
    }

    @PutMapping("/api/admin/settings")
    @Operation(summary = "Change runtime settings, e.g. switch the active priority or assignment strategy")
    public Map<String, Object> updateSettings(@RequestBody Map<SettingKey, String> changes) {
        User me = currentUser.require();
        changes.forEach((key, value) -> {
            String v = validate(key, value);
            String previous = settings.set(key, v, me.getFullName());
            audit.recordAdminAction(me, AuditAction.SETTING_CHANGED, previous, v, key + " changed");
        });
        return getSettings();
    }

    @GetMapping("/api/audit")
    @Operation(summary = "Search the audit trail")
    public PageResponse<AuditEntry> auditLog(@RequestParam(required = false) String incident,
                                             @RequestParam(required = false) String actor,
                                             @RequestParam(required = false) AuditAction action,
                                             @RequestParam(required = false) Boolean security,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "50") int size) {
        var pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 200),
                Sort.by(Sort.Direction.DESC, "occurredAt", "id"));
        return PageResponse.of(audit.search(incident, actor, action, Boolean.TRUE.equals(security) ? AuditAction.SECURITY : null,
                from, to, pageable), IncidentMapper::toAudit);
    }

    @GetMapping("/api/admin/notifications/outbox")
    @Operation(summary = "Delivery log of e-mail / SMS / in-app notifications")
    public List<Map<String, Object>> outbox(@RequestParam(required = false) ChannelType channel,
                                            @RequestParam(defaultValue = "100") int limit) {
        var page = PageRequest.of(0, Math.min(Math.max(1, limit), 500));
        List<Notification> rows = channel == null ? notifications.findRecentWithRecipient(page)
                : notifications.findRecentByChannel(channel, page);
        return rows.stream().map(n -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", n.getId());
            m.put("channel", n.getChannel());
            m.put("recipient", n.getRecipient().getFullName());
            m.put("address", n.getChannel() == ChannelType.SMS ? n.getRecipient().getPhone() : n.getRecipient().getEmail());
            m.put("subject", n.getSubject());
            m.put("message", n.getMessage());
            m.put("status", n.getDeliveryStatus());
            m.put("incidentNumber", n.getIncidentNumber());
            m.put("urgent", n.isUrgent());
            m.put("createdAt", n.getCreatedAt());
            m.put("error", n.getErrorMessage());
            return m;
        }).toList();
    }

    @GetMapping("/api/admin/security")
    @Operation(summary = "Security overview: sessions, lockouts, 2FA adoption and recent security events")
    public Map<String, Object> securityOverview() {
        Instant now = clock.instant();
        Instant day = now.minus(java.time.Duration.ofDays(1));
        Instant month = now.minus(java.time.Duration.ofDays(30));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("activeSessions", sessions.activeSessionCount());
        m.put("lockedAccounts", users.countLocked(now));
        m.put("privilegedAccounts", users.countPrivileged());
        m.put("privilegedWithMfa", users.countPrivilegedWithMfa());
        m.put("failedLogins24h", audit.count(AuditAction.LOGIN_FAILED, day) + audit.count(AuditAction.MFA_FAILED, day));
        m.put("lockouts24h", audit.count(AuditAction.ACCOUNT_LOCKED, day));
        m.put("ssoRejected30d", audit.count(AuditAction.SSO_LOGIN_REJECTED, month));
        m.put("tokenReuse30d", audit.count(AuditAction.SESSION_REUSE_DETECTED, month));
        m.put("passwordResets30d", audit.count(AuditAction.PASSWORD_RESET, month));
        m.put("googleSignInEnabled", !identityProviders.enabledProviders().isEmpty());
        m.put("allowedDomains", domains.allowedDomains());
        m.put("mfaRequiredForPrivileged", settings.isEnabled(SettingKey.REQUIRE_MFA_FOR_PRIVILEGED));
        return m;
    }

    @PostMapping("/api/admin/sla-monitor/run")
    @Operation(summary = "Run the SLA monitor now (it also runs automatically every 30 s)")
    public SlaMonitorService.ScanResult runSlaMonitor() {
        return slaMonitor.scan();
    }

    private String validate(SettingKey key, String value) {
        if (key == SettingKey.ALLOWED_EMAIL_DOMAINS) {
            return com.resolveiq.security.EmailDomainPolicy.normaliseList(value == null ? "" : value);
        }
        if (value == null || value.isBlank()) {
            throw new BusinessRuleViolationException(key + " needs a value");
        }
        return switch (key) {
            case PRIORITY_STRATEGY -> priorityEngine.require(value.trim().toUpperCase()).key();
            case ASSIGNMENT_STRATEGY -> assignment.require(value.trim()).key();
            case AUTO_ASSIGNMENT, AUTO_ESCALATION, AUTO_CLOSE_VERIFIED, SELF_REGISTRATION, AUTO_PROVISION_SSO,
                 REQUIRE_MFA_FOR_PRIVILEGED -> {
                String v = value.trim().toLowerCase();
                if (!v.equals("true") && !v.equals("false")) {
                    throw new BusinessRuleViolationException(key + " must be true or false");
                }
                yield v;
            }
            case ALLOWED_EMAIL_DOMAINS -> throw new IllegalStateException("handled above");
        };
    }

    private static StrategyView view(PriorityStrategy s, String active) {
        return new StrategyView(s.key(), s.displayName(), s.description(), s.key().equals(active));
    }

    private static StrategyView view(AssignmentStrategy s, String active) {
        return new StrategyView(s.key(), s.displayName(), s.description(), s.key().equals(active));
    }
}
