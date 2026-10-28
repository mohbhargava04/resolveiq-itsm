package com.resolveiq.notification;

import com.resolveiq.incident.event.IncidentEvent;
import com.resolveiq.incident.event.IncidentObserver;
import org.springframework.stereotype.Component;

/**
 * Observer that converts incident events into notifications: asks the {@link NotificationPolicy}
 * who to tell, then delivers through channels obtained from the {@link NotificationChannelFactory}.
 */
@Component
public class NotificationObserver implements IncidentObserver {

    private final NotificationPolicy policy;
    private final NotificationChannelFactory channelFactory;

    public NotificationObserver(NotificationPolicy policy, NotificationChannelFactory channelFactory) {
        this.policy = policy;
        this.channelFactory = channelFactory;
    }

    @Override
    public int order() {
        return 50;
    }

    @Override
    public void onIncidentEvent(IncidentEvent event) {
        for (NotificationPolicy.Dispatch d : policy.route(event)) {
            NotificationMessage message = new NotificationMessage(d.recipient(), d.subject(), d.body(),
                    event.incident().getNumber(), event.type(), d.urgent());
            channelFactory.channelsFor(d.channels()).forEach(channel -> channel.deliver(message));
        }
    }
}
