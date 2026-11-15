package com.resolveiq.notification.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;

/**
 * <b>Decorator pattern</b> - wraps any {@link EmailGateway} and adds one behaviour without changing it:
 * mail for addresses that can never be delivered (the reserved {@code .test}, {@code .example},
 * {@code .invalid} and {@code .localhost} domains used by the demo accounts) is sent to one real
 * inbox instead, with the intended recipient shown at the top. Real addresses pass straight through.
 *
 * <p>Without a redirect inbox such mail is skipped, so a demo never bounces dozens of messages back
 * into the sender's mailbox.</p>
 */
public class DemoSafeEmailGateway implements EmailGateway {

    private static final Logger log = LoggerFactory.getLogger(DemoSafeEmailGateway.class);
    private static final List<String> RESERVED_TLDS = List.of(".test", ".example", ".invalid", ".localhost");

    private final EmailGateway delegate;
    private final String redirectTo;

    public DemoSafeEmailGateway(EmailGateway delegate, String redirectTo) {
        this.delegate = delegate;
        this.redirectTo = redirectTo == null || redirectTo.isBlank() ? null : redirectTo.trim();
    }

    @Override
    public void send(String to, String subject, String body) {
        if (!isUndeliverable(to)) {
            delegate.send(to, subject, body);
            return;
        }
        if (redirectTo == null) {
            log.info("E-mail to demo address {} not sent (set ITSM_MAIL_REDIRECT_TO to receive it)", to);
            return;
        }
        delegate.send(redirectTo, subject + " (for " + to + ")",
                "[Demo copy - this message was addressed to " + to + "]\n\n" + body);
    }

    static boolean isUndeliverable(String address) {
        if (address == null) {
            return true;
        }
        String domain = address.substring(address.lastIndexOf('@') + 1).toLowerCase(Locale.ROOT).trim();
        return RESERVED_TLDS.stream().anyMatch(domain::endsWith);
    }

    @Override
    public boolean isSimulated() {
        return delegate.isSimulated();
    }

    @Override
    public String providerName() {
        return delegate.providerName();
    }
}
