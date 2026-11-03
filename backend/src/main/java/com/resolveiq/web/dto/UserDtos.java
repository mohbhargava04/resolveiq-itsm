package com.resolveiq.web.dto;

import com.resolveiq.user.domain.ExperienceLevel;
import com.resolveiq.user.domain.Role;
import com.resolveiq.user.domain.Technician;
import com.resolveiq.user.domain.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Set;

/** Authentication and user-management request/response bodies. */
public final class UserDtos {

    private UserDtos() {
    }

    public record LoginRequest(@NotBlank(message = "Email is required") @Email(message = "Enter a valid email") String email,
                               @NotBlank(message = "Password is required") String password) {
    }

    public record RegisterRequest(
            @NotBlank(message = "Full name is required") @Size(max = 120) String fullName,
            @NotBlank(message = "Email is required") @Email(message = "Enter a valid email") @Size(max = 160) String email,
            @NotBlank(message = "Password is required") @Size(min = 8, max = 72, message = "Password must be 8-72 characters") String password,
            @Pattern(regexp = "^$|^\\+?[0-9 ()-]{7,20}$", message = "Enter a valid phone number") String phone,
            @Size(max = 80) String department) {
    }

    /** {@code currentPassword} may be empty for a Google-only account setting its first password. */
    public record ChangePasswordRequest(@Size(max = 72) String currentPassword,
                                        @NotBlank @Size(min = 8, max = 72) String newPassword) {
    }

    public record MfaVerifyRequest(@Size(max = 2000) String challenge,
                                   @NotBlank(message = "Enter the code") @Size(max = 20) String code) {
    }

    public record MfaCodeRequest(@NotBlank(message = "Enter the code") @Size(max = 20) String code) {
    }

    public record MfaDisableRequest(@Size(max = 72) String password, @Size(max = 20) String code) {
    }

    public record ForgotPasswordRequest(@NotBlank(message = "Email is required") @Email(message = "Enter a valid email") String email) {
    }

    public record ResetPasswordRequest(@NotBlank @Size(max = 200) String token,
                                       @NotBlank @Size(min = 8, max = 72, message = "Password must be 8-72 characters") String password) {
    }

    public record ProfileRequest(@Size(max = 120) String fullName,
                                 @Pattern(regexp = "^$|^\\+?[0-9 ()-]{7,20}$", message = "Enter a valid phone number") String phone,
                                 @Size(max = 80) String department,
                                 @Size(max = 80) String location,
                                 Set<@Size(max = 40) String> skills) {
    }

    public record ChangeRoleRequest(@NotNull Role role, Set<String> skills, ExperienceLevel experienceLevel,
                                    @Min(1) @Max(50) Integer maxActiveIncidents) {
    }

    public record ImportUsersRequest(@NotBlank @Size(max = 200_000) String csv) {
    }

    /** No password = company-account (Google) sign-in only. */
    public record CreateUserRequest(
            @NotNull(message = "Role is required") Role role,
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Email @Size(max = 160) String email,
            @Size(max = 72) String password,
            @Pattern(regexp = "^$|^\\+?[0-9 ()-]{7,20}$", message = "Enter a valid phone number") String phone,
            @Size(max = 80) String department,
            Set<String> skills,
            ExperienceLevel experienceLevel,
            @Min(1) @Max(50) Integer maxActiveIncidents,
            Long teamLeadId) {
    }

    public record UpdateUserRequest(
            @Size(max = 120) String fullName,
            @Pattern(regexp = "^$|^\\+?[0-9 ()-]{7,20}$", message = "Enter a valid phone number") String phone,
            @Size(max = 80) String department,
            Set<String> skills,
            ExperienceLevel experienceLevel,
            @Min(1) @Max(50) Integer maxActiveIncidents,
            Long teamLeadId,
            Boolean active) {
    }

    public record AvailabilityRequest(@NotNull Boolean available) {
    }

    public record UserView(Long id, String fullName, String email, String role, String roleLabel, String initials,
                           String phone, String department, String location, String avatarUrl, boolean active,
                           Instant lastLoginAt, boolean profileCompleted, boolean passwordEnabled, boolean googleLinked,
                           boolean mfaEnabled, Instant lockedUntil, TechnicianInfo technician) {

        public static UserView of(User u) {
            return of(u, null);
        }

        public static UserView of(User u, Integer activeIncidents) {
            TechnicianInfo info = null;
            if (u instanceof Technician t) {
                info = new TechnicianInfo(t.getSkills(), t.getExperienceLevel().name(), t.getMaxActiveIncidents(),
                        t.isAvailable(), activeIncidents,
                        t.getTeamLead() == null ? null : t.getTeamLead().getId(),
                        t.getTeamLead() == null ? null : t.getTeamLead().getFullName());
            }
            return new UserView(u.getId(), u.getFullName(), u.getEmail(), u.getRole().name(),
                    u.getRole().displayName(), u.getInitials(), u.getPhone(), u.getDepartment(), u.getLocation(),
                    u.getAvatarUrl(), u.isActive(), u.getLastLoginAt(), u.isProfileCompleted(), u.isPasswordEnabled(),
                    u.getGoogleSubject() != null, u.isMfaEnabled(), u.getLockedUntil(), info);
        }
    }

    public record TechnicianInfo(Set<String> skills, String experienceLevel, int capacity, boolean available,
                                 Integer activeIncidents, Long teamLeadId, String teamLeadName) {
    }

    /**
     * Result of a sign-in step. Either a session ({@code token}, {@code user}) or, when two-factor
     * authentication is on, {@code mfaRequired} with the {@code challenge} for the second step.
     * {@code mfaSetupRequired} tells the app to send an administrator to set up 2FA first.
     */
    public record AuthResponse(String token, Instant expiresAt, UserView user, Boolean mfaRequired, String challenge,
                               Boolean mfaSetupRequired) {

        public static AuthResponse session(String token, Instant expiresAt, UserView user, boolean mfaSetupRequired) {
            return new AuthResponse(token, expiresAt, user, null, null, mfaSetupRequired ? Boolean.TRUE : null);
        }

        public static AuthResponse mfa(String challenge, Instant expiresAt) {
            return new AuthResponse(null, expiresAt, null, Boolean.TRUE, challenge, null);
        }
    }
}
