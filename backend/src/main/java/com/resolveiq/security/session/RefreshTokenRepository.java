package com.resolveiq.security.session;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByFamilyId(String familyId);

    /** A session is alive while its newest token has not been revoked (expiry is checked on refresh). */
    @Query("select count(t) > 0 from RefreshToken t where t.familyId = :familyId and t.revokedAt is null")
    boolean isFamilyAlive(@Param("familyId") String familyId);

    @Query("select t from RefreshToken t where t.user.id = :userId and t.revokedAt is null and t.expiresAt > :now order by t.issuedAt desc")
    List<RefreshToken> findActiveForUser(@Param("userId") Long userId, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now, t.revokeReason = :reason where t.familyId = :familyId and t.revokedAt is null")
    int revokeFamily(@Param("familyId") String familyId, @Param("reason") String reason, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now, t.revokeReason = :reason where t.user.id = :userId and t.revokedAt is null")
    int revokeAllForUser(@Param("userId") Long userId, @Param("reason") String reason, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now, t.revokeReason = :reason where t.user.id = :userId and t.familyId <> :keepFamily and t.revokedAt is null")
    int revokeOthersForUser(@Param("userId") Long userId, @Param("keepFamily") String keepFamily,
                            @Param("reason") String reason, @Param("now") Instant now);

    @Query("select count(distinct t.familyId) from RefreshToken t where t.revokedAt is null and t.expiresAt > :now")
    long countActiveSessions(@Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RefreshToken t where t.expiresAt < :before")
    int deleteExpiredBefore(@Param("before") Instant before);
}
