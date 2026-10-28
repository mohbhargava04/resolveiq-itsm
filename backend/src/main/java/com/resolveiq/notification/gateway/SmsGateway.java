package com.resolveiq.notification.gateway;

/** <b>Adapter pattern</b> target interface for SMS providers. */
public interface SmsGateway {

    void send(String phoneNumber, String text);

    boolean isSimulated();

    String providerName();
}
