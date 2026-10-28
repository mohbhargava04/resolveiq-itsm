package com.resolveiq.web;

import com.resolveiq.security.AuthService;
import com.resolveiq.security.CurrentUserService;
import com.resolveiq.user.domain.User;
import com.resolveiq.web.dto.CommonDtos.Message;
import com.resolveiq.web.dto.UserDtos.AuthResponse;
import com.resolveiq.web.dto.UserDtos.ChangePasswordRequest;
import com.resolveiq.web.dto.UserDtos.LoginRequest;
import com.resolveiq.web.dto.UserDtos.RegisterRequest;
import com.resolveiq.web.dto.UserDtos.UserView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService auth;
    private final CurrentUserService currentUser;

    public AuthController(AuthService auth, CurrentUserService currentUser) {
        this.auth = auth;
        this.currentUser = currentUser;
    }

    @PostMapping("/login")
    @Operation(summary = "Exchange e-mail and password for a JWT access token")
    public AuthResponse login(@Valid @RequestBody LoginRequest r) {
        AuthService.LoginResult result = auth.login(r.email(), r.password());
        return new AuthResponse(result.token().token(), result.token().expiresAt(), UserView.of(result.user()));
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Self-register as an employee")
    public AuthResponse register(@Valid @RequestBody RegisterRequest r) {
        AuthService.LoginResult result = auth.register(r.fullName(), r.email(), r.password(), r.phone(), r.department());
        return new AuthResponse(result.token().token(), result.token().expiresAt(), UserView.of(result.user()));
    }

    @GetMapping("/me")
    @Transactional(readOnly = true)
    public UserView me() {
        return UserView.of(currentUser.require());
    }

    @PostMapping("/change-password")
    public Message changePassword(@Valid @RequestBody ChangePasswordRequest r) {
        User me = currentUser.require();
        auth.changePassword(me, r.currentPassword(), r.newPassword());
        return new Message("Password changed");
    }
}
