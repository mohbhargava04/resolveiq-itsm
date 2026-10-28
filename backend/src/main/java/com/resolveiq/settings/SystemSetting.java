package com.resolveiq.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** A key/value setting row. Keyed by {@link SettingKey} so typos are impossible. */
@Entity
@Table(name = "system_settings")
public class SystemSetting {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "setting_key", length = 60)
    private SettingKey key;

    @Column(name = "setting_value", nullable = false, length = 200)
    private String value;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", length = 120)
    private String updatedBy;

    protected SystemSetting() {
    }

    public SystemSetting(SettingKey key, String value, String updatedBy, Instant at) {
        this.key = key;
        change(value, updatedBy, at);
    }

    public final void change(String value, String updatedBy, Instant at) {
        this.value = value;
        this.updatedBy = updatedBy;
        this.updatedAt = at;
    }

    public SettingKey getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }
}
