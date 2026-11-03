package com.resolveiq.notification.inbound;

import com.resolveiq.audit.AuditAction;
import com.resolveiq.audit.AuditService;
import com.resolveiq.catalog.ItService;
import com.resolveiq.catalog.ItServiceRepository;
import com.resolveiq.intelligence.text.SimilarityEngine;
import com.resolveiq.common.exception.ItsmException;
import com.resolveiq.incident.IncidentService;
import com.resolveiq.incident.ReportIncidentCommand;
import com.resolveiq.incident.domain.Impact;
import com.resolveiq.incident.domain.Incident;
import com.resolveiq.incident.domain.IncidentStatus;
import com.resolveiq.incident.domain.NoteType;
import com.resolveiq.incident.domain.Urgency;
import com.resolveiq.security.EmailDomainPolicy;
import com.resolveiq.user.UserRepository;
import com.resolveiq.user.domain.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

/**
 * E-mail in: every notification e-mail carries {@code [INC-1042]} in its subject, so replying to it
 * adds the reply to that incident. If the incident is waiting for the reporter's answer, the reply
 * <i>is</i> the answer and the SLA clock restarts. An e-mail without a reference opens a new incident.
 *
 * <p>Only active users from allowed company domains are accepted, and the sender must be allowed to
 * see the incident - a forged reply to someone else's incident is logged and ignored. (A production
 * mail provider additionally verifies SPF/DKIM before forwarding the message here.)</p>
 */
@Service
public class InboundEmailService {

    /** Used when nothing in the e-mail points to a particular service. */
    static final String DEFAULT_SERVICE = "DEVICES";

    public record Result(String outcome, String incidentNumber, String detail) {
    }

    private final InboundEmailRepository log;
    private final UserRepository users;
    private final IncidentService incidents;
    private final EmailDomainPolicy domains;
    private final AuditService audit;
    private final ItServiceRepository services;
    private final SimilarityEngine similarity;
    private final Clock clock;
    private final TransactionTemplate tx;

    public InboundEmailService(InboundEmailRepository log, UserRepository users, IncidentService incidents,
                               EmailDomainPolicy domains, AuditService audit, ItServiceRepository services,
                               SimilarityEngine similarity, Clock clock, PlatformTransactionManager txManager) {
        this.tx = new TransactionTemplate(txManager);
        this.tx.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRED);
        this.log = log;
        this.users = users;
        this.incidents = incidents;
        this.domains = domains;
        this.audit = audit;
        this.services = services;
        this.similarity = similarity;
        this.clock = clock;
    }

    /**
     * Processes one e-mail in its own transaction (a rejected e-mail rolls back cleanly), then writes
     * the log entry in another, so the log always records what happened.
     */
    public Result receive(String from, String subject, String body, String fallbackServiceCode) {
        Result result;
        try {
            result = tx.execute(status -> process(from, subject, body, fallbackServiceCode));
        } catch (ItsmException ex) {
            EmailReplyParser.address(from).flatMap(users::findByEmailIgnoreCase).ifPresent(sender ->
                    audit.recordSecurityEvent(sender, sender.getEmail(), AuditAction.EMAIL_RECEIVED,
                            "E-mail from " + sender.getEmail() + " rejected: " + ex.getMessage()));
            result = new Result("REJECTED", EmailReplyParser.incidentNumber(subject).orElse(null), ex.getMessage());
        }
        log.save(new InboundEmail(clock.instant(), from == null ? "(unknown)" : from, subject,
                EmailReplyParser.newText(body), result.outcome(), result.incidentNumber(), result.detail()));
        return result;
    }

    private Result process(String from, String subject, String body, String fallbackServiceCode) {
        Optional<String> address = EmailReplyParser.address(from);
        if (address.isEmpty()) {
            return new Result("REJECTED", null, "No sender address");
        }
        if (!domains.isAllowed(address.get())) {
            return new Result("REJECTED", null, "Sender is not from a company domain");
        }
        User sender = users.findByEmailIgnoreCase(address.get()).filter(User::isActive).orElse(null);
        if (sender == null) {
            return new Result("REJECTED", null, "No active account for " + address.get());
        }
        String text = EmailReplyParser.newText(body);
        Optional<String> number = EmailReplyParser.incidentNumber(subject);
        if (number.isPresent()) {
            return reply(number.get(), text, sender);
        }
        return create(EmailReplyParser.cleanSubject(subject), text, fallbackServiceCode, sender);
    }

    private Result reply(String number, String text, User sender) {
        if (text.isBlank()) {
            return new Result("IGNORED", number, "The reply had no new text");
        }
        Incident incident = incidents.get(number, sender); // throws if the sender may not see it
        String content = text.length() > 3900 ? text.substring(0, 3900) + "…" : text;
        if (incident.getStatus() == IncidentStatus.AWAITING_INFO && incident.getReporter().getId().equals(sender.getId())) {
            incidents.provideInformation(number, content, sender);
            return new Result("ANSWERED_QUESTION", number, "Reply recorded as the requested information; SLA resumed");
        }
        boolean staff = sender.getRole() != com.resolveiq.user.domain.Role.EMPLOYEE;
        incidents.addNote(number, staff ? NoteType.INVESTIGATION : NoteType.COMMENT, "(by e-mail) " + content, false, sender);
        return new Result("COMMENT_ADDED", number, "Reply added to the conversation");
    }

    private Result create(String subject, String text, String fallbackServiceCode, User sender) {
        String title = subject.isBlank() ? (text.length() > 60 ? text.substring(0, 60) + "…" : text) : subject;
        if (title.isBlank()) {
            return new Result("IGNORED", null, "Empty e-mail");
        }
        String description = text.isBlank() ? title : text;
        IncidentService.ReportOutcome outcome = incidents.report(new ReportIncidentCommand(
                title.length() > 160 ? title.substring(0, 159) + "…" : title,
                description.length() > 4000 ? description.substring(0, 3999) + "…" : description,
                fallbackServiceCode == null ? guessService(title + " " + description) : fallbackServiceCode, null,
                Impact.LOW, Urgency.MEDIUM, 1,
                true), sender);
        return new Result("INCIDENT_CREATED", outcome.incident().getNumber(), "New incident opened from e-mail");
    }

    /** Picks the catalogue service whose name and description best match the e-mail (TF-IDF). */
    String guessService(String text) {
        List<ItService> active = services.findAll().stream().filter(ItService::isActive).toList();
        return similarity.rank(text, active, s -> s.getName() + " " + s.getName() + " " + s.getDescription()).stream()
                .filter(s -> s.similarity() >= 0.08)
                .findFirst().map(s -> s.item().getCode()).orElse(DEFAULT_SERVICE);
    }

    @Transactional(readOnly = true)
    public java.util.List<InboundEmail> recent(int limit) {
        return log.findAllByOrderByReceivedAtDescIdDesc(org.springframework.data.domain.PageRequest.of(0, Math.min(Math.max(limit, 1), 200)));
    }
}
