package com.resolveiq.security.mfa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** A single-use backup code for when the phone with the authenticator app is lost. Stored hashed. */
@Entity
@Table(name = "mfa_recovery_codes")
public class RecoveryCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "used_at")
    private Instant usedAt;

    protected RecoveryCode() {
    }

    public RecoveryCode(Long userId, String codeHash) {
        this.userId = userId;
        this.codeHash = codeHash;
    }

    public void use(Instant at) {
        this.usedAt = at;
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public String getCodeHash() {
        return codeHash;
    }
}
