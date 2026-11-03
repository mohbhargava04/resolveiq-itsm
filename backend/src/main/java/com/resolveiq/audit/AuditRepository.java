package com.resolveiq.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface AuditRepository extends JpaRepository<AuditRecord, Long>, JpaSpecificationExecutor<AuditRecord> {

    List<AuditRecord> findByIncidentNumberOrderByOccurredAtAscIdAsc(String incidentNumber);

    List<AuditRecord> findTop15ByIncidentNumberIsNotNullOrderByOccurredAtDescIdDesc();

    long countByActionAndOccurredAtAfter(AuditAction action, java.time.Instant after);
}
