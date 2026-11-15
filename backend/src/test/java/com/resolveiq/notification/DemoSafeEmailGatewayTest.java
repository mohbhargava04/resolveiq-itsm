package com.resolveiq.notification;

import com.resolveiq.notification.gateway.DemoSafeEmailGateway;
import com.resolveiq.notification.gateway.EmailGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DemoSafeEmailGatewayTest {

    /** Records what would have been sent, instead of sending it. */
    static class RecordingGateway implements EmailGateway {
        final List<String[]> sent = new ArrayList<>();
        public void send(String to, String subject, String body) { sent.add(new String[]{to, subject, body}); }
        public boolean isSimulated() { return false; }
        public String providerName() { return "SMTP"; }
    }

    @Test
    @DisplayName("MAIL-03 Real addresses get real mail; demo addresses go to the chosen inbox, or nowhere")
    void demoAddressesAreRedirected() {
        RecordingGateway smtp = new RecordingGateway();
        EmailGateway gateway = new DemoSafeEmailGateway(smtp, "moh@gmail.com");

        gateway.send("someone@bits-pilani.ac.in", "[INC-1042] Assigned", "body");
        gateway.send("ava@resolveiq.test", "[INC-1043] Resolved", "Your incident is resolved");

        assertThat(smtp.sent).hasSize(2);
        assertThat(smtp.sent.get(0)[0]).isEqualTo("someone@bits-pilani.ac.in");
        assertThat(smtp.sent.get(1)[0]).isEqualTo("moh@gmail.com");
        assertThat(smtp.sent.get(1)[1]).isEqualTo("[INC-1043] Resolved (for ava@resolveiq.test)");
        assertThat(smtp.sent.get(1)[2]).startsWith("[Demo copy - this message was addressed to ava@resolveiq.test]")
                .endsWith("Your incident is resolved");

        RecordingGateway noRedirect = new RecordingGateway();
        new DemoSafeEmailGateway(noRedirect, "").send("neha@resolveiq.test", "x", "y");
        assertThat(noRedirect.sent).isEmpty();
        assertThat(gateway.providerName()).isEqualTo("SMTP");
    }
}
