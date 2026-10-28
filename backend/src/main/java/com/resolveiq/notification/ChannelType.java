package com.resolveiq.notification;

/** Delivery mechanisms. Adding one (e.g. SLACK) needs a new {@link NotificationChannel} bean only. */
public enum ChannelType {
    IN_APP,
    EMAIL,
    SMS
}
