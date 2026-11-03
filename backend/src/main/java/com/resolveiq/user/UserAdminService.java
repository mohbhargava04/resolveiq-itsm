package com.resolveiq.user;

import com.resolveiq.audit.AuditAction;
import com.resolveiq.audit.AuditService;
import com.resolveiq.common.exception.BusinessRuleViolationException;
import com.resolveiq.common.exception.DuplicateResourceException;
import com.resolveiq.common.exception.ResourceNotFoundException;
import com.resolveiq.common.exception.UnauthorizedOperationException;
import com.resolveiq.security.EmailDomainPolicy;
import com.resolveiq.security.crypto.Tokens;
import com.resolveiq.security.session.SessionService;
import com.resolveiq.user.domain.ExperienceLevel;
import com.resolveiq.user.domain.Role;
import com.resolveiq.user.domain.TeamLead;
import com.resolveiq.user.domain.Technician;
import com.resolveiq.user.domain.User;
import jakarta.persistence.EntityManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * User and technician administration: accounts, roles (clearance), and account security actions.
 *
 * <p>Changing someone's role, deactivating them, resetting their password or signing them out
 * everywhere ends their existing sessions immediately (see {@code SessionService#revokeAll}).</p>
 */
@Service
@Transactional
public class UserAdminService {

    /** One row of a bulk import, with what happened to it. */
    public record ImportRow(int line, String email, String outcome, String detail) {
    }

    private static final java.util.Set<Role> TECHNICAL = java.util.EnumSet.of(Role.TECHNICIAN, Role.TEAM_LEAD);

    private final UserRepository users;
    private final UserFactory factory;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final SessionService sessions;
    private final EmailDomainPolicy domains;
    private final EntityManager em;

    public UserAdminService(UserRepository users, UserFactory factory, PasswordEncoder encoder, AuditService audit,
                            SessionService sessions, EmailDomainPolicy domains, EntityManager em) {
        this.users = users;
        this.factory = factory;
        this.encoder = encoder;
        this.audit = audit;
        this.sessions = sessions;
        this.domains = domains;
        this.em = em;
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
        boolean companyAccountOnly = spec.rawPassword() == null || spec.rawPassword().isBlank();
        User user = factory.create(companyAccountOnly ? withPassword(spec, "Gx9" + Tokens.random(24)) : spec);
        if (companyAccountOnly) {
            user.disablePasswordLogin(encoder.encode(Tokens.random(32)));
        }
        if (user instanceof Technician t && teamLeadId != null) {
            t.reportTo(requireTeamLead(teamLeadId));
        }
        users.save(user);
        audit.recordAdminAction(actor, AuditAction.USER_CREATED, null, user.getRole().name(),
                "Created " + user.getRole().displayName() + " account for " + user.getFullName()
                        + (companyAccountOnly ? " (signs in with company account)" : ""));
        return user;
    }

    private static UserFactory.NewUser withPassword(UserFactory.NewUser s, String password) {
        return new UserFactory.NewUser(s.role(), s.fullName(), s.email(), password, s.phone(), s.department(),
                s.skills(), s.experienceLevel(), s.maxActiveIncidents());
    }

    /**
     * Changes a person's clearance. Implemented as a change of the single-table discriminator, because
     * the role <i>is</i> the object's class (Employee, Technician, TeamLead ...).
     *
     * <ul>
     *   <li>Nobody can change their own role, and only an IT manager can grant or remove the manager role.</li>
     *   <li>Becoming a technician or team lead needs at least one skill.</li>
     *   <li>A technician who has ever been assigned incidents cannot become a non-technical role, because
     *       those incidents reference them as technician; deactivate the account instead.</li>
     * </ul>
     */
    public User changeRole(Long id, Role newRole, Set<String> skills, ExperienceLevel level, Integer capacity, User actor) {
        User user = require(id);
        Role old = user.getRole();
        if (old == newRole) {
            return user;
        }
        if (user.getId().equals(actor.getId())) {
            throw new BusinessRuleViolationException("You cannot change your own role");
        }
        if ((old == Role.MANAGER || newRole == Role.MANAGER) && actor.getRole() != Role.MANAGER) {
            throw new UnauthorizedOperationException("Only an IT manager can grant or remove the IT manager role");
        }
        boolean wasTechnical = TECHNICAL.contains(old);
        boolean willBeTechnical = TECHNICAL.contains(newRole);
        if (wasTechnical && !willBeTechnical) {
            Number assigned = (Number) em.createNativeQuery("select count(*) from incidents where assignee_id = :id")
                    .setParameter("id", id).getSingleResult();
            if (assigned.longValue() > 0) {
                throw new BusinessRuleViolationException(user.getFullName() + " has worked on " + assigned
                        + " incident(s) as a technician, so the role cannot be removed. Deactivate the account instead.");
            }
        }
        if (!wasTechnical && willBeTechnical && (skills == null || skills.isEmpty())) {
            throw new BusinessRuleViolationException("Give the new technician at least one skill");
        }
        em.flush();
        if (old == Role.TEAM_LEAD && newRole != Role.TEAM_LEAD) {
            em.createNativeQuery("update users set team_lead_id = null where team_lead_id = :id")
                    .setParameter("id", id).executeUpdate();
        }
        if (willBeTechnical && !wasTechnical) {
            em.createNativeQuery("update users set user_type = :type, max_active_incidents = :cap, available = true, "
                            + "experience_level = :level where id = :id")
                    .setParameter("type", newRole.name())
                    .setParameter("cap", capacity == null ? Technician.DEFAULT_CAPACITY : capacity)
                    .setParameter("level", (level == null ? ExperienceLevel.JUNIOR : level).name())
                    .setParameter("id", id).executeUpdate();
        } else if (!willBeTechnical && wasTechnical) {
            em.createNativeQuery("update users set user_type = :type, max_active_incidents = null, available = null, "
                            + "experience_level = null, last_assigned_at = null, team_lead_id = null where id = :id")
                    .setParameter("type", newRole.name()).setParameter("id", id).executeUpdate();
            em.createNativeQuery("delete from technician_skills where technician_id = :id").setParameter("id", id).executeUpdate();
        } else {
            em.createNativeQuery("update users set user_type = :type where id = :id")
                    .setParameter("type", newRole.name()).setParameter("id", id).executeUpdate();
        }
        em.clear();
        User reloaded = require(id);
        if (reloaded instanceof Technician t && skills != null && !skills.isEmpty()) {
            t.replaceSkills(skills.stream().map(s -> s.trim().toUpperCase()).filter(s -> !s.isEmpty())
                    .collect(java.util.stream.Collectors.toSet()));
        }
        sessions.revokeAll(reloaded, "ROLE_CHANGED");
        User actorRef = require(actor.getId());
        audit.recordAdminAction(actorRef, AuditAction.ROLE_CHANGED, old.name(), newRole.name(),
                reloaded.getFullName() + " is now " + newRole.displayName() + " (was " + old.displayName() + ")");
        return require(id);
    }

    public void unlock(Long id, User actor) {
        User user = require(id);
        user.unlock();
        audit.recordAdminAction(actor, AuditAction.ACCOUNT_UNLOCKED, null, null, "Unlocked " + user.getFullName());
    }

    public void signOutEverywhere(Long id, User actor) {
        User user = require(id);
        sessions.revokeAll(user, "ADMIN_REVOKED");
        audit.recordAdminAction(actor, AuditAction.SESSIONS_REVOKED, null, null,
                "Signed " + user.getFullName() + " out of every device");
    }

    /**
     * Pre-registers people from CSV ({@code email,name,role,department,skills}; skills separated by
     * {@code ;}). Imported people sign in with their company account and land in the workspace of the
     * role given here.
     */
    public List<ImportRow> importCsv(String csv, User actor) {
        List<ImportRow> rows = new java.util.ArrayList<>();
        String[] lines = csv.replace("\r", "").split("\n");
        int created = 0;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.startsWith("#") || (i == 0 && line.toLowerCase().startsWith("email"))) {
                continue;
            }
            String[] f = line.split(",", -1);
            String email = f.length > 0 ? f[0].trim() : "";
            try {
                if (f.length < 3) {
                    throw new BusinessRuleViolationException("Expected at least email,name,role");
                }
                Role role = Role.valueOf(f[2].trim().toUpperCase().replace(' ', '_'));
                if (role == Role.MANAGER && actor.getRole() != Role.MANAGER) {
                    throw new UnauthorizedOperationException("Only an IT manager can create IT managers");
                }
                domains.require(email);
                if (users.existsByEmailIgnoreCase(email)) {
                    rows.add(new ImportRow(i + 1, email, "SKIPPED", "Already has an account"));
                    continue;
                }
                Set<String> skills = f.length > 4 && !f[4].isBlank()
                        ? java.util.Arrays.stream(f[4].split(";")).map(s -> s.trim().toUpperCase()).filter(s -> !s.isEmpty())
                        .collect(java.util.stream.Collectors.toSet()) : Set.of();
                User u = factory.create(new UserFactory.NewUser(role, f[1].trim(), email, "Gx9" + Tokens.random(24), null,
                        f.length > 3 ? f[3].trim() : null, skills, null, null));
                u.disablePasswordLogin(encoder.encode(Tokens.random(32)));
                users.save(u);
                created++;
                rows.add(new ImportRow(i + 1, email, "CREATED", role.displayName()));
            } catch (IllegalArgumentException ex) {
                rows.add(new ImportRow(i + 1, email, "ERROR", ex.getMessage() != null && ex.getMessage().contains("No enum")
                        ? "Unknown role '" + (f.length > 2 ? f[2].trim() : "") + "'" : ex.getMessage()));
            } catch (com.resolveiq.common.exception.ItsmException ex) {
                rows.add(new ImportRow(i + 1, email, "ERROR", ex.getMessage()));
            }
        }
        audit.recordAdminAction(actor, AuditAction.USERS_IMPORTED, null, String.valueOf(created),
                "Imported " + created + " user(s) from CSV");
        return rows;
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
            sessions.revokeAll(user, "DEACTIVATED");
        }
        audit.recordAdminAction(actor, active ? AuditAction.USER_ACTIVATED : AuditAction.USER_DEACTIVATED,
                null, null, (active ? "Activated " : "Deactivated ") + user.getFullName());
    }

    public void resetPassword(Long id, String newPassword, User actor) {
        User user = require(id);
        PasswordPolicy.validate(newPassword, user.getEmail());
        user.changePasswordHash(encoder.encode(newPassword));
        sessions.revokeAll(user, "PASSWORD_RESET_BY_ADMIN");
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
