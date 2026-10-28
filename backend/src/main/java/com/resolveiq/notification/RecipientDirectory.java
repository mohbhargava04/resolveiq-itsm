package com.resolveiq.notification;

import com.resolveiq.user.UserRepository;
import com.resolveiq.user.domain.Administrator;
import com.resolveiq.user.domain.ItManager;
import com.resolveiq.user.domain.TeamLead;
import com.resolveiq.user.domain.Technician;
import com.resolveiq.user.domain.User;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** Answers "who should hear about this?" for roles that are not stored on the incident itself. */
@Component
public class RecipientDirectory {

    private final UserRepository users;

    public RecipientDirectory(UserRepository users) {
        this.users = users;
    }

    /** The technician's own team lead, or any active team lead if they have none. */
    public Optional<TeamLead> teamLeadFor(Technician technician) {
        if (technician != null && technician.getTeamLead() != null && technician.getTeamLead().isActive()) {
            return Optional.of(technician.getTeamLead());
        }
        return users.findActiveTeamLeads().stream().findFirst();
    }

    public List<TeamLead> teamLeads() {
        return users.findActiveTeamLeads();
    }

    public List<Administrator> systemAdministrators() {
        return users.findActiveSystemAdministrators();
    }

    public List<ItManager> managers() {
        return users.findActiveManagers();
    }

    public List<Administrator> allAdministrators() {
        return users.findActiveAdministrators();
    }

    public List<User> supervisors() {
        List<User> result = new java.util.ArrayList<>(users.findActiveTeamLeads());
        result.addAll(users.findActiveSystemAdministrators());
        return result;
    }
}
