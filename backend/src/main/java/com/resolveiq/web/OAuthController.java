package com.resolveiq.web;

import com.resolveiq.config.AppProperties;
import com.resolveiq.security.AuthCookies;
import com.resolveiq.security.AuthService;
import com.resolveiq.security.EmailDomainPolicy;
import com.resolveiq.security.JwtService;
import com.resolveiq.security.oauth.AuthorizationRequest;
import com.resolveiq.security.oauth.ExternalIdentity;
import com.resolveiq.security.oauth.FederatedSignInException;
import com.resolveiq.security.oauth.FederatedSignInService;
import com.resolveiq.security.oauth.IdentityProvider;
import com.resolveiq.security.oauth.IdentityProviders;
import com.resolveiq.security.session.RequestMeta;
import com.resolveiq.security.session.SignInMethod;
import com.resolveiq.user.domain.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

/**
 * "Continue with Google". Two browser redirects:
 * <ol>
 *   <li>{@code /start}: create state, nonce and PKCE verifier, keep them in a signed HttpOnly cookie,
 *       and send the browser to Google;</li>
 *   <li>{@code /callback}: check the state against the cookie, exchange the code, verify the ID
 *       token, find or create the account, then either start the session (refresh cookie) and go to
 *       the app, or go to the 2FA step.</li>
 * </ol>
 * Failures redirect to the sign-in page with a short reason code rather than showing an error page.
 */
@RestController
@RequestMapping("/api/auth/oauth")
@Tag(name = "Authentication")
public class OAuthController {

    private static final Logger log = LoggerFactory.getLogger(OAuthController.class);
    private static final Duration STATE_VALIDITY = Duration.ofMinutes(10);

    private final IdentityProviders providers;
    private final FederatedSignInService federated;
    private final AuthService auth;
    private final JwtService jwt;
    private final AuthCookies cookies;
    private final EmailDomainPolicy domains;
    private final AppProperties app;

    public OAuthController(IdentityProviders providers, FederatedSignInService federated, AuthService auth,
                           JwtService jwt, AuthCookies cookies, EmailDomainPolicy domains, AppProperties app) {
        this.providers = providers;
        this.federated = federated;
        this.auth = auth;
        this.jwt = jwt;
        this.cookies = cookies;
        this.domains = domains;
        this.app = app;
    }

    @GetMapping("/{provider}/start")
    @Operation(summary = "Begin signing in with a company account (redirects to the provider)")
    public ResponseEntity<Void> start(@PathVariable String provider, @RequestParam(required = false) String returnTo,
                                      HttpServletRequest req, HttpServletResponse res) {
        IdentityProvider idp = providers.enabled(provider).orElse(null);
        if (idp == null) {
            return redirect("/login?error=provider_disabled");
        }
        AuthorizationRequest ar = AuthorizationRequest.create(callbackUri(idp), safeReturn(returnTo));
        String state = jwt.issueChallenge(JwtService.TYPE_OAUTH, idp.id(), Map.of(
                "state", ar.state(), "nonce", ar.nonce(), "verifier", ar.codeVerifier(),
                "redirect", ar.redirectUri(), "returnTo", ar.returnTo()), STATE_VALIDITY).token();
        cookies.setOAuthState(req, res, state, STATE_VALIDITY);
        return redirect(idp.authorizationUri(ar, domains.allowedDomains()).toString());
    }

    @GetMapping("/{provider}/callback")
    @Operation(summary = "Provider redirects back here after the person signs in")
    public ResponseEntity<Void> callback(@PathVariable String provider, @RequestParam(required = false) String code,
                                         @RequestParam(required = false) String state,
                                         @RequestParam(required = false) String error,
                                         HttpServletRequest req, HttpServletResponse res) {
        cookies.clear(req, res, AuthCookies.OAUTH_STATE);
        try {
            IdentityProvider idp = providers.enabled(provider)
                    .orElseThrow(() -> new FederatedSignInException("provider_disabled", "Provider not enabled"));
            if (error != null) {
                throw new FederatedSignInException("access_denied".equals(error) ? "cancelled" : "provider_error",
                        "Provider returned " + error);
            }
            Jwt saved = cookies.read(req, AuthCookies.OAUTH_STATE)
                    .flatMap(c -> jwt.readChallenge(c, JwtService.TYPE_OAUTH))
                    .orElseThrow(() -> new FederatedSignInException("expired", "Sign-in took too long or was started in another browser"));
            if (state == null || !state.equals(saved.getClaimAsString("state")) || !idp.id().equals(saved.getSubject())) {
                throw new FederatedSignInException("state_mismatch", "State mismatch - possible login CSRF");
            }
            if (code == null) {
                throw new FederatedSignInException("provider_error", "No authorization code returned");
            }
            AuthorizationRequest ar = new AuthorizationRequest(state, saved.getClaimAsString("nonce"),
                    saved.getClaimAsString("verifier"), saved.getClaimAsString("redirect"), saved.getClaimAsString("returnTo"));
            ExternalIdentity identity = idp.exchange(code, ar);
            RequestMeta meta = RequestMeta.of(req);
            User user = federated.resolve(identity, meta.ipAddress());
            AuthService.SignInResult result = auth.afterFirstFactor(user, SignInMethod.GOOGLE, meta);
            String returnTo = safeReturn(ar.returnTo());
            return switch (result) {
                case AuthService.SignedIn s -> {
                    cookies.setRefresh(req, res, s.session().refreshToken(), s.session().refreshExpiresAt());
                    yield redirect(s.session().user().isProfileCompleted() ? returnTo : "/welcome");
                }
                case AuthService.MfaRequired m -> {
                    cookies.setMfaChallenge(req, res, m.challenge(), Duration.ofMinutes(5));
                    yield redirect("/login?step=mfa");
                }
            };
        } catch (FederatedSignInException ex) {
            log.info("Company-account sign-in failed ({}): {}", ex.reason(), ex.getMessage());
            return redirect("/login?error=" + ex.reason());
        }
    }

    private String callbackUri(IdentityProvider idp) {
        return app.publicUrl() + "/api/auth/oauth/" + idp.id() + "/callback";
    }

    /** Only same-site paths are allowed after sign-in (no open redirect to another site). */
    static String safeReturn(String returnTo) {
        if (returnTo == null || !returnTo.startsWith("/") || returnTo.startsWith("//") || returnTo.contains("\\")
                || returnTo.startsWith("/api/") || returnTo.startsWith("/login")) {
            return "/";
        }
        return returnTo;
    }

    /** Same-site paths are sent as relative redirects, so the browser stays on the host it is using. */
    private static ResponseEntity<Void> redirect(String location) {
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, location)
                .header(HttpHeaders.CACHE_CONTROL, "no-store").build();
    }
}
