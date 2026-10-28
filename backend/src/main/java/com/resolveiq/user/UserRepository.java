package com.resolveiq.user;

import com.resolveiq.user.domain.Administrator;
import com.resolveiq.user.domain.ItManager;
import com.resolveiq.user.domain.TeamLead;
import com.resolveiq.user.domain.Technician;
import com.resolveiq.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

/** <b>Repository pattern</b>: the domain asks for users by intent; Spring Data writes the SQL. */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    @Query("select t from Technician t where t.active = true order by t.fullName")
    List<Technician> findActiveTechnicians();

    @Query("select t from Technician t order by t.fullName")
    List<Technician> findAllTechnicians();

    @Query("select l from TeamLead l where l.active = true order by l.fullName")
    List<TeamLead> findActiveTeamLeads();

    /** System administrators only (excludes IT managers, who are a subclass). */
    @Query("select a from Administrator a where a.active = true and type(a) = Administrator order by a.fullName")
    List<Administrator> findActiveSystemAdministrators();

    @Query("select m from ItManager m where m.active = true order by m.fullName")
    List<ItManager> findActiveManagers();

    /** Admins and managers, i.e. everyone who monitors the whole service desk. */
    @Query("select a from Administrator a where a.active = true order by a.fullName")
    List<Administrator> findActiveAdministrators();

    @Query("select t from Technician t where t.id = :id")
    Optional<Technician> findTechnicianById(Long id);
}
