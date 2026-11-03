package com.resolveiq.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;

/**
 * The cookies used during sign-in. All are:
 * <ul>
 *   <li><b>HttpOnly</b> - JavaScript (including any injected script) cannot read them;</li>
 *   <li><b>Secure</b> whenever the site is served over HTTPS;</li>
 *   <li>scoped to {@code /api/auth}, so they are not even sent with ordinary API calls.</li>
 * </ul>
 * The refresh cookie is {@code SameSite=Strict}: other sites cannot make the browser send it. The
 * Google sign-in state cookie has to be {@code Lax}, because Google sends the browser back to us
 * with a top-level redirect from its own site.
 */
@Component
public class AuthCookies {

    public static final String REFRESH = "riq_refresh";
    public static final String OAUTH_STATE = "riq_oauth";
    public static final String MFA_CHALLENGE = "riq_mfa";
    /**
     * Not a secret: a readable "a session probably exists" hint, so the app only attempts a silent
     * refresh when there is something to refresh. It grants nothing on its own.
     */
    public static final String SIGNED_IN_HINT = "riq_signed_in";
    private static final String PATH = "/api/auth";

    public void setRefresh(HttpServletRequest req, HttpServletResponse res, String token, Instant expiresAt) {
        Duration ttl = Duration.between(Instant.now(), expiresAt).plusMinutes(1);
        write(res, ResponseCookie.from(REFRESH, token).httpOnly(true).secure(req.isSecure()).sameSite("Strict")
                .path(PATH).maxAge(ttl).build());
        write(res, ResponseCookie.from(SIGNED_IN_HINT, "1").httpOnly(false).secure(req.isSecure()).sameSite("Lax")
                .path("/").maxAge(ttl).build());
    }

    public void setOAuthState(HttpServletRequest req, HttpServletResponse res, String value, Duration ttl) {
        write(res, ResponseCookie.from(OAUTH_STATE, value).httpOnly(true).secure(req.isSecure()).sameSite("Lax")
                .path(PATH + "/oauth").maxAge(ttl).build());
    }

    public void setMfaChallenge(HttpServletRequest req, HttpServletResponse res, String value, Duration ttl) {
        write(res, ResponseCookie.from(MFA_CHALLENGE, value).httpOnly(true).secure(req.isSecure()).sameSite("Lax")
                .path(PATH).maxAge(ttl).build());
    }

    public void clear(HttpServletRequest req, HttpServletResponse res, String name) {
        if (REFRESH.equals(name)) {
            write(res, ResponseCookie.from(SIGNED_IN_HINT, "").secure(req.isSecure()).sameSite("Lax").path("/").maxAge(0).build());
        }
        String path = OAUTH_STATE.equals(name) ? PATH + "/oauth" : PATH;
        write(res, ResponseCookie.from(name, "").httpOnly(true).secure(req.isSecure())
                .sameSite(REFRESH.equals(name) ? "Strict" : "Lax").path(path).maxAge(0).build());
    }

    public Optional<String> read(HttpServletRequest req, String name) {
        Cookie[] cookies = req.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies).filter(c -> name.equals(c.getName())).map(Cookie::getValue)
                .filter(v -> v != null && !v.isBlank()).findFirst();
    }

    private static void write(HttpServletResponse res, ResponseCookie cookie) {
        res.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
