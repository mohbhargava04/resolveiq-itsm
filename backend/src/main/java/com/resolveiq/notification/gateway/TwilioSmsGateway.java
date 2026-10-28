package com.resolveiq.notification.gateway;

import com.resolveiq.common.exception.ExternalServiceException;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Real adapter for the Twilio Messaging REST API. Activated only when Twilio credentials are
 * configured (see NotificationConfig); otherwise the simulated adapter is used.
 */
public class TwilioSmsGateway implements SmsGateway {

    private final RestClient client;
    private final String accountSid;
    private final String fromNumber;

    public TwilioSmsGateway(String accountSid, String authToken, String fromNumber) {
        this.accountSid = accountSid;
        this.fromNumber = fromNumber;
        String basic = Base64.getEncoder()
                .encodeToString((accountSid + ":" + authToken).getBytes(StandardCharsets.UTF_8));
        this.client = RestClient.builder()
                .baseUrl("https://api.twilio.com/2010-04-01")
                .defaultHeader("Authorization", "Basic " + basic)
                .build();
    }

    @Override
    public void send(String phoneNumber, String text) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("To", phoneNumber);
        form.add("From", fromNumber);
        form.add("Body", text);
        try {
            client.post()
                    .uri("/Accounts/{sid}/Messages.json", accountSid)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            throw new ExternalServiceException("Twilio SMS delivery failed: " + ex.getMessage(), ex);
        }
    }

    @Override
    public boolean isSimulated() {
        return false;
    }

    @Override
    public String providerName() {
        return "Twilio";
    }
}
