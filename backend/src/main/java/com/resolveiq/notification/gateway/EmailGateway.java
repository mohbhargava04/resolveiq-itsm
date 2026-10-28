package com.resolveiq.notification.gateway;

/**
 * <b>Adapter pattern</b> - the target interface. The e-mail channel depends only on this simple
 * contract; each adapter translates it to a concrete provider API (SMTP via JavaMail, or a
 * console simulation). Switching provider (SendGrid, AWS SES...) means writing one more adapter.
 */
public interface EmailGateway {

    void send(String to, String subject, String body);

    /** True for simulation adapters, so the delivery log can say SIMULATED rather than SENT. */
    boolean isSimulated();

    String providerName();
}
