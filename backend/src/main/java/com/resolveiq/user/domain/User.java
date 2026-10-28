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
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
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
