package com.resolveiq.incident;

import com.resolveiq.incident.domain.Incident;
import com.resolveiq.incident.domain.IncidentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface IncidentRepository extends JpaRepository<Incident, Long>, JpaSpecificationExecutor<Incident> {

    @EntityGraph(attributePaths = {"reporter", "assignee", "service", "category"})
    Optional<Incident> findByNumberIgnoreCase(String number);

    /** Dedicated database sequence so incident numbers are unique even under concurrent creation. */
    @Query(value = "select nextval('incident_number_seq')", nativeQuery = true)
    long nextIncidentNumber();

    @Override
    @EntityGraph(attributePaths = {"reporter", "assignee", "service", "category"})
    Page<Incident> findAll(Specification<Incident> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"reporter", "assignee", "service", "category"})
    List<Incident> findByStatusIn(Collection<IncidentStatus> statuses);

    @EntityGraph(attributePaths = {"reporter", "assignee", "service", "category"})
    @Query("select i from Incident i where i.status in :statuses and i.createdAt >= :since")
    List<Incident> findRecentInStatuses(Collection<IncidentStatus> statuses, Instant since);

    /** Knowledge base for suggested resolutions: everything resolved with a written resolution. */
    @EntityGraph(attributePaths = {"service", "category"})
    @Query("select i from Incident i where i.status in :statuses and i.resolution.summary is not null")
    List<Incident> findResolvedKnowledge(Collection<IncidentStatus> statuses);

    /** Training data for the category classifier. */
    @EntityGraph(attributePaths = {"category"})
    @Query("select i from Incident i where i.category is not null")
    List<Incident> findAllCategorised();

    long countByAssigneeIdAndStatusIn(Long assigneeId, Collection<IncidentStatus> statuses);

    /** [technicianId, activeCount] rows for every technician with active work. */
    @Query("select i.assignee.id, count(i) from Incident i where i.assignee is not null and i.status in :statuses "
            + "group by i.assignee.id")
    List<Object[]> countActiveByAssignee(Collection<IncidentStatus> statuses);

    /** [technicianId, resolvedCount] on one service - "previous experience with the affected service". */
    @Query("select i.assignee.id, count(i) from Incident i where i.assignee is not null and i.service.id = :serviceId "
            + "and i.status in :statuses group by i.assignee.id")
    List<Object[]> countResolvedByAssigneeForService(Long serviceId, Collection<IncidentStatus> statuses);

    /** [technicianId, resolvedCount] in one category. */
    @Query("select i.assignee.id, count(i) from Incident i where i.assignee is not null and i.category.id = :categoryId "
            + "and i.status in :statuses group by i.assignee.id")
    List<Object[]> countResolvedByAssigneeForCategory(Long categoryId, Collection<IncidentStatus> statuses);

    @EntityGraph(attributePaths = {"reporter", "assignee", "service", "category"})
    @Query("select i from Incident i where i.createdAt >= :from")
    List<Incident> findCreatedSince(Instant from);

    @EntityGraph(attributePaths = {"reporter", "assignee", "service", "category"})
    @Query("select i from Incident i where i.duplicateOf.id = :parentId")
    List<Incident> findDuplicatesOf(Long parentId);
}
