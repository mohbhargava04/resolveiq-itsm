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

    public record ChangePasswordRequest(@NotBlank String currentPassword,
                                        @NotBlank @Size(min = 8, max = 72) String newPassword) {
    }

    public record CreateUserRequest(
            @NotNull(message = "Role is required") Role role,
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Email @Size(max = 160) String email,
            @NotBlank @Size(min = 8, max = 72) String password,
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
                           String phone, String department, boolean active, Instant lastLoginAt,
                           TechnicianInfo technician) {

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
                    u.getRole().displayName(), u.getInitials(), u.getPhone(), u.getDepartment(), u.isActive(),
                    u.getLastLoginAt(), info);
        }
    }

    public record TechnicianInfo(Set<String> skills, String experienceLevel, int capacity, boolean available,
                                 Integer activeIncidents, Long teamLeadId, String teamLeadName) {
    }

    public record AuthResponse(String token, Instant expiresAt, UserView user) {
    }
}
