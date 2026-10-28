package com.resolveiq.notification.gateway;

import com.resolveiq.common.exception.ExternalServiceException;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Real adapter: adapts Spring's {@link JavaMailSender} (SMTP - works with Gmail, Outlook, Mailtrap,
 * SendGrid SMTP relay ...) to the {@link EmailGateway} interface.
 */
public class SmtpEmailGateway implements EmailGateway {

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public SmtpEmailGateway(JavaMailSender mailSender, String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        try {
            mailSender.send(message);
        } catch (MailException ex) {
            throw new ExternalServiceException("SMTP delivery failed: " + ex.getMessage(), ex);
        }
    }

    @Override
    public boolean isSimulated() {
        return false;
    }

    @Override
    public String providerName() {
        return "SMTP";
    }
}
