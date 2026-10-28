package com.resolveiq.priority;

import com.resolveiq.catalog.Category;
import com.resolveiq.catalog.ItService;
import com.resolveiq.common.exception.BusinessRuleViolationException;
import com.resolveiq.incident.domain.Impact;
import com.resolveiq.incident.domain.Urgency;
import com.resolveiq.settings.SettingKey;
import com.resolveiq.settings.SettingsService;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Context of the priority Strategy pattern. Spring injects every {@link PriorityStrategy} bean, so a
 * new strategy class is discovered automatically; the active one is read from settings on every
 * call, so an administrator can switch algorithms without restarting the server.
 */
@Service
public class PriorityEngine {

    private final Map<String, PriorityStrategy> strategies = new LinkedHashMap<>();
    private final SettingsService settings;

    public PriorityEngine(List<PriorityStrategy> strategies, SettingsService settings) {
        strategies.forEach(s -> this.strategies.put(s.key(), s));
        this.settings = settings;
    }

    public PriorityAssessment assess(Impact impact, Urgency urgency, int affectedUsers, ItService service,
                                     Category category) {
        PriorityContext context = new PriorityContext(impact, urgency, service.getCriticality(), affectedUsers,
                category != null && category.isSecuritySensitive(), service.getName(),
                category == null ? null : category.getCode());
        return activeStrategy().assess(context);
    }

    public PriorityStrategy activeStrategy() {
        String key = settings.get(SettingKey.PRIORITY_STRATEGY);
        return strategies.getOrDefault(key, strategies.get(WeightedScoringPriorityStrategy.KEY));
    }

    public PriorityStrategy require(String key) {
        PriorityStrategy s = strategies.get(key);
        if (s == null) {
            throw new BusinessRuleViolationException("Unknown priority strategy '" + key + "'. Available: "
                    + strategies.keySet());
        }
        return s;
    }

    public Collection<PriorityStrategy> available() {
        return strategies.values();
    }
}
