package com.resolveiq.settings;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.EnumMap;
import java.util.Map;

/** Typed access to runtime settings, falling back to each key's default. */
@Service
public class SettingsService {

    private final SystemSettingRepository repository;
    private final Clock clock;

    public SettingsService(SystemSettingRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public String get(SettingKey key) {
        return repository.findById(key).map(SystemSetting::getValue).orElse(key.defaultValue());
    }

    @Transactional(readOnly = true)
    public boolean isEnabled(SettingKey key) {
        return Boolean.parseBoolean(get(key));
    }

    @Transactional(readOnly = true)
    public Map<SettingKey, String> all() {
        Map<SettingKey, String> map = new EnumMap<>(SettingKey.class);
        for (SettingKey key : SettingKey.values()) {
            map.put(key, key.defaultValue());
        }
        repository.findAll().forEach(s -> map.put(s.getKey(), s.getValue()));
        return map;
    }

    /** @return the previous value */
    @Transactional
    public String set(SettingKey key, String value, String changedBy) {
        String previous = get(key);
        repository.findById(key).ifPresentOrElse(
                s -> s.change(value, changedBy, clock.instant()),
                () -> repository.save(new SystemSetting(key, value, changedBy, clock.instant())));
        return previous;
    }
}
