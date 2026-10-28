package com.resolveiq.audit;

import com.resolveiq.user.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Writes non-incident audit entries (admin and security actions) and searches the trail. */
@Service
public class AuditService {

    private final AuditRepository repository;
    private final Clock clock;

    public AuditService(AuditRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public void recordAdminAction(User actor, AuditAction action, String before, String after, String description) {
        repository.save(AuditRecord.builder(action, clock.instant())
                .actor(actor == null ? null : actor.getId(), actor == null ? "System" : actor.getFullName(),
                        actor == null ? null : actor.getRole().name())
                .states(before, after)
                .description(description)
                .build());
    }

    /** Security events are saved in their own transaction so a failed login is logged even though it throws. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSecurityEvent(User user, String email, AuditAction action, String description) {
        repository.save(AuditRecord.builder(action, clock.instant())
                .actor(user == null ? null : user.getId(), user == null ? email : user.getFullName(),
                        user == null ? null : user.getRole().name())
                .description(description)
                .build());
    }

    @Transactional(readOnly = true)
    public List<AuditRecord> forIncident(String incidentNumber) {
        return repository.findByIncidentNumberOrderByOccurredAtAscIdAsc(incidentNumber);
    }

    @Transactional(readOnly = true)
    public Page<AuditRecord> search(String incidentNumber, String actor, AuditAction action, Instant from, Instant to,
                                    Pageable pageable) {
        List<Specification<AuditRecord>> specs = new ArrayList<>();
        if (incidentNumber != null && !incidentNumber.isBlank()) {
            specs.add((r, q, cb) -> cb.equal(cb.upper(r.get("incidentNumber")), incidentNumber.trim().toUpperCase()));
        }
        if (actor != null && !actor.isBlank()) {
            specs.add((r, q, cb) -> cb.like(cb.lower(r.get("actorName")), "%" + actor.trim().toLowerCase() + "%"));
        }
        if (action != null) {
            specs.add((r, q, cb) -> cb.equal(r.get("action"), action));
        }
        if (from != null) {
            specs.add((r, q, cb) -> cb.greaterThanOrEqualTo(r.get("occurredAt"), from));
        }
        if (to != null) {
            specs.add((r, q, cb) -> cb.lessThan(r.get("occurredAt"), to));
        }
        return repository.findAll(Specification.allOf(specs), pageable);
    }
}
