package com.resolveiq.notification;

import com.resolveiq.notification.gateway.EmailGateway;
import org.springframework.stereotype.Component;

import java.time.Clock;

/** Sends notifications by e-mail through whichever {@link EmailGateway} adapter is configured. */
@Component
public class EmailNotificationChannel extends AbstractNotificationChannel {

    private final EmailGateway gateway;

    public EmailNotificationChannel(NotificationRepository repository, Clock clock, EmailGateway gateway) {
        super(repository, clock);
        this.gateway = gateway;
    }

    @Override
    public ChannelType type() {
        return ChannelType.EMAIL;
    }

    @Override
    protected DeliveryStatus send(NotificationMessage m) {
        String subject = (m.urgent() ? "[URGENT] " : "") + m.subject();
        String body = "Hello " + m.recipient().getFullName() + ",\n\n" + m.body()
                + (m.incidentNumber() == null ? "" : "\n\nIncident: " + m.incidentNumber())
                + "\n\n— ResolveIQ Service Desk";
        gateway.send(m.recipient().getEmail(), subject, body);
        return gateway.isSimulated() ? DeliveryStatus.SIMULATED : DeliveryStatus.SENT;
    }
}
