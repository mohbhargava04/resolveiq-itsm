package com.resolveiq.knowledge;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ArticleVoteRepository extends JpaRepository<ArticleVote, ArticleVote.Key> {
}
