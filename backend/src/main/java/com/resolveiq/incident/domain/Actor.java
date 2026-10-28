package com.resolveiq.incident.domain;

import com.resolveiq.user.domain.Role;
import com.resolveiq.user.domain.User;

import java.util.Objects;

/**
 * Who performed an action. An immutable value object (Java record) so that events and audit
 * records keep a snapshot of the actor's name even if the user is later renamed or deactivated.
 * {@link #SYSTEM} represents automated actions (classification, SLA monitor, escalation).
 */
public record Actor(Long userId, String name, Role role) {

    public static final Actor SYSTEM = new Actor(null, "ResolveIQ Automation", null);

    public static Actor of(User user) {
        Objects.requireNonNull(user, "user");
        return new Actor(user.getId(), user.getFullName(), user.getRole());
    }

    public boolean isSystem() {
        return userId == null;
    }

    public boolean is(User user) {
        return user != null && userId != null && userId.equals(user.getId());
    }

    public boolean hasRole(Role... roles) {
        if (role == null) {
            return false;
        }
        for (Role r : roles) {
            if (r == role) {
                return true;
            }
        }
        return false;
    }

    /** Team leads, administrators and managers (or the automation itself). */
    public boolean isManagement() {
        return isSystem() || (role != null && role.isManagement());
    }

    public String roleLabel() {
        return role == null ? "System" : role.displayName();
    }
}
