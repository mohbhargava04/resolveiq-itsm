package com.resolveiq.incident.domain;

import com.resolveiq.common.exception.BusinessRuleViolationException;
import com.resolveiq.common.exception.InvalidIncidentException;
import com.resolveiq.common.exception.InvalidStateTransitionException;
import com.resolveiq.common.exception.UnauthorizedOperationException;
import com.resolveiq.incident.event.IncidentEvent;
import com.resolveiq.incident.event.IncidentStatusChanged;
import com.resolveiq.sla.SlaStatus;
import com.resolveiq.user.domain.Employee;
import com.resolveiq.user.domain.ExperienceLevel;
import com.resolveiq.user.domain.TeamLead;
import com.resolveiq.user.domain.Technician;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static com.resolveiq.support.TestData.T0;
import static com.resolveiq.support.TestData.classified;
import static com.resolveiq.support.TestData.employee;
import static com.resolveiq.support.TestData.newIncident;
import static com.resolveiq.support.TestData.service;
import static com.resolveiq.support.TestData.teamLead;
import static com.resolveiq.support.TestData.technician;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** State pattern: the incident lifecycle and its business rules, tested without Spring or a database. */
class IncidentLifecycleTest {

    private Employee reporter;
    private Technician tech;
    private TeamLead lead;
    private Incident incident;

    @BeforeEach
    void setUp() {
        reporter = employee("Ava Sharma");
        tech = technician("Rahul Verma", ExperienceLevel.SENIOR, "NETWORK");
        lead = teamLead("Neha Joshi", "NETWORK");
        incident = classified(reporter, Priority.P2, 15, 120, T0);
    }

    private Instant at(int minutes) {
        return T0.plus(Duration.ofMinutes(minutes));
    }

    @Test
    @DisplayName("TC-01 Happy path walks every state from CLASSIFIED to CLOSED")
    void happyPath() {
        incident.assignTo(tech, "test", Actor.of(lead), at(1));
        incident.acknowledge(Actor.of(tech), at(5));
        incident.startWork(Actor.of(tech), at(6));
        incident.resolve(new Resolution("Replaced switch", "Hardware fault", tech), Actor.of(tech), at(60));
        incident.verify(Actor.of(reporter), at(70));
        incident.close("Thanks", Actor.of(reporter), at(71));

        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.CLOSED);
        assertThat(incident.getClosedAt()).isEqualTo(at(71));
        assertThat(incident.slaStatus(at(80))).isEqualTo(SlaStatus.MET);
        List<IncidentStatus> visited = incident.pullEvents().stream()
                .filter(IncidentStatusChanged.class::isInstance).map(e -> ((IncidentStatusChanged) e).to()).toList();
        assertThat(visited).containsExactly(IncidentStatus.CLASSIFIED, IncidentStatus.ASSIGNED,
                IncidentStatus.ACKNOWLEDGED, IncidentStatus.IN_PROGRESS, IncidentStatus.RESOLVED,
                IncidentStatus.VERIFIED, IncidentStatus.CLOSED);
    }

    @Test
    @DisplayName("TC-02 NEW → CLOSED is rejected (no skipping intermediate processing)")
    void cannotCloseNewIncident() {
        Incident fresh = newIncident(reporter, service("EMAIL", com.resolveiq.catalog.Criticality.HIGH), T0);
        assertThatThrownBy(() -> fresh.close("done", Actor.of(lead), at(1)))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("NEW");
        assertThat(fresh.getStatus()).isEqualTo(IncidentStatus.NEW);
    }

    @Test
    @DisplayName("TC-03 Resolving an incident nobody has started working on is rejected")
    void cannotResolveFromAssigned() {
        incident.assignTo(tech, "test", Actor.of(lead), at(1));
        assertThatThrownBy(() -> incident.resolve(new Resolution("x", null, tech), Actor.of(tech), at(2)))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    @DisplayName("TC-04 Only the assigned technician may acknowledge")
    void onlyAssigneeAcknowledges() {
        Technician other = technician("Meera Das", ExperienceLevel.MID, "DATABASE");
        incident.assignTo(tech, "test", Actor.of(lead), at(1));
        assertThatThrownBy(() -> incident.acknowledge(Actor.of(other), at(2)))
                .isInstanceOf(UnauthorizedOperationException.class);
        assertThatThrownBy(() -> incident.acknowledge(Actor.of(reporter), at(2)))
                .isInstanceOf(UnauthorizedOperationException.class);
    }

    @Test
    @DisplayName("TC-05 Employees cannot assign incidents")
    void employeeCannotAssign() {
        assertThatThrownBy(() -> incident.assignTo(tech, "manual", Actor.of(reporter), at(1)))
                .isInstanceOf(UnauthorizedOperationException.class);
    }

    @Test
    @DisplayName("TC-06 Off-duty technicians cannot receive work")
    void offDutyTechnicianRejected() {
        tech.goOffDuty();
        assertThatThrownBy(() -> incident.assignTo(tech, "manual", Actor.of(lead), at(1)))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    @DisplayName("TC-07 Requesting information pauses the SLA; answering resumes it and extends the deadline")
    void awaitingInfoPausesSla() {
        incident.assignTo(tech, "t", Actor.of(lead), at(1));
        incident.acknowledge(Actor.of(tech), at(2));
        incident.startWork(Actor.of(tech), at(3));
        Instant dueBefore = incident.getSla().getResolutionDueAt();

        incident.requestInformation("Which browser?", Actor.of(tech), at(10));
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.AWAITING_INFO);
        assertThat(incident.slaStatus(at(50))).isEqualTo(SlaStatus.PAUSED);

        incident.provideInformation("Chrome 129", Actor.of(reporter), at(40));
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.IN_PROGRESS);
        assertThat(incident.getSla().getResolutionDueAt()).isEqualTo(dueBefore.plus(Duration.ofMinutes(30)));
    }

    @Test
    @DisplayName("TC-08 Reporter can reopen a resolution that did not work")
    void reopen() {
        incident.assignTo(tech, "t", Actor.of(lead), at(1));
        incident.acknowledge(Actor.of(tech), at(2));
        incident.startWork(Actor.of(tech), at(3));
        incident.resolve(new Resolution("Restarted", null, tech), Actor.of(tech), at(20));
        incident.reopen("Still broken", Actor.of(reporter), at(25));
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.IN_PROGRESS);
        assertThat(incident.getSla().getResolvedAt()).isNull();
    }

    @Test
    @DisplayName("TC-09 Feedback: reporter only, once, rating 1-5")
    void feedbackRules() {
        incident.assignTo(tech, "t", Actor.of(lead), at(1));
        incident.acknowledge(Actor.of(tech), at(2));
        incident.startWork(Actor.of(tech), at(3));
        assertThatThrownBy(() -> incident.submitFeedback(5, null, Actor.of(reporter), at(4)))
                .isInstanceOf(InvalidStateTransitionException.class); // not resolved yet
        incident.resolve(new Resolution("Fixed", null, tech), Actor.of(tech), at(20));
        assertThatThrownBy(() -> incident.submitFeedback(5, null, Actor.of(tech), at(21)))
                .isInstanceOf(UnauthorizedOperationException.class);
        assertThatThrownBy(() -> incident.submitFeedback(6, null, Actor.of(reporter), at(21)))
                .isInstanceOf(IllegalArgumentException.class);
        incident.submitFeedback(4, "ok", Actor.of(reporter), at(21));
        assertThatThrownBy(() -> incident.submitFeedback(5, null, Actor.of(reporter), at(22)))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    @DisplayName("TC-10 Closed incidents are terminal: no notes, no transitions")
    void closedIsTerminal() {
        Incident other = classified(reporter, Priority.P3, 60, 480, T0);
        incident.closeAsDuplicateOf(other, Actor.of(lead), at(1));
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.CLOSED);
        assertThat(incident.isDuplicateConfirmed()).isTrue();
        assertThat(incident.allowedNextStatuses()).isEmpty();
        assertThatThrownBy(() -> incident.addNote(NoteType.COMMENT, "hello", false, reporter, at(2)))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    @DisplayName("TC-11 An incident cannot be a duplicate of itself")
    void notDuplicateOfItself() {
        assertThatThrownBy(() -> incident.closeAsDuplicateOf(incident, Actor.of(lead), at(1)))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    @DisplayName("TC-12 Invalid input: blank description and zero affected users are rejected")
    void invalidInput() {
        assertThatThrownBy(() -> new Incident("INC-1", "t", "  ", reporter,
                service("X", com.resolveiq.catalog.Criticality.LOW), Impact.LOW, Urgency.LOW, 1, T0))
                .isInstanceOf(InvalidIncidentException.class);
        assertThatThrownBy(() -> new Incident("INC-1", "t", "d", reporter,
                service("X", com.resolveiq.catalog.Criticality.LOW), Impact.LOW, Urgency.LOW, 0, T0))
                .isInstanceOf(InvalidIncidentException.class);
    }

    @Test
    @DisplayName("TC-13 Employees cannot write internal notes; staff can")
    void internalNotes() {
        assertThatThrownBy(() -> incident.addNote(NoteType.COMMENT, "secret", true, reporter, at(1)))
                .isInstanceOf(UnauthorizedOperationException.class);
        incident.addNote(NoteType.INVESTIGATION, "Checked logs", true, tech, at(1));
        assertThat(incident.getNotes()).anyMatch(IncidentNote::isInternal);
    }

    @Test
    @DisplayName("Available actions reflect both state and role")
    void availableActions() {
        incident.assignTo(tech, "t", Actor.of(lead), at(1));
        assertThat(incident.availableActions(Actor.of(tech))).contains(IncidentAction.ACKNOWLEDGE)
                .doesNotContain(IncidentAction.RESOLVE, IncidentAction.ASSIGN);
        assertThat(incident.availableActions(Actor.of(lead))).contains(IncidentAction.ASSIGN, IncidentAction.ESCALATE);
        assertThat(incident.availableActions(Actor.of(reporter))).doesNotContain(IncidentAction.ACKNOWLEDGE,
                IncidentAction.ASSIGN, IncidentAction.ADD_INTERNAL_NOTE);
    }

    @Test
    @DisplayName("Every change records a domain event for the observers")
    void eventsRecorded() {
        incident.pullEvents();
        incident.assignTo(tech, "Smart", Actor.of(lead), at(1));
        List<IncidentEvent> events = incident.pullEvents();
        assertThat(events).extracting(IncidentEvent::type).containsExactly("IncidentAssigned", "IncidentStatusChanged");
        assertThat(incident.pullEvents()).isEmpty();
    }
}
