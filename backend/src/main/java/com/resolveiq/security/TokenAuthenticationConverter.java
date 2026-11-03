package com.resolveiq.security;

import com.resolveiq.security.session.SessionService;
import com.resolveiq.user.UserRepository;
import com.resolveiq.user.domain.User;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Turns a verified access token into an authenticated user - but only after checking the database:
 *
 * <ol>
 *   <li>the account still exists and is active;</li>
 *   <li>the token's version ({@code tv}) matches the user's current version, so deactivation, a role
 *       change, a password reset or "sign out everywhere" takes effect on the very next request;</li>
 *   <li>the token's session ({@code sid}) has not been signed out.</li>
 * </ol>
 *
 * <p>The role used for authorisation is read from the database, not from the token, so a demoted
 * administrator loses admin access immediately rather than when the token expires.</p>
 */
@Component
public class TokenAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserRepository users;
    private final SessionService sessions;

    public TokenAuthenticationConverter(UserRepository users, SessionService sessions) {
        this.users = users;
        this.sessions = sessions;
    }

    @Override
    @Transactional(readOnly = true)
    public AbstractAuthenticationToken convert(Jwt jwt) {
        if (!JwtService.TYPE_ACCESS.equals(jwt.getClaimAsString(JwtService.CLAIM_TYPE))) {
            throw new InvalidBearerTokenException("Not an access token");
        }
        Object uid = jwt.getClaim(JwtService.CLAIM_USER_ID);
        if (!(uid instanceof Number id)) {
            throw new InvalidBearerTokenException("Token has no user");
        }
        User user = users.findById(id.longValue())
                .filter(User::isActive)
                .orElseThrow(() -> new InvalidBearerTokenException("Account not found or disabled"));
        Object tv = jwt.getClaim(JwtService.CLAIM_TOKEN_VERSION);
        if (!(tv instanceof Number version) || version.intValue() != user.getTokenVersion()) {
            throw new InvalidBearerTokenException("Token has been revoked");
        }
        if (!sessions.isAlive(jwt.getClaimAsString(JwtService.CLAIM_SESSION))) {
            throw new InvalidBearerTokenException("Session has been signed out");
        }
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())),
                user.getEmail());
    }
}
