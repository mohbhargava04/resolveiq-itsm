package com.resolveiq.security.session;

import com.resolveiq.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One issued refresh token. Every refresh <b>rotates</b> the token: the old row is marked
 * {@code ROTATED} and a new row is added to the same <i>family</i> (one family = one signed-in
 * browser). Presenting a rotated token again means it was copied, so the whole family is revoked.
 * Only the SHA-256 hash of the token is stored.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    public static final String ROTATED = "ROTATED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "family_id", nullable = false, length = 36)
    private String familyId;

    @Column(name = "family_started", nullable = false)
    private Instant familyStarted;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "rotated_at")
    private Instant rotatedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoke_reason", length = 40)
    private String revokeReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "sign_in_method", nullable = false, length = 20)
    private SignInMethod signInMethod;

    @Column(name = "user_agent", length = 250)
    private String userAgent;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    protected RefreshToken() {
        // for JPA
    }

    public RefreshToken(User user, String tokenHash, String familyId, Instant familyStarted, Instant issuedAt,
                        Instant expiresAt, SignInMethod method, RequestMeta meta) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.familyId = familyId;
        this.familyStarted = familyStarted;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.signInMethod = method;
        this.userAgent = meta.userAgent();
        this.ipAddress = meta.ipAddress();
    }

    public boolean isActive(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    public boolean wasRotated() {
        return ROTATED.equals(revokeReason);
    }

    public void rotate(Instant at) {
        this.rotatedAt = at;
        revoke(ROTATED, at);
    }

    public void revoke(String reason, Instant at) {
        if (revokedAt == null) {
            this.revokedAt = at;
            this.revokeReason = reason;
        }
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getFamilyId() {
        return familyId;
    }

    public Instant getFamilyStarted() {
        return familyStarted;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRotatedAt() {
        return rotatedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public String getRevokeReason() {
        return revokeReason;
    }

    public SignInMethod getSignInMethod() {
        return signInMethod;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public String getIpAddress() {
        return ipAddress;
    }
}
