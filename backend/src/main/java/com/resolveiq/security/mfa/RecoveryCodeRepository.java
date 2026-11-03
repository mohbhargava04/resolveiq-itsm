package com.resolveiq.security.mfa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface RecoveryCodeRepository extends JpaRepository<RecoveryCode, Long> {

    Optional<RecoveryCode> findByUserIdAndCodeHashAndUsedAtIsNull(Long userId, String codeHash);

    @Query("select count(c) from RecoveryCode c where c.userId = :userId and c.usedAt is null")
    long countUnused(Long userId);

    List<RecoveryCode> findByUserId(Long userId);

    @Modifying
    @Query("delete from RecoveryCode c where c.userId = :userId")
    void deleteByUserId(Long userId);
}
