package com.resolveiq.web;

import com.resolveiq.knowledge.ArticleAudience;
import com.resolveiq.knowledge.ArticleStatus;
import com.resolveiq.knowledge.KnowledgeArticle;
import com.resolveiq.knowledge.KnowledgeService;
import com.resolveiq.security.CurrentUserService;
import com.resolveiq.user.domain.User;
import com.resolveiq.web.dto.CommonDtos.Ref;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/kb")
@Tag(name = "Knowledge base")
@Transactional
public class KnowledgeController {

    public record ArticleSummary(Long id, String title, String excerpt, String status, String audience, Ref category,
                                 Ref service, int views, int helpfulVotes, int deflections, Double relevance,
                                 Instant updatedAt) {
    }

    public record ArticleView(Long id, String title, String symptoms, String solution, String status, String audience,
                              Ref category, Ref service, List<String> tags, String sourceIncident, String author,
                              int views, int helpfulVotes, int unhelpfulVotes, int deflections, Boolean myVote,
                              Instant publishedAt, Instant updatedAt, List<ArticleSummary> related) {
    }

    public record ArticleRequest(@NotBlank @Size(max = 160) String title, @NotBlank @Size(max = 2000) String symptoms,
                                 @NotBlank @Size(max = 6000) String solution, String categoryCode, String serviceCode,
                                 @Size(max = 300) String tags, ArticleAudience audience, String sourceIncident,
                                 boolean publish) {
    }

    public record VoteRequest(@NotNull Boolean helpful) {
    }

    public record SuggestRequest(@Size(max = 4400) String text, String serviceCode) {
    }

    private final KnowledgeService kb;
    private final CurrentUserService currentUser;

    public KnowledgeController(KnowledgeService kb, CurrentUserService currentUser) {
        this.kb = kb;
        this.currentUser = currentUser;
    }

    @GetMapping
    @Transactional(readOnly = true)
    @Operation(summary = "Search the knowledge base (TF-IDF relevance); employees see published self-help only")
    public List<ArticleSummary> search(@RequestParam(required = false) String q,
                                       @RequestParam(required = false) String category,
                                       @RequestParam(required = false) String service,
                                       @RequestParam(required = false) ArticleStatus status) {
        return kb.search(q, category, service, status, currentUser.require()).stream()
                .map(s -> summary(s.article(), q == null || q.isBlank() ? null : s.relevance())).toList();
    }

    @PostMapping("/suggest")
    @Transactional(readOnly = true)
    @Operation(summary = "Articles that may solve the problem being reported (shown beside the report form)")
    public List<ArticleSummary> suggest(@Valid @RequestBody SuggestRequest r) {
        currentUser.require();
        return kb.suggest(r.text(), r.serviceCode(), 3).stream().map(s -> summary(s.article(), s.relevance())).toList();
    }

    @GetMapping("/{id}")
    public ArticleView get(@PathVariable Long id) {
        User me = currentUser.require();
        KnowledgeArticle a = kb.view(id, me);
        List<ArticleSummary> related = kb.search(a.getTitle() + " " + a.getSymptoms(), null, null, ArticleStatus.PUBLISHED, me)
                .stream().filter(s -> !s.article().getId().equals(id)).limit(3)
                .map(s -> summary(s.article(), null)).toList();
        return view(a, kb.myVote(id, me).orElse(null), related);
    }

    @GetMapping("/draft-from-incident/{number}")
    @PreAuthorize("hasRole('TECHNICIAN')")
    @Transactional(readOnly = true)
    @Operation(summary = "Pre-filled article from a resolved incident")
    public KnowledgeService.Command draft(@PathVariable String number) {
        return kb.draftFromIncident(number, currentUser.require());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('TECHNICIAN')")
    public ArticleView create(@Valid @RequestBody ArticleRequest r) {
        KnowledgeArticle a = kb.create(command(r), r.sourceIncident(), r.publish(), currentUser.require());
        return view(a, null, List.of());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('TECHNICIAN')")
    public ArticleView update(@PathVariable Long id, @Valid @RequestBody ArticleRequest r) {
        KnowledgeArticle a = kb.update(id, command(r), currentUser.require());
        if (r.publish() && a.getStatus() != ArticleStatus.PUBLISHED) {
            a = kb.publish(id, currentUser.require());
        }
        return view(a, null, List.of());
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasRole('TECHNICIAN')")
    public ArticleView publish(@PathVariable Long id) {
        return view(kb.publish(id, currentUser.require()), null, List.of());
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasRole('TEAM_LEAD')")
    public ArticleView archive(@PathVariable Long id) {
        return view(kb.archive(id, currentUser.require()), null, List.of());
    }

    @PostMapping("/{id}/vote")
    public ArticleView vote(@PathVariable Long id, @Valid @RequestBody VoteRequest r) {
        User me = currentUser.require();
        return view(kb.vote(id, r.helpful(), me), r.helpful(), List.of());
    }

    @PostMapping("/{id}/solved-my-problem")
    @Operation(summary = "Record that an article solved the problem, so no incident was needed (deflection)")
    public ArticleSummary solved(@PathVariable Long id) {
        return summary(kb.recordDeflection(id, currentUser.require()), null);
    }

    private static KnowledgeService.Command command(ArticleRequest r) {
        return new KnowledgeService.Command(r.title(), r.symptoms(), r.solution(), r.categoryCode(), r.serviceCode(),
                r.tags(), r.audience());
    }

    static ArticleSummary summary(KnowledgeArticle a, Double relevance) {
        String s = a.getSymptoms();
        return new ArticleSummary(a.getId(), a.getTitle(), s.length() > 180 ? s.substring(0, 179) + "…" : s,
                a.getStatus().name(), a.getAudience().name(), IncidentMapper.ref(a.getCategory()),
                IncidentMapper.ref(a.getService()), a.getViews(), a.getHelpfulVotes(), a.getDeflections(),
                relevance == null ? null : Math.round(relevance * 1000) / 1000.0, a.getUpdatedAt());
    }

    static ArticleView view(KnowledgeArticle a, Boolean myVote, List<ArticleSummary> related) {
        List<String> tags = a.getTags() == null ? List.of()
                : java.util.Arrays.stream(a.getTags().split(",")).map(String::trim).filter(t -> !t.isEmpty()).toList();
        return new ArticleView(a.getId(), a.getTitle(), a.getSymptoms(), a.getSolution(), a.getStatus().name(),
                a.getAudience().name(), IncidentMapper.ref(a.getCategory()), IncidentMapper.ref(a.getService()), tags,
                a.getSourceIncident() == null ? null : a.getSourceIncident().getNumber(), a.getAuthorName(), a.getViews(),
                a.getHelpfulVotes(), a.getUnhelpfulVotes(), a.getDeflections(), myVote, a.getPublishedAt(),
                a.getUpdatedAt(), related);
    }
}
