package com.resolveiq.incident;

import com.resolveiq.catalog.ItService;
import com.resolveiq.incident.domain.Impact;
import com.resolveiq.incident.domain.Incident;
import com.resolveiq.incident.domain.Urgency;
import com.resolveiq.user.domain.User;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * <b>Factory pattern</b>: owns the knowledge of how a new incident is put together - drawing a unique
 * number from the database sequence and applying defaults (e.g. affected users) - so controllers,
 * the seeder and tests all create incidents the same way.
 */
@Component
public class IncidentFactory {

    public static final String PREFIX = "INC-";

    private final IncidentRepository repository;

    public IncidentFactory(IncidentRepository repository) {
        this.repository = repository;
    }

    public Incident create(String title, String description, User reporter, ItService service, Impact impact,
                           Urgency urgency, Integer affectedUsers, Instant reportedAt) {
        String number = PREFIX + repository.nextIncidentNumber();
        int users = affectedUsers == null || affectedUsers < 1 ? 1 : affectedUsers;
        return new Incident(number, title, description, reporter, service,
                impact == null ? Impact.LOW : impact, urgency == null ? Urgency.MEDIUM : urgency, users, reportedAt);
    }
}
