package com.resolveiq.security.reset;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface OneTimeTokenRepository extends JpaRepository<OneTimeToken, Long> {

    Optional<OneTimeToken> findByTokenHashAndPurpose(String tokenHash, String purpose);

    /** Requesting a new link invalidates earlier unused ones. */
    @Modifying
    @Query("update OneTimeToken t set t.usedAt = :now where t.userId = :userId and t.purpose = :purpose and t.usedAt is null")
    int invalidateAll(Long userId, String purpose, Instant now);
}
