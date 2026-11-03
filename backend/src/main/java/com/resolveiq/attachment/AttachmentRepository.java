package com.resolveiq.attachment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    @Query("select a from Attachment a where a.incident.id = :incidentId order by a.createdAt, a.id")
    List<Attachment> findByIncident(Long incidentId);

    @Query("select count(a) from Attachment a where a.incident.id = :incidentId")
    long countByIncident(Long incidentId);

    @Query("select a.incident.id, count(a) from Attachment a where a.incident.id in :ids group by a.incident.id")
    List<Object[]> countsFor(java.util.Collection<Long> ids);
}
