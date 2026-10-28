package com.resolveiq.notification.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Simulation adapter: logs the SMS instead of sending it. */
public class SimulatedSmsGateway implements SmsGateway {

    private static final Logger log = LoggerFactory.getLogger(SimulatedSmsGateway.class);

    @Override
    public void send(String phoneNumber, String text) {
        String masked = phoneNumber.length() > 4
                ? "*".repeat(phoneNumber.length() - 4) + phoneNumber.substring(phoneNumber.length() - 4)
                : phoneNumber;
        log.info("[SIMULATED SMS] to={} text=\"{}\"", masked, text);
    }

    @Override
    public boolean isSimulated() {
        return true;
    }

    @Override
    public String providerName() {
        return "Simulated SMS";
    }
}
