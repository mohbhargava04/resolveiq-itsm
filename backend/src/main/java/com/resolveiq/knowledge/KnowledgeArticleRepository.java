package com.resolveiq.knowledge;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface KnowledgeArticleRepository extends JpaRepository<KnowledgeArticle, Long> {

    List<KnowledgeArticle> findByStatusOrderByUpdatedAtDesc(ArticleStatus status);

    @Query("select a from KnowledgeArticle a where a.status <> com.resolveiq.knowledge.ArticleStatus.ARCHIVED order by a.updatedAt desc")
    List<KnowledgeArticle> findAllCurrent();

    Optional<KnowledgeArticle> findFirstBySourceIncidentId(Long incidentId);

    @Query("select coalesce(sum(a.deflections), 0) from KnowledgeArticle a")
    long totalDeflections();

    @Query("select coalesce(sum(a.views), 0) from KnowledgeArticle a")
    long totalViews();
}
