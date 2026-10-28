package com.resolveiq.assignment;

import com.resolveiq.incident.domain.Priority;
import com.resolveiq.user.domain.ExperienceLevel;
import com.resolveiq.user.domain.Technician;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static com.resolveiq.support.TestData.T0;
import static com.resolveiq.support.TestData.technician;
import static org.assertj.core.api.Assertions.assertThat;

/** Strategy + Template Method patterns for technician assignment. */
class AssignmentStrategyTest {

    private Technician rahul;   // network, senior
    private Technician meera;   // database, senior
    private Technician kabir;   // database, junior
    private AssignmentRequest dbIncident;

    @BeforeEach
    void setUp() {
        rahul = technician("Rahul", ExperienceLevel.SENIOR, "NETWORK");
        meera = technician("Meera", ExperienceLevel.SENIOR, "DATABASE");
        kabir = technician("Kabir", ExperienceLevel.JUNIOR, "DATABASE", "HARDWARE");
        dbIncident = new AssignmentRequest("INC-1", "DATABASE", "CORE_DB", Priority.P1, T0);
    }

    private static TechnicianProfile p(Technician t, int active, int onService) {
        return new TechnicianProfile(t, active, onService, 0);
    }

    @Test
    @DisplayName("TC-21 Skill-based assignment only considers skilled technicians, then the least busy")
    void skillBased() {
        List<AssignmentCandidate> ranked = new SkillBasedAssignmentStrategy()
                .rank(dbIncident, List.of(p(rahul, 0, 0), p(meera, 3, 0), p(kabir, 1, 0)));
        assertThat(ranked).extracting(c -> c.profile().technician()).containsExactly(kabir, meera);
    }

    @Test
    @DisplayName("TC-22 Load-based assignment picks the lowest utilisation regardless of skill")
    void loadBased() {
        List<AssignmentCandidate> ranked = new LoadBasedAssignmentStrategy()
                .rank(dbIncident, List.of(p(rahul, 0, 0), p(meera, 3, 0), p(kabir, 1, 0)));
        assertThat(ranked.getFirst().profile().technician()).isEqualTo(rahul);
    }

    @Test
    @DisplayName("TC-23 Technicians at capacity or off duty are never recommended (template method filter)")
    void capacityAndAvailability() {
        meera.goOffDuty();
        List<AssignmentCandidate> ranked = new LoadBasedAssignmentStrategy()
                .rank(dbIncident, List.of(p(rahul, 5, 0), p(meera, 0, 0), p(kabir, 4, 0)));
        assertThat(ranked).extracting(c -> c.profile().technician()).containsExactly(kabir);
    }

    @Test
    @DisplayName("Round-robin: never-assigned first, then whoever waited longest")
    void roundRobin() {
        rahul.recordAssignment(T0.minus(Duration.ofMinutes(5)));
        meera.recordAssignment(T0.minus(Duration.ofMinutes(50)));
        List<AssignmentCandidate> ranked = new RoundRobinAssignmentStrategy()
                .rank(dbIncident, List.of(p(rahul, 0, 0), p(meera, 0, 0), p(kabir, 0, 0)));
        assertThat(ranked).extracting(c -> c.profile().technician()).containsExactly(kabir, meera, rahul);
    }

    @Test
    @DisplayName("Experience-based prefers seniority and history on the service")
    void experience() {
        List<AssignmentCandidate> ranked = new ExperienceBasedAssignmentStrategy()
                .rank(dbIncident, List.of(p(kabir, 0, 8), p(meera, 0, 2)));
        assertThat(ranked.getFirst().profile().technician()).isEqualTo(meera);
    }

    @Test
    @DisplayName("TC-24 Smart recommendation balances skill, load, experience and seniority, with reasons")
    void smart() {
        SmartAssignmentStrategy smart = new SmartAssignmentStrategy();
        List<AssignmentCandidate> ranked = smart.rank(dbIncident,
                List.of(p(rahul, 0, 0), p(meera, 2, 4), p(kabir, 0, 0)));
        assertThat(ranked.getFirst().profile().technician()).isEqualTo(meera); // skilled, senior, experienced
        assertThat(ranked.getFirst().reasons()).anyMatch(r -> r.contains("Skilled in DATABASE"));
        assertThat(ranked.getLast().profile().technician()).isEqualTo(rahul);  // no skill
    }

    @Test
    @DisplayName("Seniority fit: seniors for P1/P2, juniors for P3/P4")
    void seniorityFit() {
        assertThat(SmartAssignmentStrategy.seniorityFit(Priority.P1, 4)).isGreaterThan(
                SmartAssignmentStrategy.seniorityFit(Priority.P1, 1));
        assertThat(SmartAssignmentStrategy.seniorityFit(Priority.P4, 1)).isGreaterThan(
                SmartAssignmentStrategy.seniorityFit(Priority.P4, 4));
    }
}
