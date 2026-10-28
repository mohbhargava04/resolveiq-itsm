package com.resolveiq.user.domain;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import java.util.Collection;

/**
 * A team lead <em>is a</em> technician (still solves incidents) with extra responsibility: they
 * supervise a team, can (re)assign work and are the first level of the escalation chain.
 */
@Entity
@DiscriminatorValue("TEAM_LEAD")
public class TeamLead extends Technician {

    protected TeamLead() {
    }

    public TeamLead(String fullName, String email, String passwordHash, String phone, String department,
                    Collection<String> skills, ExperienceLevel level, int maxActiveIncidents) {
        super(fullName, email, passwordHash, phone, department, skills, level, maxActiveIncidents);
    }

    @Override
    public Role getRole() {
        return Role.TEAM_LEAD;
    }
}
