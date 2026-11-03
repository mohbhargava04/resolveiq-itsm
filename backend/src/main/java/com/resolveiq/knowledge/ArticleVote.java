package com.resolveiq.knowledge;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/** One person's "was this helpful?" answer; one vote per person per article. */
@Entity
@Table(name = "kb_votes")
public class ArticleVote {

    @Embeddable
    public static class Key implements Serializable {
        @Column(name = "article_id")
        private Long articleId;
        @Column(name = "user_id")
        private Long userId;

        protected Key() {
        }

        public Key(Long articleId, Long userId) {
            this.articleId = articleId;
            this.userId = userId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(articleId, k.articleId) && Objects.equals(userId, k.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(articleId, userId);
        }
    }

    @EmbeddedId
    private Key id;

    @Column(nullable = false)
    private boolean helpful;

    @Column(name = "voted_at", nullable = false)
    private Instant votedAt;

    protected ArticleVote() {
    }

    public ArticleVote(Long articleId, Long userId, boolean helpful, Instant at) {
        this.id = new Key(articleId, userId);
        this.helpful = helpful;
        this.votedAt = at;
    }

    public void change(boolean helpful, Instant at) {
        this.helpful = helpful;
        this.votedAt = at;
    }

    public boolean isHelpful() {
        return helpful;
    }
}
