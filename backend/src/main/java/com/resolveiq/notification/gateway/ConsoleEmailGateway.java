package com.resolveiq.notification.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Simulation adapter: writes the e-mail to the application log instead of sending it. */
public class ConsoleEmailGateway implements EmailGateway {

    private static final Logger log = LoggerFactory.getLogger(ConsoleEmailGateway.class);

    @Override
    public void send(String to, String subject, String body) {
        log.info("[SIMULATED EMAIL] to={} subject=\"{}\"", to, subject);
    }

    @Override
    public boolean isSimulated() {
        return true;
    }

    @Override
    public String providerName() {
        return "Console (simulated)";
    }
}
