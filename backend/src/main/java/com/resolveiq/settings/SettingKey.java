package com.resolveiq.settings;

/** Runtime-configurable system settings with their defaults. */
public enum SettingKey {
    PRIORITY_STRATEGY("WEIGHTED", "Active priority calculation strategy"),
    ASSIGNMENT_STRATEGY("SMART", "Active technician assignment strategy"),
    AUTO_ASSIGNMENT("true", "Assign a technician automatically when an incident is classified"),
    AUTO_ESCALATION("true", "Escalate unacknowledged high-priority incidents automatically"),
    AUTO_CLOSE_VERIFIED("false", "Close incidents automatically once the reporter verifies the fix");

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
