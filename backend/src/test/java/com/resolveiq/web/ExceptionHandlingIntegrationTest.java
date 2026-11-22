package com.resolveiq.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.resolveiq.catalog.Criticality;
import com.resolveiq.catalog.ItService;
import com.resolveiq.common.exception.ErrorCode;
import com.resolveiq.common.exception.MissingSlaConfigurationException;
import com.resolveiq.incident.domain.Priority;
import com.resolveiq.notification.DeliveryStatus;
import com.resolveiq.notification.EmailNotificationChannel;
import com.resolveiq.notification.Notification;
import com.resolveiq.notification.NotificationMessage;
import com.resolveiq.notification.NotificationRepository;
import com.resolveiq.notification.gateway.SmtpEmailGateway;
import com.resolveiq.sla.SlaPolicyRepository;
import com.resolveiq.sla.SlaPolicyResolver;
import com.resolveiq.support.IntegrationTestBase;
import com.resolveiq.user.domain.Employee;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The exceptional situations listed in the brief that are not covered elsewhere: an unavailable or overloaded
 * technician, a missing SLA configuration, a database outage, a concurrent edit and a failing external service.
 */
class ExceptionHandlingIntegrationTest extends IntegrationTestBase {

    private long userId(String email) throws Exception {
        for (JsonNode u : get("admin", "/api/admin/users?size=100").body()) {
            if (u.get("email").asText().equals(email)) {
                return u.get("id").asLong();
            }
        }
        throw new AssertionError("No user " + email);
    }

    @Test
    @DisplayName("EXC-01 Assigning to an off-duty technician is refused with 409 TECHNICIAN_UNAVAILABLE")
    void offDutyTechnician() throws Exception {
        assertThat(put("rahul", "/api/technicians/me/availability", Map.of("available", false)).status()).isEqualTo(200);
        Response r = post("neha", "/api/incidents/INC-1039/assign", Map.of("technicianId", userId("rahul@resolveiq.test")));
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.code()).isEqualTo("TECHNICIAN_UNAVAILABLE");
        assertThat(get("neha", "/api/incidents/INC-1039").body().get("status").asText())
                .as("the incident is unchanged").isEqualTo("CLASSIFIED");
    }

    @Test
    @DisplayName("EXC-02 Assigning to a technician at capacity is refused with 409 WORKLOAD_EXCEEDED")
    void technicianAtCapacity() throws Exception {
        long kabir = userId("kabir@resolveiq.test");   // two active incidents in the demo data
        assertThat(put("admin", "/api/admin/users/" + kabir, Map.of("maxActiveIncidents", 1)).status()).isEqualTo(200);
        Response r = post("neha", "/api/incidents/INC-1039/assign", Map.of("technicianId", kabir));
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.code()).isEqualTo("WORKLOAD_EXCEEDED");
    }

    @Test
    @DisplayName("EXC-03 No service-specific and no default SLA policy: MissingSlaConfigurationException (422)")
    void missingSlaConfiguration() {
        SlaPolicyRepository repository = mock(SlaPolicyRepository.class);
        when(repository.findActiveForService(any(), any())).thenReturn(Optional.empty());
        when(repository.findActiveDefault(any())).thenReturn(Optional.empty());
        ItService service = new ItService("PAYMENT", "Payment Gateway", "Payments", Criticality.CRITICAL, "Finance", 500);

        assertThatThrownBy(() -> new SlaPolicyResolver(repository).resolve(service, Priority.P1))
                .isInstanceOf(MissingSlaConfigurationException.class)
                .satisfies(ex -> assertThat(((MissingSlaConfigurationException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.MISSING_SLA_CONFIGURATION))
                .hasMessageContaining("Payment Gateway");
        assertThat(ErrorCode.MISSING_SLA_CONFIGURATION.status().value()).isEqualTo(422);
    }

    @Test
    @DisplayName("EXC-04 Database failure becomes 503 DATABASE_UNAVAILABLE, never a stack trace")
    void databaseFailure() {
        ResponseEntity<ProblemDetail> r = new GlobalExceptionHandler().handleDatabaseDown(
                new DataAccessResourceFailureException("Connection refused"), new MockHttpServletRequest("GET", "/api/incidents"));
        assertThat(r.getStatusCode().value()).isEqualTo(503);
        assertThat(r.getBody().getProperties()).containsEntry("code", "DATABASE_UNAVAILABLE");
        assertThat(r.getBody().getDetail()).doesNotContain("Connection refused");
    }

    @Test
    @DisplayName("EXC-05 Two people editing the same incident: the second gets 409 CONCURRENT_MODIFICATION")
    void concurrentModification() {
        ResponseEntity<ProblemDetail> r = new GlobalExceptionHandler().handleConcurrent(
                new OptimisticLockingFailureException("stale version"), new MockHttpServletRequest("POST", "/api/incidents/INC-1/resolve"));
        assertThat(r.getStatusCode().value()).isEqualTo(409);
        assertThat(r.getBody().getProperties()).containsEntry("code", "CONCURRENT_MODIFICATION");
    }

    @Test
    @DisplayName("EXC-06 Network failure at the mail server is recorded as FAILED and never breaks the incident operation")
    void externalServiceFailure() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        doThrow(new MailSendException("Connection timed out")).when(mailSender).send(any(SimpleMailMessage.class));
        NotificationRepository repository = mock(NotificationRepository.class);
        when(repository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        EmailNotificationChannel channel = new EmailNotificationChannel(repository,
                Clock.fixed(Instant.parse("2026-11-22T10:00:00Z"), ZoneOffset.UTC),
                new SmtpEmailGateway(mailSender, "resolveiq@example.com"));
        Employee ava = new Employee("Ava Sharma", "ava@example.com", "hash", null, "CS");

        Notification n = channel.deliver(new NotificationMessage(ava, "INC-1042 resolved", "body", "INC-1042", "STATUS", false));

        assertThat(n.getDeliveryStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(n.getErrorMessage()).contains("SMTP delivery failed");
    }
}
