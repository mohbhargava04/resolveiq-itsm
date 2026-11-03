package com.resolveiq.knowledge;

import com.resolveiq.catalog.Category;
import com.resolveiq.catalog.ItService;
import com.resolveiq.common.BaseEntity;
import com.resolveiq.common.exception.BusinessRuleViolationException;
import com.resolveiq.common.exception.InvalidIncidentException;
import com.resolveiq.incident.domain.Incident;
import com.resolveiq.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A knowledge-base article: the symptoms a person would describe and the fix. Articles are written by
 * IT staff, often straight from a resolved incident, and are suggested to employees while they type a
 * new report - every report avoided this way is counted as a <i>deflection</i>.
 */
@Entity
@Table(name = "kb_articles")
public class KnowledgeArticle extends BaseEntity {

    public static final int MAX_TITLE = 160;
    public static final int MAX_SYMPTOMS = 2000;
    public static final int MAX_SOLUTION = 6000;

    @Column(nullable = false, length = MAX_TITLE)
    private String title;

    @Column(nullable = false, length = MAX_SYMPTOMS)
    private String symptoms;

    @Column(nullable = false, length = MAX_SOLUTION)
    private String solution;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id")
    private ItService service;

    @Column(length = 300)
    private String tags;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private ArticleStatus status = ArticleStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private ArticleAudience audience = ArticleAudience.EVERYONE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_incident_id")
    private Incident sourceIncident;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private User author;

    @Column(name = "author_name", nullable = false, length = 120)
    private String authorName;

    @Column(nullable = false)
    private int views;

    @Column(name = "helpful_votes", nullable = false)
    private int helpfulVotes;

    @Column(name = "unhelpful_votes", nullable = false)
    private int unhelpfulVotes;

    @Column(nullable = false)
    private int deflections;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected KnowledgeArticle() {
    }

    public KnowledgeArticle(String title, String symptoms, String solution, Category category, ItService service,
                            String tags, ArticleAudience audience, Incident sourceIncident, User author) {
        edit(title, symptoms, solution, category, service, tags, audience);
        this.sourceIncident = sourceIncident;
        this.author = author;
        this.authorName = author == null ? "ResolveIQ" : author.getFullName();
    }

    public void edit(String title, String symptoms, String solution, Category category, ItService service,
                     String tags, ArticleAudience audience) {
        this.title = require(title, "A title is required", MAX_TITLE);
        this.symptoms = require(symptoms, "Describe the symptoms people see", MAX_SYMPTOMS);
        this.solution = require(solution, "The solution is required", MAX_SOLUTION);
        this.category = category;
        this.service = service;
        this.tags = tags == null || tags.isBlank() ? null : tags.trim().toLowerCase();
        if (this.tags != null && this.tags.length() > 300) {
            throw new InvalidIncidentException("Tags must be at most 300 characters");
        }
        this.audience = audience == null ? ArticleAudience.EVERYONE : audience;
    }

    public void publish(Instant at) {
        if (status == ArticleStatus.PUBLISHED) {
            throw new BusinessRuleViolationException("Already published");
        }
        status = ArticleStatus.PUBLISHED;
        if (publishedAt == null) {
            publishedAt = at;
        }
    }

    public void archive() {
        status = ArticleStatus.ARCHIVED;
    }

    /** Carries over usage figures when articles are imported from another system (and for demo data). */
    public void importStatistics(int views, int helpful, int unhelpful, int deflections) {
        this.views = Math.max(0, views);
        this.helpfulVotes = Math.max(0, helpful);
        this.unhelpfulVotes = Math.max(0, unhelpful);
        this.deflections = Math.max(0, deflections);
    }

    public void recordView() {
        views++;
    }

    public void recordDeflection() {
        deflections++;
    }

    /** Applies a vote, replacing the same person's earlier vote if there was one. */
    public void applyVote(Boolean previous, boolean helpful) {
        if (previous != null) {
            if (previous) {
                helpfulVotes--;
            } else {
                unhelpfulVotes--;
            }
        }
        if (helpful) {
            helpfulVotes++;
        } else {
            unhelpfulVotes++;
        }
    }

    public boolean isVisibleTo(boolean staff) {
        return staff || (status == ArticleStatus.PUBLISHED && audience == ArticleAudience.EVERYONE);
    }

    /** Text used for similarity search. Title words count double. */
    public String searchText() {
        return title + ". " + title + ". " + symptoms + " " + (tags == null ? "" : tags.replace(',', ' '));
    }

    private static String require(String v, String message, int max) {
        if (v == null || v.isBlank()) {
            throw new InvalidIncidentException(message);
        }
        String t = v.trim();
        if (t.length() > max) {
            throw new InvalidIncidentException("Must be at most " + max + " characters");
        }
        return t;
    }

    public String getTitle() {
        return title;
    }

    public String getSymptoms() {
        return symptoms;
    }

    public String getSolution() {
        return solution;
    }

    public Category getCategory() {
        return category;
    }

    public ItService getService() {
        return service;
    }

    public String getTags() {
        return tags;
    }

    public ArticleStatus getStatus() {
        return status;
    }

    public ArticleAudience getAudience() {
        return audience;
    }

    public Incident getSourceIncident() {
        return sourceIncident;
    }

    public User getAuthor() {
        return author;
    }

    public String getAuthorName() {
        return authorName;
    }

    public int getViews() {
        return views;
    }

    public int getHelpfulVotes() {
        return helpfulVotes;
    }

    public int getUnhelpfulVotes() {
        return unhelpfulVotes;
    }

    public int getDeflections() {
        return deflections;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }
}
