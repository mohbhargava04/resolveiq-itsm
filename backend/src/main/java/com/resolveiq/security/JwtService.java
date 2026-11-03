package com.resolveiq.security;

import com.resolveiq.user.domain.User;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues and reads signed (HMAC-SHA256) JSON Web Tokens.
 *
 * <ul>
 *   <li><b>Access tokens</b> ({@code typ=access}) authenticate API calls. They live only
 *       {@code token-validity-minutes} (15 by default) and are kept in browser memory, never in storage.
 *       They carry the user's {@code tv} (token version) and {@code sid} (session) so the server can
 *       reject them early when an account is disabled or the session is signed out.</li>
 *   <li><b>Challenge tokens</b> ({@code typ=mfa}, {@code typ=oauth}) carry state between two steps of
 *       a sign-in, e.g. "password correct, now enter your 2FA code". They are short-lived and the
 *       resource server refuses them as access tokens.</li>
 * </ul>
 */
@Service
public class JwtService {

    public static final String ISSUER = "resolveiq";
    public static final String CLAIM_USER_ID = "uid";
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_NAME = "name";
    public static final String CLAIM_TYPE = "typ";
    public static final String CLAIM_TOKEN_VERSION = "tv";
    public static final String CLAIM_SESSION = "sid";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_MFA = "mfa";
    public static final String TYPE_OAUTH = "oauth";

    private final JwtEncoder encoder;
    private final JwtDecoder challengeDecoder;
    private final SecurityProperties properties;
    private final Clock clock;

    public JwtService(JwtEncoder encoder, @org.springframework.beans.factory.annotation.Qualifier("challengeJwtDecoder")
    JwtDecoder challengeDecoder, SecurityProperties properties, Clock clock) {
        this.encoder = encoder;
        this.challengeDecoder = challengeDecoder;
        this.properties = properties;
        this.clock = clock;
    }

    public record IssuedToken(String token, Instant expiresAt) {
    }

    /** Access token for one signed-in session. */
    public IssuedToken issue(User user, String sessionId) {
        Instant now = clock.instant();
        Instant expiry = now.plus(properties.tokenValidityMinutes(), ChronoUnit.MINUTES);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(expiry)
                .subject(user.getEmail())
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .claim(CLAIM_USER_ID, user.getId())
                .claim(CLAIM_ROLE, user.getRole().name())
                .claim(CLAIM_NAME, user.getFullName())
                .claim(CLAIM_TOKEN_VERSION, user.getTokenVersion())
                .claim(CLAIM_SESSION, sessionId)
                .build();
        return new IssuedToken(sign(claims), expiry);
    }

    /** Short-lived token carrying state between two sign-in steps. */
    public IssuedToken issueChallenge(String type, String subject, Map<String, Object> data, Duration validity) {
        Instant now = clock.instant();
        Instant expiry = now.plus(validity);
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(expiry)
                .subject(subject)
                .claim(CLAIM_TYPE, type);
        data.forEach(claims::claim);
        return new IssuedToken(sign(claims.build()), expiry);
    }

    /** Reads a challenge token of the expected type; empty if it is invalid, expired or of another type. */
    public Optional<Jwt> readChallenge(String token, String expectedType) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Jwt jwt = challengeDecoder.decode(token);
            Instant exp = jwt.getExpiresAt();
            // checked against the application clock as well, so expiry behaves the same in tests
            if (!expectedType.equals(jwt.getClaimAsString(CLAIM_TYPE)) || exp == null || exp.isBefore(clock.instant())) {
                return Optional.empty();
            }
            return Optional.of(jwt);
        } catch (JwtException ex) {
            return Optional.empty();
        }
    }

    private String sign(JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
