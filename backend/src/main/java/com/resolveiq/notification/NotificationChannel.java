package com.resolveiq.notification;

/**
 * Contract for a delivery mechanism. Polymorphism: the observer calls {@code deliver} without
 * knowing whether it is talking to the in-app inbox, an e-mail server or an SMS provider.
 */
public interface NotificationChannel {

    ChannelType type();

    /** Delivers the message and records the delivery. Must never throw for a provider failure. */
    Notification deliver(NotificationMessage message);
}
