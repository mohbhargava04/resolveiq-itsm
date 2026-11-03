package com.resolveiq.settings;

/** Runtime-configurable system settings with their defaults. */
public enum SettingKey {
    PRIORITY_STRATEGY("WEIGHTED", "Active priority calculation strategy"),
    ASSIGNMENT_STRATEGY("SMART", "Active technician assignment strategy"),
    AUTO_ASSIGNMENT("true", "Assign a technician automatically when an incident is classified"),
    AUTO_ESCALATION("true", "Escalate unacknowledged high-priority incidents automatically"),
    AUTO_CLOSE_VERIFIED("false", "Close incidents automatically once the reporter verifies the fix"),
    ALLOWED_EMAIL_DOMAINS("resolveiq.test", "Company e-mail domains that may register or sign in with Google (comma separated; empty = any domain)"),
    SELF_REGISTRATION("true", "Let people from an allowed domain create their own employee account"),
    AUTO_PROVISION_SSO("true", "Create an employee account the first time someone from an allowed domain signs in with Google"),
    REQUIRE_MFA_FOR_PRIVILEGED("false", "Administrators and IT managers must set up two-factor authentication");

    /** Keys whose value is a true/false switch. */
    public boolean isSwitch() {
        return "true".equals(defaultValue) || "false".equals(defaultValue);
    }

    private final String defaultValue;
    private final String description;

    SettingKey(String defaultValue, String description) {
        this.defaultValue = defaultValue;
        this.description = description;
    }

    public String defaultValue() {
        return defaultValue;
    }

    public String description() {
        return description;
    }
}
