package com.resolveiq.sla;

import com.resolveiq.incident.domain.Priority;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SlaPolicyRepository extends JpaRepository<SlaPolicy, Long> {

    @Query("select p from SlaPolicy p where p.active = true and p.priority = :priority and p.service.id = :serviceId")
    Optional<SlaPolicy> findActiveForService(Long serviceId, Priority priority);

    @Query("select p from SlaPolicy p where p.active = true and p.priority = :priority and p.service is null")
    Optional<SlaPolicy> findActiveDefault(Priority priority);

    @Query("select p from SlaPolicy p left join fetch p.service s order by s.name nulls first, p.priority")
    List<SlaPolicy> findAllOrdered();

    @Query("select count(p) > 0 from SlaPolicy p where p.priority = :priority and "
            + "((:serviceId is null and p.service is null) or p.service.id = :serviceId)")
    boolean existsFor(Long serviceId, Priority priority);
}
