package com.resolveiq.notification;

public enum DeliveryStatus {
    /** Delivered by a real external provider (SMTP server, SMS API). */
    SENT,
    /** Delivered by a simulation adapter (logged instead of sent) - acceptable per the brief. */
    SIMULATED,
    FAILED
}
