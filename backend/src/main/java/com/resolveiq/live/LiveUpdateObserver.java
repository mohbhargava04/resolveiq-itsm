package com.resolveiq.live;

import com.resolveiq.incident.domain.Incident;
import com.resolveiq.incident.event.IncidentEvent;
import com.resolveiq.incident.event.IncidentObserver;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Observer that pushes a lightweight "incident changed" message to dashboards so they refresh live.
 * A good illustration of the Observer pattern's extensibility: this feature was added without
 * modifying a single line of the incident or service code.
 */
@Component
public class LiveUpdateObserver implements IncidentObserver {

    private final LiveUpdateHub hub;

    public LiveUpdateObserver(LiveUpdateHub hub) {
        this.hub = hub;
    }

    @Override
    public int order() {
        return 90;
    }

    @Override
    public void onIncidentEvent(IncidentEvent event) {
        Incident i = event.incident();
        Map<String, Object> payload = new HashMap<>();
        payload.put("number", i.getNumber());
        payload.put("event", event.type());
        payload.put("status", i.getStatus().name());
        payload.put("priority", i.getPriority() == null ? null : i.getPriority().name());
        payload.put("actor", event.actor().name());
        payload.put("at", event.occurredAt().toString());
        hub.broadcastToStaff("incident", payload);
        hub.sendToUser(i.getReporter().getId(), "incident", payload);
    }
}
