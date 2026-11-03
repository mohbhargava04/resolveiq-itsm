package com.resolveiq.knowledge;

import com.resolveiq.audit.AuditAction;
import com.resolveiq.audit.AuditService;
import com.resolveiq.catalog.Category;
import com.resolveiq.catalog.CategoryRepository;
import com.resolveiq.catalog.ItService;
import com.resolveiq.catalog.ItServiceRepository;
import com.resolveiq.common.exception.BusinessRuleViolationException;
import com.resolveiq.common.exception.IncidentNotFoundException;
import com.resolveiq.common.exception.InvalidIncidentException;
import com.resolveiq.common.exception.ResourceNotFoundException;
import com.resolveiq.common.exception.UnauthorizedOperationException;
import com.resolveiq.incident.IncidentAccessPolicy;
import com.resolveiq.incident.IncidentRepository;
import com.resolveiq.incident.domain.Incident;
import com.resolveiq.intelligence.text.SimilarityEngine;
import com.resolveiq.user.domain.Role;
import com.resolveiq.user.domain.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The knowledge base: search, self-help suggestions while reporting, authoring, and feedback.
 *
 * <p>Search and suggestions reuse the TF-IDF {@link SimilarityEngine} that powers duplicate detection,
 * so "VPN keeps disconnecting" finds an article titled "VPN drops every few minutes".</p>
 */
@Service
@Transactional
public class KnowledgeService {

    /** Minimum similarity for an article to be offered while someone is typing a report. */
    static final double SUGGEST_THRESHOLD = 0.12;
    static final double SEARCH_THRESHOLD = 0.04;

    public record Command(String title, String symptoms, String solution, String categoryCode, String serviceCode,
                          String tags, ArticleAudience audience) {
    }

    public record Scored(KnowledgeArticle article, double relevance) {
    }

    private final KnowledgeArticleRepository articles;
    private final ArticleVoteRepository votes;
    private final CategoryRepository categories;
    private final ItServiceRepository services;
    private final IncidentRepository incidents;
    private final IncidentAccessPolicy access;
    private final SimilarityEngine similarity;
    private final AuditService audit;
    private final Clock clock;

    public KnowledgeService(KnowledgeArticleRepository articles, ArticleVoteRepository votes,
                            CategoryRepository categories, ItServiceRepository services, IncidentRepository incidents,
                            IncidentAccessPolicy access, SimilarityEngine similarity, AuditService audit, Clock clock) {
        this.articles = articles;
        this.votes = votes;
        this.categories = categories;
        this.services = services;
        this.incidents = incidents;
        this.access = access;
        this.similarity = similarity;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Scored> search(String query, String categoryCode, String serviceCode, ArticleStatus status, User viewer) {
        boolean staff = isStaff(viewer);
        List<KnowledgeArticle> pool = (staff ? articles.findAllCurrent()
                : articles.findByStatusOrderByUpdatedAtDesc(ArticleStatus.PUBLISHED)).stream()
                .filter(a -> a.isVisibleTo(staff))
                .filter(a -> status == null || a.getStatus() == status)
                .filter(a -> categoryCode == null || categoryCode.isBlank()
                        || (a.getCategory() != null && a.getCategory().getCode().equalsIgnoreCase(categoryCode)))
                .filter(a -> serviceCode == null || serviceCode.isBlank()
                        || (a.getService() != null && a.getService().getCode().equalsIgnoreCase(serviceCode)))
                .toList();
        if (query == null || query.isBlank()) {
            return pool.stream()
                    .sorted(Comparator.comparingInt((KnowledgeArticle a) -> a.getHelpfulVotes() + a.getDeflections() * 2)
                            .reversed().thenComparing(KnowledgeArticle::getUpdatedAt, Comparator.reverseOrder()))
                    .map(a -> new Scored(a, 0)).toList();
        }
        String q = query.trim().toLowerCase();
        return similarity.rank(query, pool, KnowledgeArticle::searchText).stream()
                .map(s -> new Scored(s.item(), s.similarity() + (s.item().getTitle().toLowerCase().contains(q) ? 0.3 : 0)))
                .filter(s -> s.relevance() >= SEARCH_THRESHOLD)
                .sorted(Comparator.comparingDouble(Scored::relevance).reversed())
                .toList();
    }

    /** Self-help while reporting: published, everyone-visible articles similar to what is being typed. */
    @Transactional(readOnly = true)
    public List<Scored> suggest(String text, String serviceCode, int limit) {
        if (text == null || text.trim().length() < 8) {
            return List.of();
        }
        List<KnowledgeArticle> pool = articles.findByStatusOrderByUpdatedAtDesc(ArticleStatus.PUBLISHED).stream()
                .filter(a -> a.getAudience() == ArticleAudience.EVERYONE).toList();
        return similarity.rank(text, pool, KnowledgeArticle::searchText).stream()
                .map(s -> {
                    boolean sameService = serviceCode != null && s.item().getService() != null
                            && s.item().getService().getCode().equalsIgnoreCase(serviceCode);
                    return new Scored(s.item(), Math.round((0.85 * s.similarity() + (sameService ? 0.15 : 0)) * 1000) / 1000.0);
                })
                .filter(s -> s.relevance() >= SUGGEST_THRESHOLD)
                .sorted(Comparator.comparingDouble(Scored::relevance).reversed())
                .limit(limit)
                .toList();
    }

    public KnowledgeArticle view(Long id, User viewer) {
        KnowledgeArticle a = require(id);
        if (!a.isVisibleTo(isStaff(viewer))) {
            throw new ResourceNotFoundException("Article", id);
        }
        if (a.getStatus() == ArticleStatus.PUBLISHED) {
            a.recordView();
        }
        return a;
    }

    @Transactional(readOnly = true)
    public Optional<Boolean> myVote(Long articleId, User viewer) {
        return votes.findById(new ArticleVote.Key(articleId, viewer.getId())).map(ArticleVote::isHelpful);
    }

    public KnowledgeArticle create(Command c, String sourceIncidentNumber, boolean publish, User author) {
        requireStaff(author);
        Incident source = null;
        if (sourceIncidentNumber != null && !sourceIncidentNumber.isBlank()) {
            source = incidents.findByNumberIgnoreCase(sourceIncidentNumber.trim())
                    .orElseThrow(() -> new IncidentNotFoundException(sourceIncidentNumber));
            access.requireView(author, source);
        }
        KnowledgeArticle a = new KnowledgeArticle(c.title(), c.symptoms(), c.solution(), category(c.categoryCode()),
                service(c.serviceCode()), c.tags(), c.audience(), source, author);
        if (publish) {
            a.publish(clock.instant());
        }
        KnowledgeArticle saved = articles.save(a);
        audit.recordAdminAction(author, AuditAction.KB_ARTICLE_CHANGED, null, saved.getStatus().name(),
                "Knowledge article \"" + saved.getTitle() + "\" created"
                        + (source == null ? "" : " from " + source.getNumber()));
        return saved;
    }

    public KnowledgeArticle update(Long id, Command c, User editor) {
        requireStaff(editor);
        KnowledgeArticle a = require(id);
        a.edit(c.title(), c.symptoms(), c.solution(), category(c.categoryCode()), service(c.serviceCode()), c.tags(),
                c.audience());
        audit.recordAdminAction(editor, AuditAction.KB_ARTICLE_CHANGED, null, a.getStatus().name(),
                "Knowledge article \"" + a.getTitle() + "\" edited");
        return a;
    }

    public KnowledgeArticle publish(Long id, User editor) {
        requireStaff(editor);
        KnowledgeArticle a = require(id);
        String before = a.getStatus().name();
        a.publish(clock.instant());
        audit.recordAdminAction(editor, AuditAction.KB_ARTICLE_CHANGED, before, a.getStatus().name(),
                "Knowledge article \"" + a.getTitle() + "\" published");
        return a;
    }

    public KnowledgeArticle archive(Long id, User editor) {
        if (editor.getRole() == Role.EMPLOYEE || editor.getRole() == Role.TECHNICIAN) {
            throw new UnauthorizedOperationException("Only team leads and administrators can archive articles");
        }
        KnowledgeArticle a = require(id);
        String before = a.getStatus().name();
        a.archive();
        audit.recordAdminAction(editor, AuditAction.KB_ARTICLE_CHANGED, before, a.getStatus().name(),
                "Knowledge article \"" + a.getTitle() + "\" archived");
        return a;
    }

    public KnowledgeArticle vote(Long id, boolean helpful, User voter) {
        KnowledgeArticle a = require(id);
        if (!a.isVisibleTo(isStaff(voter)) || a.getStatus() != ArticleStatus.PUBLISHED) {
            throw new ResourceNotFoundException("Article", id);
        }
        ArticleVote.Key key = new ArticleVote.Key(id, voter.getId());
        Optional<ArticleVote> existing = votes.findById(key);
        Boolean previous = existing.map(ArticleVote::isHelpful).orElse(null);
        if (previous != null && previous == helpful) {
            return a;
        }
        a.applyVote(previous, helpful);
        existing.ifPresentOrElse(v -> v.change(helpful, clock.instant()),
                () -> votes.save(new ArticleVote(id, voter.getId(), helpful, clock.instant())));
        return a;
    }

    /** "This fixed it - I don't need to report an incident." */
    public KnowledgeArticle recordDeflection(Long id, User viewer) {
        KnowledgeArticle a = require(id);
        if (a.getStatus() != ArticleStatus.PUBLISHED) {
            throw new BusinessRuleViolationException("Only published articles can resolve a problem");
        }
        a.recordDeflection();
        return a;
    }

    /** Pre-filled draft from a resolved incident: its description becomes the symptoms, its fix the solution. */
    @Transactional(readOnly = true)
    public Command draftFromIncident(String number, User author) {
        requireStaff(author);
        Incident i = incidents.findByNumberIgnoreCase(number).orElseThrow(() -> new IncidentNotFoundException(number));
        access.requireView(author, i);
        if (i.getResolution() == null) {
            throw new BusinessRuleViolationException(number + " has not been resolved yet");
        }
        String solution = i.getResolution().getSummary()
                + (i.getResolution().getRootCause() == null ? "" : "\n\nRoot cause: " + i.getResolution().getRootCause());
        return new Command(i.getTitle(), i.getDescription(), solution,
                i.getCategory() == null ? null : i.getCategory().getCode(), i.getService().getCode(), null,
                ArticleAudience.EVERYONE);
    }

    @Transactional(readOnly = true)
    public Optional<KnowledgeArticle> articleFromIncident(Long incidentId) {
        return articles.findFirstBySourceIncidentId(incidentId);
    }

    private KnowledgeArticle require(Long id) {
        return articles.findById(id).orElseThrow(() -> new ResourceNotFoundException("Article", id));
    }

    private Category category(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return categories.findByCodeIgnoreCase(code).orElseThrow(() -> new InvalidIncidentException("Unknown category " + code));
    }

    private ItService service(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return services.findByCodeIgnoreCase(code).orElseThrow(() -> new InvalidIncidentException("Unknown service " + code));
    }

    private static boolean isStaff(User u) {
        return u.getRole() != Role.EMPLOYEE;
    }

    private static void requireStaff(User u) {
        if (!isStaff(u)) {
            throw new UnauthorizedOperationException("Only IT staff can write knowledge articles");
        }
    }
}
