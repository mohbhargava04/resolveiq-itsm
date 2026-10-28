package com.resolveiq.user;

import com.resolveiq.audit.AuditAction;
import com.resolveiq.audit.AuditService;
import com.resolveiq.common.exception.BusinessRuleViolationException;
import com.resolveiq.common.exception.DuplicateResourceException;
import com.resolveiq.common.exception.ResourceNotFoundException;
import com.resolveiq.common.exception.UnauthorizedOperationException;
import com.resolveiq.user.domain.ExperienceLevel;
import com.resolveiq.user.domain.Role;
import com.resolveiq.user.domain.TeamLead;
import com.resolveiq.user.domain.Technician;
import com.resolveiq.user.domain.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/** User and technician administration. */
@Service
@Transactional
public class UserAdminService {

    private final UserRepository users;
    private final UserFactory factory;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    public UserAdminService(UserRepository users, UserFactory factory, PasswordEncoder encoder, AuditService audit) {
        this.users = users;
        this.factory = factory;
        this.encoder = encoder;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<User> listUsers() {
        return users.findAll(org.springframework.data.domain.Sort.by("fullName"));
    }

    @Transactional(readOnly = true)
    public List<Technician> listTechnicians() {
        return users.findAllTechnicians();
    }

    public User create(UserFactory.NewUser spec, Long teamLeadId, User actor) {
        if (spec.role() == Role.MANAGER && actor.getRole() != Role.MANAGER) {
            throw new UnauthorizedOperationException("Only an IT manager can create another IT manager");
        }
        if (users.existsByEmailIgnoreCase(spec.email())) {
            throw new DuplicateResourceException("An account with e-mail " + spec.email() + " already exists");
        }
        User user = factory.create(spec);
        if (user instanceof Technician t && teamLeadId != null) {
            t.reportTo(requireTeamLead(teamLeadId));
        }
        users.save(user);
        audit.recordAdminAction(actor, AuditAction.USER_CREATED, null, user.getRole().name(),
                "Created " + user.getRole().displayName() + " account for " + user.getFullName());
        return user;
    }

    public User update(Long id, String fullName, String phone, String department, Set<String> skills,
                       ExperienceLevel level, Integer capacity, Long teamLeadId, Boolean active, User actor) {
        User user = require(id);
        if (fullName != null && !fullName.isBlank()) {
            user.rename(fullName);
        }
        if (phone != null || department != null) {
            user.updateContactDetails(phone == null ? user.getPhone() : phone,
                    department == null ? user.getDepartment() : department);
        }
        if (user instanceof Technician t) {
            if (skills != null) {
                if (skills.isEmpty()) {
                    throw new BusinessRuleViolationException("Technicians must keep at least one skill");
                }
                t.replaceSkills(skills);
            }
            if (level != null) {
                t.changeExperienceLevel(level);
            }
            if (capacity != null) {
                t.changeCapacity(capacity);
            }
            if (teamLeadId != null) {
                t.reportTo(teamLeadId == 0 ? null : requireTeamLead(teamLeadId));
            }
        }
        if (active != null) {
            setActive(user, active, actor);
        }
        audit.recordAdminAction(actor, AuditAction.USER_UPDATED, null, null, "Updated " + user.getFullName());
        return user;
    }

    public void setActive(User user, boolean active, User actor) {
        if (!active && user.getId().equals(actor.getId())) {
            throw new BusinessRuleViolationException("You cannot deactivate your own account");
        }
        if (active) {
            user.activate();
        } else {
            user.deactivate();
        }
        audit.recordAdminAction(actor, active ? AuditAction.USER_ACTIVATED : AuditAction.USER_DEACTIVATED,
                null, null, (active ? "Activated " : "Deactivated ") + user.getFullName());
    }

    public void resetPassword(Long id, String newPassword, User actor) {
        User user = require(id);
        PasswordPolicy.validate(newPassword);
        user.changePasswordHash(encoder.encode(newPassword));
        audit.recordAdminAction(actor, AuditAction.PASSWORD_CHANGED, null, null,
                "Password reset for " + user.getFullName() + " by an administrator");
    }

    /** Technicians toggle themselves on/off duty. */
    public Technician setAvailability(User actor, boolean available) {
        Technician t = users.findTechnicianById(actor.getId())
                .orElseThrow(() -> new UnauthorizedOperationException("Only technicians have an availability status"));
        if (available) {
            t.goOnDuty();
        } else {
            t.goOffDuty();
        }
        return t;
    }

    public User require(Long id) {
        return users.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    private TeamLead requireTeamLead(Long id) {
        User u = require(id);
        if (!(u instanceof TeamLead lead)) {
            throw new BusinessRuleViolationException(u.getFullName() + " is not a team lead");
        }
        return lead;
    }
}
