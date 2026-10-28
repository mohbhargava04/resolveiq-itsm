package com.resolveiq.security;

import com.resolveiq.common.exception.AccountDisabledException;
import com.resolveiq.common.exception.InvalidCredentialsException;
import com.resolveiq.user.UserRepository;
import com.resolveiq.user.domain.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves the authenticated user from the JWT in the security context. Re-reads the user from the
 * database on every request so that deactivating an account takes effect immediately, even if the
 * user still holds an unexpired token.
 */
@Service
public class CurrentUserService {

    private final UserRepository users;

    public CurrentUserService(UserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public User require() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            throw new InvalidCredentialsException();
        }
        Object uid = jwt.getClaim(JwtService.CLAIM_USER_ID);
        if (!(uid instanceof Number id)) {
            throw new InvalidCredentialsException();
        }
        User user = users.findById(id.longValue()).orElseThrow(InvalidCredentialsException::new);
        if (!user.isActive()) {
            throw new AccountDisabledException();
        }
        return user;
    }
}
