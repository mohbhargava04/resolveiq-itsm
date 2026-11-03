package com.resolveiq.web;

import com.resolveiq.notification.inbound.InboundEmail;
import com.resolveiq.notification.inbound.InboundEmailService;
import com.resolveiq.common.exception.UnauthorizedOperationException;
import com.resolveiq.security.CurrentUserService;
import com.resolveiq.security.crypto.Tokens;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * E-mail in. Mail providers (SendGrid Inbound Parse, Mailgun Routes, Postmark) turn a received e-mail
 * into an HTTP POST; this endpoint accepts that, authenticated with a shared secret header compared in
 * constant time. Administrators can also simulate an incoming e-mail from the delivery-log screen.
 */
@RestController
@Tag(name = "E-mail")
public class InboundEmailController {

    public record InboundRequest(@NotBlank @Size(max = 320) String from, @Size(max = 300) String subject,
                                 @Size(max = 20_000) String text, String serviceCode) {
    }

    public record InboundView(Long id, Instant receivedAt, String from, String subject, String excerpt, String outcome,
                              String incidentNumber, String detail) {
        static InboundView of(InboundEmail e) {
            return new InboundView(e.getId(), e.getReceivedAt(), e.getFromAddress(), e.getSubject(), e.getBodyExcerpt(),
                    e.getOutcome(), e.getIncidentNumber(), e.getDetail());
        }
    }

    private final InboundEmailService inbound;
    private final CurrentUserService currentUser;
    private final String secret;
    private final String address;

    public InboundEmailController(InboundEmailService inbound, CurrentUserService currentUser,
                                  @Value("${itsm.inbound-email.secret:}") String secret,
                                  @Value("${itsm.inbound-email.address:support@resolveiq.test}") String address) {
        this.inbound = inbound;
        this.currentUser = currentUser;
        this.secret = secret;
        this.address = address;
    }

    @PostMapping("/api/inbound/email")
    @Operation(summary = "Webhook for the mail provider (X-Inbound-Secret header required)")
    public Map<String, Object> receive(@RequestHeader(name = "X-Inbound-Secret", required = false) String providedSecret,
                                       @Valid @RequestBody InboundRequest r) {
        if (secret.isBlank() || !Tokens.constantTimeEquals(secret, providedSecret)) {
            throw new UnauthorizedOperationException("Inbound e-mail is not enabled, or the secret is wrong");
        }
        return result(inbound.receive(r.from(), r.subject(), r.text(), r.serviceCode()));
    }

    @PostMapping("/api/admin/inbound/simulate")
    @Operation(summary = "Demo: process an e-mail as if it had arrived at the support mailbox")
    public Map<String, Object> simulate(@Valid @RequestBody InboundRequest r) {
        currentUser.require();
        return result(inbound.receive(r.from(), r.subject(), r.text(), r.serviceCode()));
    }

    @GetMapping("/api/admin/inbound")
    public Map<String, Object> recent(@RequestParam(defaultValue = "50") int limit) {
        List<InboundView> rows = inbound.recent(limit).stream().map(InboundView::of).toList();
        return Map.of("address", address, "enabled", !secret.isBlank(), "emails", rows);
    }

    private static Map<String, Object> result(InboundEmailService.Result r) {
        return r.incidentNumber() == null ? Map.of("outcome", r.outcome(), "detail", r.detail())
                : Map.of("outcome", r.outcome(), "detail", r.detail(), "incidentNumber", r.incidentNumber());
    }
}
