package com.resolveiq.user.domain;

/**
 * Roles map one-to-one onto the concrete {@link User} subclasses. The ordering expresses the
 * escalation hierarchy used by role-based authorisation (see SecurityConfig's role hierarchy).
 */
public enum Role {
    EMPLOYEE("Employee"),
    TECHNICIAN("Technician"),
    TEAM_LEAD("Team Lead"),
    ADMIN("System Administrator"),
    MANAGER("IT Manager");

    private final String displayName;

    Role(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /** Spring Security authority string, e.g. {@code ROLE_TECHNICIAN}. */
    public String authority() {
        return "ROLE_" + name();
    }

    public boolean isSupportStaff() {
        return this == TECHNICIAN || this == TEAM_LEAD;
    }

    public boolean isManagement() {
        return this == TEAM_LEAD || this == ADMIN || this == MANAGER;
    }

    public boolean isAdministrative() {
        return this == ADMIN || this == MANAGER;
    }
}
