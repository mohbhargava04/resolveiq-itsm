package com.resolveiq.notification;

import com.resolveiq.notification.gateway.SmsGateway;
import org.springframework.stereotype.Component;

import java.time.Clock;

/** Sends short text alerts through the configured {@link SmsGateway} adapter. */
@Component
public class SmsNotificationChannel extends AbstractNotificationChannel {

    private final SmsGateway gateway;

    public SmsNotificationChannel(NotificationRepository repository, Clock clock, SmsGateway gateway) {
        super(repository, clock);
        this.gateway = gateway;
    }

    @Override
    public ChannelType type() {
        return ChannelType.SMS;
    }

    @Override
    protected DeliveryStatus send(NotificationMessage m) {
        String phone = m.recipient().getPhone();
        if (phone == null || phone.isBlank()) {
            throw new IllegalStateException("No phone number on file for " + m.recipient().getFullName());
        }
        gateway.send(phone, m.shortText());
        return gateway.isSimulated() ? DeliveryStatus.SIMULATED : DeliveryStatus.SENT;
    }
}
