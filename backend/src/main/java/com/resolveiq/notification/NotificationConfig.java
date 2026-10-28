package com.resolveiq.notification;

import com.resolveiq.notification.gateway.ConsoleEmailGateway;
import com.resolveiq.notification.gateway.EmailGateway;
import com.resolveiq.notification.gateway.SimulatedSmsGateway;
import com.resolveiq.notification.gateway.SmsGateway;
import com.resolveiq.notification.gateway.SmtpEmailGateway;
import com.resolveiq.notification.gateway.TwilioSmsGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Chooses the concrete adapters at start-up from configuration: real providers when credentials
 * are supplied, simulations otherwise. The rest of the system never knows which one it got.
 */
@Configuration
public class NotificationConfig {

    private static final Logger log = LoggerFactory.getLogger(NotificationConfig.class);

    @Bean
    EmailGateway emailGateway(@Value("${spring.mail.host:}") String smtpHost,
                              @Value("${itsm.notifications.from-address}") String from,
                              ObjectProvider<JavaMailSender> mailSender) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (!smtpHost.isBlank() && sender != null) {
            log.info("E-mail notifications: SMTP via {}", smtpHost);
            return new SmtpEmailGateway(sender, from);
        }
        log.info("E-mail notifications: simulated (set ITSM_SMTP_HOST to send real e-mail)");
        return new ConsoleEmailGateway();
    }

    @Bean
    SmsGateway smsGateway(@Value("${itsm.notifications.twilio.account-sid:}") String sid,
                          @Value("${itsm.notifications.twilio.auth-token:}") String token,
                          @Value("${itsm.notifications.twilio.from-number:}") String from) {
        if (!sid.isBlank() && !token.isBlank() && !from.isBlank()) {
            log.info("SMS notifications: Twilio");
            return new TwilioSmsGateway(sid, token, from);
        }
        log.info("SMS notifications: simulated");
        return new SimulatedSmsGateway();
    }
}
