package com.resolveiq.user;

import com.resolveiq.catalog.Criticality;
import com.resolveiq.catalog.ItService;
import com.resolveiq.common.exception.BusinessRuleViolationException;
import com.resolveiq.user.domain.Employee;
import com.resolveiq.user.domain.ExperienceLevel;
import com.resolveiq.user.domain.Role;
import com.resolveiq.user.domain.TeamLead;
import com.resolveiq.user.domain.Technician;
import com.resolveiq.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Duration;
import java.util.Set;

import static com.resolveiq.support.TestData.T0;
import static com.resolveiq.support.TestData.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserAndCatalogRulesTest {

    private final UserFactory factory = new UserFactory(new BCryptPasswordEncoder(4));

    @Test
    @DisplayName("TC-29 Factory creates the right subclass for each role and hashes the password")
    void factory() {
        User tl = factory.create(new UserFactory.NewUser(Role.TEAM_LEAD, "Neha", "neha@x.io", "Secret123", null, "IT",
                Set.of("network"), ExperienceLevel.EXPERT, 3));
        assertThat(tl).isInstanceOf(TeamLead.class).isInstanceOf(Technician.class);
        assertThat(tl.getRole()).isEqualTo(Role.TEAM_LEAD);
        assertThat(((Technician) tl).getSkills()).containsExactly("NETWORK");
        assertThat(tl.getPasswordHash()).isNotEqualTo("Secret123").startsWith("$2a$");
        assertThat(factory.create(UserFactory.NewUser.of(Role.EMPLOYEE, "Ava", "ava@x.io", "Secret123", "CS")))
                .isInstanceOf(Employee.class);
    }

    @Test
    @DisplayName("TC-30 Weak passwords and skill-less technicians are rejected")
    void validation() {
        assertThatThrownBy(() -> PasswordPolicy.validate("short1")).isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> PasswordPolicy.validate("allletters")).isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> factory.create(new UserFactory.NewUser(Role.TECHNICIAN, "T", "t@x.io", "Secret123",
                null, null, Set.of(), null, null))).isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    @DisplayName("TC-31 Account locks after 5 failed logins and unlocks after the lock-out period")
    void lockout() {
        Employee e = new Employee("Ava", "ava@x.io", "h", null, null);
        for (int i = 0; i < 4; i++) {
            assertThat(e.recordFailedLogin(T0, 5, Duration.ofMinutes(15))).isFalse();
        }
        assertThat(e.recordFailedLogin(T0, 5, Duration.ofMinutes(15))).isTrue();
        assertThat(e.isLocked(T0.plus(Duration.ofMinutes(14)))).isTrue();
        assertThat(e.isLocked(T0.plus(Duration.ofMinutes(16)))).isFalse();
    }

    @Test
    @DisplayName("TC-32 Service dependency graph rejects cycles")
    void noCycles() {
        ItService a = withId(new ItService("A", "A", null, Criticality.LOW, "t", 1));
        ItService b = withId(new ItService("B", "B", null, Criticality.LOW, "t", 1));
        ItService c = withId(new ItService("C", "C", null, Criticality.LOW, "t", 1));
        a.addDependency(b);
        b.addDependency(c);
        assertThat(a.dependsOn(c)).isTrue();
        assertThatThrownBy(() -> c.addDependency(a)).isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> a.addDependency(a)).isInstanceOf(BusinessRuleViolationException.class);
    }
}
