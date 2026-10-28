package com.resolveiq.web;

import com.resolveiq.assignment.AssignmentService;
import com.resolveiq.security.CurrentUserService;
import com.resolveiq.user.UserAdminService;
import com.resolveiq.user.UserFactory;
import com.resolveiq.user.domain.User;
import com.resolveiq.web.dto.CommonDtos.Message;
import com.resolveiq.web.dto.IncidentDtos.TextRequest;
import com.resolveiq.web.dto.UserDtos.AvailabilityRequest;
import com.resolveiq.web.dto.UserDtos.CreateUserRequest;
import com.resolveiq.web.dto.UserDtos.UpdateUserRequest;
import com.resolveiq.web.dto.UserDtos.UserView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** User administration (admins) and technician roster/availability. */
@RestController
@Transactional
@Tag(name = "Users & technicians")
public class UserController {

    private final UserAdminService users;
    private final AssignmentService assignment;
    private final CurrentUserService currentUser;

    public UserController(UserAdminService users, AssignmentService assignment, CurrentUserService currentUser) {
        this.users = users;
        this.assignment = assignment;
        this.currentUser = currentUser;
    }

    @GetMapping("/api/admin/users")
    public List<UserView> list() {
        Map<Long, Integer> load = assignment.workloads();
        return users.listUsers().stream().map(u -> UserView.of(u, load.getOrDefault(u.getId(), 0))).toList();
    }

    @PostMapping("/api/admin/users")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create any kind of user (Factory pattern chooses the subclass from the role)")
    public UserView create(@Valid @RequestBody CreateUserRequest r) {
        User me = currentUser.require();
        User created = users.create(new UserFactory.NewUser(r.role(), r.fullName(), r.email(), r.password(), r.phone(),
                r.department(), r.skills(), r.experienceLevel(), r.maxActiveIncidents()), r.teamLeadId(), me);
        return UserView.of(created, 0);
    }

    @PutMapping("/api/admin/users/{id}")
    public UserView update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest r) {
        User me = currentUser.require();
        User updated = users.update(id, r.fullName(), r.phone(), r.department(), r.skills(), r.experienceLevel(),
                r.maxActiveIncidents(), r.teamLeadId(), r.active(), me);
        return UserView.of(updated, assignment.activeIncidentCount(updated.getId()));
    }

    @PostMapping("/api/admin/users/{id}/reset-password")
    public Message resetPassword(@PathVariable Long id, @Valid @RequestBody TextRequest r) {
        users.resetPassword(id, r.text(), currentUser.require());
        return new Message("Password reset");
    }

    @GetMapping("/api/technicians")
    @PreAuthorize("hasRole('TECHNICIAN')")
    @Operation(summary = "Technician roster with live workload")
    public List<UserView> technicians() {
        Map<Long, Integer> load = assignment.workloads();
        return users.listTechnicians().stream().map(t -> UserView.of(t, load.getOrDefault(t.getId(), 0))).toList();
    }

    @PutMapping("/api/technicians/me/availability")
    @PreAuthorize("hasRole('TECHNICIAN')")
    @Operation(summary = "Go on or off duty (off-duty technicians receive no new assignments)")
    public UserView availability(@Valid @RequestBody AvailabilityRequest r) {
        User me = currentUser.require();
        return UserView.of(users.setAvailability(me, r.available()), assignment.activeIncidentCount(me.getId()));
    }
}
