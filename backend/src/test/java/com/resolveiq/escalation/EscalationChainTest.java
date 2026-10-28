package com.resolveiq.escalation;

import com.resolveiq.incident.domain.Actor;
import com.resolveiq.incident.domain.EscalationLevel;
import com.resolveiq.incident.domain.Incident;
import com.resolveiq.incident.domain.Priority;
import com.resolveiq.notification.RecipientDirectory;
import com.resolveiq.settings.SettingKey;
import com.resolveiq.settings.SettingsService;
import com.resolveiq.user.domain.Administrator;
import com.resolveiq.user.domain.ExperienceLevel;
import com.resolveiq.user.domain.ItManager;
import com.resolveiq.user.domain.TeamLead;
import com.resolveiq.user.domain.Technician;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.resolveiq.support.TestData.T0;
import static com.resolveiq.support.TestData.admin;
import static com.resolveiq.support.TestData.classified;
import static com.resolveiq.support.TestData.employee;
import static com.resolveiq.support.TestData.teamLead;
import static com.resolveiq.support.TestData.technician;
import static com.resolveiq.support.TestData.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Chain of Responsibility: Technician → Team Lead → System Administrator → IT Manager. */
class EscalationChainTest {

    private EscalationService service;
    private TeamLead lead;
    private Technician tech;

    @BeforeEach
    void setUp() {
        lead = teamLead("Neha", "NETWORK");
        tech = technician("Rahul", ExperienceLevel.SENIOR, "NETWORK");
        Administrator sysAdmin = admin("Ananya");
        ItManager manager = withId(new ItManager("Sanjay", "sanjay@test.io", "h", null, "IT"));
        RecipientDirectory directory = mock(RecipientDirectory.class);
        when(directory.teamLeadFor(any())).thenAnswer(inv -> Optional.of(lead));
        when(directory.systemAdministrators()).thenReturn(List.of(sysAdmin));
        when(directory.managers()).thenReturn(List.of(manager));
        SettingsService settings = mock(SettingsService.class);
        when(settings.isEnabled(SettingKey.AUTO_ESCALATION)).thenReturn(true);
        service = new EscalationService(new TeamLeadEscalationHandler(directory),
                new AdministratorEscalationHandler(directory), new ItManagerEscalationHandler(directory), settings);
    }

    private Instant at(int minutes) {
        return T0.plus(Duration.ofMinutes(minutes));
    }

    @Test
    @DisplayName("TC-28 An unacknowledged P1 climbs the chain one level per response window")
    void climbsChain() {
        Incident p1 = classified(employee("Ava"), Priority.P1, 5, 30, T0);
        p1.assignTo(tech, "t", Actor.SYSTEM, at(1));

        assertThat(service.evaluate(p1, at(4))).isEmpty();                        // still within response target
        assertThat(service.evaluate(p1, at(6))).get().extracting(EscalationDecision::level)
                .isEqualTo(EscalationLevel.TEAM_LEAD);
        assertThat(p1.getEscalatedTo()).isEqualTo(lead);

        assertThat(service.evaluate(p1, at(8))).isEmpty();                        // cool-down between levels
        assertThat(service.evaluate(p1, at(11))).get().extracting(EscalationDecision::level)
                .isEqualTo(EscalationLevel.ADMINISTRATOR);
        assertThat(service.evaluate(p1, at(16))).get().extracting(EscalationDecision::level)
                .isEqualTo(EscalationLevel.IT_MANAGER);
        assertThat(service.evaluate(p1, at(60))).isEmpty();                       // top of the chain
    }

    @Test
    @DisplayName("Acknowledged incidents within SLA are not escalated")
    void acknowledgedNotEscalated() {
        Incident p1 = classified(employee("Ava"), Priority.P1, 5, 30, T0);
        p1.assignTo(tech, "t", Actor.SYSTEM, at(1));
        p1.acknowledge(Actor.of(tech), at(3));
        assertThat(service.evaluate(p1, at(20))).isEmpty();
    }

    @Test
    @DisplayName("Low-priority incidents are not escalated for a slow acknowledgement")
    void lowPriorityNotEscalated() {
        Incident p4 = classified(employee("Ava"), Priority.P4, 240, 1440, T0);
        assertThat(service.evaluate(p4, at(300))).isEmpty();
    }

    @Test
    @DisplayName("Manual escalation moves exactly one level up")
    void manual() {
        Incident p3 = classified(employee("Ava"), Priority.P3, 60, 480, T0);
        p3.assignTo(tech, "t", Actor.SYSTEM, at(1));
        EscalationDecision d = service.escalateManually(p3, "Need DB access", Actor.of(tech), at(2));
        assertThat(d.level()).isEqualTo(EscalationLevel.TEAM_LEAD);
        assertThat(p3.getEscalationLevel()).isEqualTo(EscalationLevel.TEAM_LEAD);
    }
}
