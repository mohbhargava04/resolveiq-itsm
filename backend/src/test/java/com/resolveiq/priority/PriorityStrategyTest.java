package com.resolveiq.priority;

import com.resolveiq.catalog.Criticality;
import com.resolveiq.incident.domain.Impact;
import com.resolveiq.incident.domain.Priority;
import com.resolveiq.incident.domain.Urgency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class PriorityStrategyTest {

    private final WeightedScoringPriorityStrategy weighted = new WeightedScoringPriorityStrategy();
    private final ItilMatrixPriorityStrategy matrix = new ItilMatrixPriorityStrategy();

    private static PriorityContext ctx(Impact i, Urgency u, Criticality c, int users, boolean security) {
        return new PriorityContext(i, u, c, users, security, "svc", "CAT");
    }

    @Test
    @DisplayName("TC-18 The brief's scenario - payment application unavailable - is P1")
    void paymentOutageIsP1() {
        PriorityAssessment a = weighted.assess(ctx(Impact.CRITICAL, Urgency.CRITICAL, Criticality.CRITICAL, 500, false));
        assertThat(a.priority()).isEqualTo(Priority.P1);
        assertThat(a.score()).isEqualTo(96.0);
        assertThat(a.factors()).anyMatch(f -> f.contains("Score 96.0"));
    }

    @ParameterizedTest(name = "{0}×{1}×{2} with {3} users = score {4} → {5}")
    @DisplayName("TC-19 Threshold boundaries 48 / 24 / 10")
    @CsvSource({
            "CRITICAL, CRITICAL, HIGH,   1, 48.0, P1",
            "HIGH,     CRITICAL, HIGH,   1, 36.0, P2",
            "HIGH,     CRITICAL, MEDIUM, 1, 24.0, P2",
            "MEDIUM,   MEDIUM,   MEDIUM, 11, 10.0, P3",
            "MEDIUM,   MEDIUM,   MEDIUM, 10, 8.0, P4",
            "LOW,      LOW,      LOW,    1, 1.0, P4"
    })
    void thresholds(Impact i, Urgency u, Criticality c, int users, double score, Priority expected) {
        PriorityAssessment a = weighted.assess(ctx(i, u, c, users, false));
        assertThat(a.score()).isEqualTo(score);
        assertThat(a.priority()).isEqualTo(expected);
    }

    @ParameterizedTest
    @DisplayName("Affected-user multiplier boundaries")
    @CsvSource({"1,1.0", "10,1.0", "11,1.25", "100,1.25", "101,1.5", "1000,1.5", "1001,2.0"})
    void userMultiplier(int users, double multiplier) {
        assertThat(WeightedScoringPriorityStrategy.userMultiplier(users)).isEqualTo(multiplier);
    }

    @Test
    @DisplayName("TC-20 Business rule: security incidents are never below P2")
    void securityFloor() {
        PriorityAssessment a = weighted.assess(ctx(Impact.LOW, Urgency.LOW, Criticality.LOW, 1, true));
        assertThat(a.priority()).isEqualTo(Priority.P2);
        assertThat(matrix.assess(ctx(Impact.LOW, Urgency.LOW, Criticality.LOW, 1, true)).priority())
                .isEqualTo(Priority.P2);
    }

    @Test
    @DisplayName("ITIL matrix is adjusted one level by service criticality")
    void matrixAdjustment() {
        assertThat(matrix.assess(ctx(Impact.HIGH, Urgency.HIGH, Criticality.MEDIUM, 1, false)).priority())
                .isEqualTo(Priority.P2);
        assertThat(matrix.assess(ctx(Impact.HIGH, Urgency.HIGH, Criticality.CRITICAL, 1, false)).priority())
                .isEqualTo(Priority.P1);
        assertThat(matrix.assess(ctx(Impact.HIGH, Urgency.HIGH, Criticality.LOW, 1, false)).priority())
                .isEqualTo(Priority.P3);
    }

    @Test
    @DisplayName("Strategies are interchangeable behind one interface (polymorphism)")
    void interchangeable() {
        PriorityContext c = ctx(Impact.MEDIUM, Urgency.HIGH, Criticality.HIGH, 30, false);
        for (PriorityStrategy s : new PriorityStrategy[]{weighted, matrix}) {
            PriorityAssessment a = s.assess(c);
            assertThat(a.priority()).isNotNull();
            assertThat(a.strategy()).isEqualTo(s.displayName());
        }
    }
}
