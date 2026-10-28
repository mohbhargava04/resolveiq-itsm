package com.resolveiq.notification;

import com.resolveiq.live.LiveUpdateHub;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Map;

/** Stores the notification in the user's inbox and pushes it live to any open browser tab. */
@Component
public class InAppNotificationChannel extends AbstractNotificationChannel {

    private final LiveUpdateHub liveUpdates;

    public InAppNotificationChannel(NotificationRepository repository, Clock clock, LiveUpdateHub liveUpdates) {
        super(repository, clock);
        this.liveUpdates = liveUpdates;
    }

    @Override
    public ChannelType type() {
        return ChannelType.IN_APP;
    }

    @Override
    protected DeliveryStatus send(NotificationMessage message) {
        return DeliveryStatus.SENT; // persisting the row *is* the delivery
    }

    @Override
    protected void afterDelivery(Notification n) {
        liveUpdates.sendToUser(n.getRecipient().getId(), "notification", Map.of(
                "id", n.getId(),
                "subject", n.getSubject(),
                "message", n.getMessage(),
                "incidentNumber", n.getIncidentNumber() == null ? "" : n.getIncidentNumber(),
                "urgent", n.isUrgent(),
                "createdAt", n.getCreatedAt().toString()));
    }
}
