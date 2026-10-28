package com.resolveiq.sla;

import com.resolveiq.incident.domain.Priority;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static com.resolveiq.support.TestData.T0;
import static com.resolveiq.support.TestData.policy;
import static org.assertj.core.api.Assertions.assertThat;

class SlaRecordTest {

    private SlaRecord sla;

    @BeforeEach
    void start() {
        sla = new SlaRecord();
        sla.start(policy(Priority.P1, 5, 30), T0); // the brief's P1 targets, 75% warning
    }

    private Instant at(int minutes) {
        return T0.plus(Duration.ofMinutes(minutes));
    }

    @Test
    @DisplayName("TC-14 Deadlines are calculated from the applicable policy")
    void deadlines() {
        assertThat(sla.getResponseDueAt()).isEqualTo(at(5));
        assertThat(sla.getResolutionDueAt()).isEqualTo(at(30));
        assertThat(sla.status(at(1))).isEqualTo(SlaStatus.ON_TRACK);
    }

    @Test
    @DisplayName("TC-15 Boundary: exactly at the deadline is not a breach; one second later is")
    void boundary() {
        sla.markResponded(at(1));
        assertThat(sla.evaluate(at(30))).noneMatch(s -> s.kind() == SlaRecord.Signal.Kind.BREACH);
        assertThat(sla.evaluate(at(30).plusSeconds(1)))
                .anyMatch(s -> s.kind() == SlaRecord.Signal.Kind.BREACH && s.target() == SlaTarget.RESOLUTION);
    }

    @Test
    @DisplayName("TC-16 The 'approaching' warning fires once at 75% of the window")
    void warningOnce() {
        sla.markResponded(at(1));
        assertThat(sla.evaluate(at(20))).isEmpty();                 // 67%
        assertThat(sla.evaluate(at(23))).hasSize(1)                 // 77%
                .allMatch(s -> s.kind() == SlaRecord.Signal.Kind.WARNING);
        assertThat(sla.evaluate(at(24))).isEmpty();                 // not repeated
        assertThat(sla.status(at(24))).isEqualTo(SlaStatus.AT_RISK);
    }

    @Test
    @DisplayName("TC-17 Unacknowledged past the response target is a response breach (reported once)")
    void responseBreach() {
        assertThat(sla.evaluate(at(6))).anyMatch(s -> s.target() == SlaTarget.RESPONSE);
        assertThat(sla.evaluate(at(7))).noneMatch(s -> s.target() == SlaTarget.RESPONSE);
        assertThat(sla.isResponseBreached()).isTrue();
        assertThat(sla.status(at(7))).isEqualTo(SlaStatus.BREACHED);
    }

    @Test
    @DisplayName("Late acknowledgement is recorded as a response breach")
    void lateAck() {
        assertThat(sla.markResponded(at(9))).isTrue();
        assertThat(sla.responseTime()).isEqualTo(Duration.ofMinutes(9));
    }

    @Test
    @DisplayName("Paused time is excluded from elapsed time and pushes the deadline back")
    void pause() {
        sla.markResponded(at(1));
        sla.pause(at(10));
        assertThat(sla.evaluate(at(100))).isEmpty();          // paused: no breach
        sla.resume(at(70));                                    // paused for 60 minutes
        assertThat(sla.getResolutionDueAt()).isEqualTo(at(90));
        assertThat(sla.elapsedFraction(at(70))).isCloseTo(10 / 30.0, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    @DisplayName("Resolved within target counts as MET")
    void met() {
        sla.markResponded(at(2));
        assertThat(sla.markResolved(at(25))).isFalse();
        assertThat(sla.status(at(40))).isEqualTo(SlaStatus.MET);
        assertThat(sla.resolutionTime()).isEqualTo(Duration.ofMinutes(25));
    }
}
