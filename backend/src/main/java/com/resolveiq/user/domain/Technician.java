package com.resolveiq.user.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * A support engineer who can be assigned incidents.
 *
 * <p>Skills are stored as category codes (e.g. NETWORK, DATABASE) so that adding a new incident
 * category never requires a code change here. A {@link TreeSet} keeps them sorted and unique.</p>
 *
 * <p>The technician's current workload is <em>not</em> stored as a counter (which drifts out of
 * sync); it is always derived from the incidents actually assigned to them.</p>
 */
@Entity
@DiscriminatorValue("TECHNICIAN")
public class Technician extends User {

    public static final int DEFAULT_CAPACITY = 5;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "technician_skills", joinColumns = @JoinColumn(name = "technician_id"))
    @Column(name = "skill", length = 40)
    private Set<String> skills = new TreeSet<>();

    @Column(name = "max_active_incidents")
    private Integer maxActiveIncidents = DEFAULT_CAPACITY;

    /** On duty / available to receive new work. */
    @Column(name = "available")
    private Boolean available = Boolean.TRUE;

    @Enumerated(EnumType.STRING)
    @Column(name = "experience_level", length = 20)
    private ExperienceLevel experienceLevel = ExperienceLevel.MID;

    /** Used by round-robin assignment: whoever waited longest gets the next incident. */
    @Column(name = "last_assigned_at")
    private Instant lastAssignedAt;

    @ManyToOne(fetch = FetchType.EAGER) // tiny, always shown alongside the technician
    @JoinColumn(name = "team_lead_id")
    private TeamLead teamLead;

    protected Technician() {
    }

    public Technician(String fullName, String email, String passwordHash, String phone, String department,
                      Collection<String> skills, ExperienceLevel level, int maxActiveIncidents) {
        super(fullName, email, passwordHash, phone, department);
        replaceSkills(skills);
        this.experienceLevel = level == null ? ExperienceLevel.MID : level;
        changeCapacity(maxActiveIncidents);
    }

    @Override
    public Role getRole() {
        return Role.TECHNICIAN;
    }

    @Override
    public boolean canHandleIncidents() {
        return true;
    }

    // ---- behaviour -------------------------------------------------------------------------

    public boolean hasSkill(String categoryCode) {
        return categoryCode != null && skills.contains(categoryCode.toUpperCase());
    }

    public void replaceSkills(Collection<String> newSkills) {
        skills.clear();
        if (newSkills != null) {
            newSkills.stream().filter(s -> s != null && !s.isBlank()).map(s -> s.trim().toUpperCase())
                    .forEach(skills::add);
        }
    }

    public void changeCapacity(int maxActiveIncidents) {
        if (maxActiveIncidents < 1 || maxActiveIncidents > 50) {
            throw new IllegalArgumentException("Capacity must be between 1 and 50");
        }
        this.maxActiveIncidents = maxActiveIncidents;
    }

    public void changeExperienceLevel(ExperienceLevel level) {
        this.experienceLevel = level;
    }

    public void goOnDuty() {
        this.available = true;
    }

    public void goOffDuty() {
        this.available = false;
    }

    public void reportTo(TeamLead lead) {
        if (lead == this) {
            throw new IllegalArgumentException("A team lead cannot report to themselves");
        }
        this.teamLead = lead;
    }

    public void recordAssignment(Instant at) {
        this.lastAssignedAt = at;
    }

    /** Eligible for new work right now (active account, on duty). Capacity is checked separately. */
    public boolean isAssignable() {
        return isActive() && isAvailable();
    }

    public boolean hasCapacityFor(int currentActiveIncidents) {
        return currentActiveIncidents < getMaxActiveIncidents();
    }

    // ---- accessors ---------------------------------------------------------------------------

    public Set<String> getSkills() {
        return Collections.unmodifiableSet(skills);
    }

    public int getMaxActiveIncidents() {
        return maxActiveIncidents == null ? DEFAULT_CAPACITY : maxActiveIncidents;
    }

    public boolean isAvailable() {
        return Boolean.TRUE.equals(available);
    }

    public ExperienceLevel getExperienceLevel() {
        return experienceLevel == null ? ExperienceLevel.MID : experienceLevel;
    }

    public Instant getLastAssignedAt() {
        return lastAssignedAt;
    }

    public TeamLead getTeamLead() {
        return teamLead;
    }
}
