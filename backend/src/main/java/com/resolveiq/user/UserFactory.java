package com.resolveiq.user;

import com.resolveiq.common.exception.BusinessRuleViolationException;
import com.resolveiq.user.domain.Administrator;
import com.resolveiq.user.domain.Employee;
import com.resolveiq.user.domain.ExperienceLevel;
import com.resolveiq.user.domain.ItManager;
import com.resolveiq.user.domain.Role;
import com.resolveiq.user.domain.TeamLead;
import com.resolveiq.user.domain.Technician;
import com.resolveiq.user.domain.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * <b>Factory Method pattern</b>: callers ask for "a user with role X" and receive the correct concrete
 * subclass, with the password already hashed. Controllers, the registration flow and the data seeder
 * never need to know which constructor belongs to which role, and adding a new role only touches
 * this class.
 */
@Component
public class UserFactory {

    private final PasswordEncoder passwordEncoder;

    public UserFactory(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    /** Everything needed to create any kind of user. Technician-only fields are ignored for others. */
    public record NewUser(Role role, String fullName, String email, String rawPassword, String phone,
                          String department, Set<String> skills, ExperienceLevel experienceLevel,
                          Integer maxActiveIncidents) {

        public static NewUser of(Role role, String fullName, String email, String rawPassword, String department) {
            return new NewUser(role, fullName, email, rawPassword, null, department, Set.of(), null, null);
        }
    }

    public User create(NewUser spec) {
        PasswordPolicy.validate(spec.rawPassword());
        String hash = passwordEncoder.encode(spec.rawPassword());
        int capacity = spec.maxActiveIncidents() == null ? Technician.DEFAULT_CAPACITY : spec.maxActiveIncidents();
        return switch (spec.role()) {
            case EMPLOYEE -> new Employee(spec.fullName(), spec.email(), hash, spec.phone(), spec.department());
            case TECHNICIAN -> new Technician(spec.fullName(), spec.email(), hash, spec.phone(), spec.department(),
                    requireSkills(spec), spec.experienceLevel(), capacity);
            case TEAM_LEAD -> new TeamLead(spec.fullName(), spec.email(), hash, spec.phone(), spec.department(),
                    requireSkills(spec), spec.experienceLevel(), capacity);
            case ADMIN -> new Administrator(spec.fullName(), spec.email(), hash, spec.phone(), spec.department());
            case MANAGER -> new ItManager(spec.fullName(), spec.email(), hash, spec.phone(), spec.department());
        };
    }

    private static Set<String> requireSkills(NewUser spec) {
        if (spec.skills() == null || spec.skills().isEmpty()) {
            throw new BusinessRuleViolationException("Technicians must have at least one skill");
        }
        return spec.skills();
    }
}
