package com.resolveiq.user.domain;

import com.resolveiq.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.Entity;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Abstract base for every person who uses the system.
 *
 * <p><b>Inheritance</b>: Employee, Technician, Administrator are genuine "is-a" specialisations
 * of a User. Single-table mapping keeps queries such as "all active users" simple.</p>
 *
 * <p><b>Encapsulation</b>: there are no public setters. State changes go through intention-revealing
 * methods ({@link #deactivate()}, {@link #recordFailedLogin(Instant, int, Duration)}) that enforce
 * invariants, and the password hash never leaves the object except for verification.</p>
 */
@Entity
@Table(name = "users")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "user_type")
public abstract class User extends BaseEntity {

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(nullable = false, unique = true, length = 160)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(length = 30)
    private String phone;

    @Column(length = 80)
    private String department;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(length = 80)
    private String location;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    /** False for accounts created on first Google sign-in until the person confirms their details. */
    @Column(name = "profile_completed", nullable = false)
    private boolean profileCompleted = true;

    /** False for company-account-only users, who have no usable password. */
    @Column(name = "password_enabled", nullable = false)
    private boolean passwordEnabled = true;

    /** Google's stable account id ("sub" claim), set when the account is linked to Google sign-in. */
    @Column(name = "google_subject", length = 64, unique = true)
    private String googleSubject;

    /**
     * Incremented whenever existing sessions must stop working at once (deactivation, role change,
     * password change, "sign out everywhere"). Access tokens carry the version they were issued with.
     */
    @Column(name = "token_version", nullable = false)
    private int tokenVersion;

    /** TOTP secret, encrypted at rest (AES-GCM) by {@code SecretCipher}. */
    @Column(name = "mfa_secret", length = 200)
    private String mfaSecret;

    /** Secret generated during set-up, promoted to {@link #mfaSecret} once a valid code is entered. */
    @Column(name = "mfa_pending_secret", length = 200)
    private String mfaPendingSecret;

    @Column(name = "mfa_enabled", nullable = false)
    private boolean mfaEnabled;

    /** Last TOTP time-step accepted, so the same code can never be replayed. */
    @Column(name = "mfa_last_used_step")
    private Long mfaLastUsedStep;

    protected User() {
        // for JPA
    }

    protected User(String fullName, String email, String passwordHash, String phone, String department) {
        rename(fullName);
        this.email = normaliseEmail(email);
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.phone = phone;
        this.department = department;
    }

    /** Polymorphic: each subclass knows its own role. */
    public abstract Role getRole();

    /** Whether this kind of user can be assigned incidents. Overridden by Technician. */
    public boolean canHandleIncidents() {
        return false;
    }

    public static String normaliseEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        return email.trim().toLowerCase();
    }

    // ---- behaviour -------------------------------------------------------------------------

    public void rename(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("Full name is required");
        }
        this.fullName = fullName.trim();
    }

    public void updateContactDetails(String phone, String department) {
        this.phone = phone;
        this.department = department;
    }

    public void changePasswordHash(String newPasswordHash) {
        this.passwordHash = Objects.requireNonNull(newPasswordHash);
        this.passwordEnabled = true;
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
        revokeAllSessions();
    }

    /** Company-account-only user: the stored hash is of a random value nobody knows. */
    public void disablePasswordLogin(String unusableHash) {
        this.passwordHash = Objects.requireNonNull(unusableHash);
        this.passwordEnabled = false;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
        revokeAllSessions();
    }

    /** Invalidates every access token issued so far. */
    public void revokeAllSessions() {
        this.tokenVersion++;
    }

    public void unlock() {
        this.lockedUntil = null;
        this.failedLoginAttempts = 0;
    }

    public void updateProfile(String phone, String department, String location) {
        this.phone = blankToNull(phone);
        this.department = blankToNull(department);
        this.location = blankToNull(location);
    }

    public void completeProfile() {
        this.profileCompleted = true;
    }

    public void markProfileIncomplete() {
        this.profileCompleted = false;
    }

    public void changeAvatar(String url) {
        this.avatarUrl = blankToNull(url);
    }

    /** Links this account to a Google identity. A different Google account can never take it over. */
    public void linkGoogleAccount(String subject) {
        Objects.requireNonNull(subject, "subject");
        if (googleSubject != null && !googleSubject.equals(subject)) {
            throw new IllegalStateException("This account is already linked to a different Google account");
        }
        this.googleSubject = subject;
    }

    // ---- two-factor authentication ---------------------------------------------------------------

    public void beginMfaEnrolment(String encryptedSecret) {
        this.mfaPendingSecret = Objects.requireNonNull(encryptedSecret);
    }

    public void confirmMfaEnrolment(long acceptedStep) {
        if (mfaPendingSecret == null) {
            throw new IllegalStateException("Two-factor set-up has not been started");
        }
        this.mfaSecret = mfaPendingSecret;
        this.mfaPendingSecret = null;
        this.mfaEnabled = true;
        this.mfaLastUsedStep = acceptedStep;
    }

    public void disableMfa() {
        this.mfaSecret = null;
        this.mfaPendingSecret = null;
        this.mfaEnabled = false;
        this.mfaLastUsedStep = null;
    }

    /** Records a used TOTP step; returns false if it (or a later one) was already used - a replay. */
    public boolean acceptMfaStep(long step) {
        if (mfaLastUsedStep != null && step <= mfaLastUsedStep) {
            return false;
        }
        this.mfaLastUsedStep = step;
        return true;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /**
     * Records a failed login and locks the account once {@code maxAttempts} is reached.
     *
     * @return true if this attempt caused the account to become locked
     */
    public boolean recordFailedLogin(Instant now, int maxAttempts, Duration lockDuration) {
        failedLoginAttempts++;
        if (failedLoginAttempts >= maxAttempts) {
            lockedUntil = now.plus(lockDuration);
            failedLoginAttempts = 0;
            return true;
        }
        return false;
    }

    public void recordSuccessfulLogin(Instant now) {
        failedLoginAttempts = 0;
        lockedUntil = null;
        lastLoginAt = now;
    }

    // ---- accessors ---------------------------------------------------------------------------

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    /** Package-private on purpose would be ideal; it is only read by the authentication service. */
    public String getPasswordHash() {
        return passwordHash;
    }

    public String getPhone() {
        return phone;
    }

    public String getDepartment() {
        return department;
    }

    public boolean isActive() {
        return active;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public String getLocation() {
        return location;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public boolean isProfileCompleted() {
        return profileCompleted;
    }

    public boolean isPasswordEnabled() {
        return passwordEnabled;
    }

    public String getGoogleSubject() {
        return googleSubject;
    }

    public int getTokenVersion() {
        return tokenVersion;
    }

    public String getMfaSecret() {
        return mfaSecret;
    }

    public String getMfaPendingSecret() {
        return mfaPendingSecret;
    }

    public boolean isMfaEnabled() {
        return mfaEnabled;
    }

    /** Admins and managers can change the whole system, so they are the accounts 2FA matters most for. */
    public boolean isPrivileged() {
        return getRole() == Role.ADMIN || getRole() == Role.MANAGER;
    }

    public String getInitials() {
        String[] parts = fullName.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (!p.isEmpty() && sb.length() < 2) {
                sb.append(Character.toUpperCase(p.charAt(0)));
            }
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return getRole().displayName() + "{" + getId() + ", " + email + "}";
    }
}
