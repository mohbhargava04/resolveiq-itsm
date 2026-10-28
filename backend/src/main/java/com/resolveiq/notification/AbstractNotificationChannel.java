package com.resolveiq.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;

/**
 * <b>Template Method pattern</b>: the delivery algorithm (attempt, catch failures, persist the outcome)
 * is fixed here; subclasses supply only the channel-specific {@link #send(NotificationMessage)} step.
 */
public abstract class AbstractNotificationChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(AbstractNotificationChannel.class);

    private final NotificationRepository repository;
    private final Clock clock;

    protected AbstractNotificationChannel(NotificationRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public final Notification deliver(NotificationMessage message) {
        DeliveryStatus status;
        String error = null;
        try {
            status = send(message);
        } catch (RuntimeException ex) {
            status = DeliveryStatus.FAILED;
            error = ex.getMessage();
            log.warn("{} delivery to {} failed: {}", type(), message.recipient().getEmail(), ex.getMessage());
        }
        Notification saved = repository.save(new Notification(message, type(), status, error, clock.instant()));
        afterDelivery(saved);
        return saved;
    }

    /** Channel-specific delivery. Return SENT for real delivery or SIMULATED for a simulation. */
    protected abstract DeliveryStatus send(NotificationMessage message);

    /** Optional hook (e.g. push the new in-app notification to the browser). */
    protected void afterDelivery(Notification notification) {
    }
}
