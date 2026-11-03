# Security

This document explains how ResolveIQ protects its users and data, maps every protection to the
**OWASP Top 10:2025**, and lists the tests that prove each one works. It also says honestly what a
real company would still add before running this in production.

- [Threat model](#threat-model)
- [How sign-in works](#how-sign-in-works)
- [OWASP Top 10:2025 mapping](#owasp-top-102025-mapping)
- [Security tests](#security-tests)
- [Configuration checklist for a real deployment](#configuration-checklist-for-a-real-deployment)
- [Known limitations](#known-limitations)
- [Reporting a vulnerability](#reporting-a-vulnerability)

---

## Threat model

| What we protect | Why it matters |
|---|---|
| Accounts and sessions | A stolen session lets an attacker act as an IT admin: change roles, read every incident. |
| Incident data | Incidents describe security problems, outages and people's devices; employees must only see their own. |
| Secrets at rest | Password hashes, two-factor secrets, refresh and reset tokens. |
| Uploaded files | A file is shared with other people, so it must not be able to carry malware or run script. |
| The audit trail | Needed to answer "who did this?" after something goes wrong. |

| Who might attack | Typical attempts | Main defences |
|---|---|---|
| Outsider on the internet | Password guessing, credential stuffing, forged tokens | Rate limits, lockout, breached-password check, 2FA, short signed tokens |
| Employee | Reading other people's incidents, granting themselves a higher role | Server-side access checks on every request, roles read from the database |
| Malicious web page | Making the user's browser call the API (CSRF), stealing tokens (XSS) | SameSite=Strict HttpOnly cookie, custom header on cookie endpoints, strict CSP |
| Someone with a stolen laptop or cookie | Re-using a session | Refresh-token rotation with reuse detection, "sign out everywhere", short access tokens |

## How sign-in works

```
 password ─┐                        ┌─► 2FA code (if enabled) ─┐
           ├─► first factor checked ┤                          ├─► session created
 Google  ──┘   (or Google identity) └─────────────────────────-┘
                                                   │
         access token (15 min, kept in memory) ◄───┤
         refresh token (HttpOnly cookie)       ◄───┘
```

- **Access token** – a signed JWT (HS256), valid for 15 minutes, held only in JavaScript memory (never
  `localStorage`, so an injected script cannot steal it from storage). It carries the user id, a
  session id and a **token version**.
- **Refresh token** – a random 256-bit value in an `HttpOnly; SameSite=Strict; Path=/api/auth` cookie.
  Only its SHA-256 hash is stored. Every refresh **rotates** it. Presenting an already-used token after a
  20-second grace window (two tabs refreshing together) is treated as theft: the whole session family is
  revoked and the event is audited.
- **Every request is re-checked against the database**: the account must still be active, the token
  version must match (bumped on password change, role change, deactivation, "sign out everywhere"),
  and the session must not be revoked. Roles come from the database, never from the token, so a role
  change takes effect on the very next request.
- **Live updates** (server-sent events) cannot send headers, so the app first asks for a **one-time,
  60-second stream ticket** and opens the stream with that. Tokens never appear in URLs or server logs.
- **Google sign-in** uses the OpenID Connect authorisation-code flow with **PKCE (S256)**, `state` and
  `nonce`. Only verified e-mails from the company's allowed domains are accepted; a new person becomes an
  *Employee*, and only an admin can give a higher role. A Google account can never take over an account
  already linked to a different Google account.
- **Two-factor authentication** is TOTP (RFC 6238, implemented and unit-tested against the RFC test
  vector) with ±1 step of clock drift, replay prevention, and 10 single-use recovery codes. The secret
  is encrypted with **AES-256-GCM** before it is stored. Admins can require 2FA for privileged roles.
- **Password reset** sends a single-use link valid for 30 minutes. The response is identical whether
  or not the e-mail exists, so the form cannot be used to discover accounts.

## OWASP Top 10:2025 mapping

### A01 Broken Access Control
- URL rules in `SecurityConfig` (`/api/admin/**` → ADMIN, `/api/reports/**` → TEAM_LEAD, everything else
  signed in) plus a role hierarchy (MANAGER > ADMIN > TEAM_LEAD > TECHNICIAN).
- **Object-level checks**: `IncidentAccessPolicy` decides who may see each incident, so an employee who
  guesses `INC-1042` gets 404, not someone else's data. Attachments and conversation threads go through
  the same check; internal notes are never sent to employees.
- Role-change rules: nobody changes their own role, only a MANAGER grants or removes MANAGER, and a
  technician who has worked incidents cannot be demoted (history would break). Changes revoke sessions.
- Open-redirect protection after Google sign-in: only same-site relative paths are accepted.
- CORS is limited to configured origins; the production app is same-origin.

### A02 Security Misconfiguration
- **Strict Content-Security-Policy**: `script-src 'self'` with no `unsafe-inline` (the theme script was
  moved to a file), `object-src 'none'`, `base-uri 'self'`, `form-action 'self'`, `frame-ancestors 'self'`.
- Other headers: `X-Content-Type-Options: nosniff`, `Referrer-Policy: strict-origin-when-cross-origin`,
  `Permissions-Policy` (camera, microphone, location, payment, USB off), `Cross-Origin-Opener-Policy` and
  `Cross-Origin-Resource-Policy: same-origin`.
- Swagger UI and the H2 console are open only when `ITSM_EXPOSE_DEV_TOOLS=true` (development profile).
  Otherwise the H2 console is denied and Swagger needs an ADMIN.
- `DeploymentSafetyCheck` **refuses to start** a non-demo deployment that still uses the public
  development secrets or exposes dev tools.
- Stack traces are never returned (`server.error.include-stacktrace: never`); unexpected errors return
  a generic message with a reference code that matches the server log.
- The Docker image runs as an unprivileged user.

### A03 Software Supply Chain Failures
- **Dependabot** (`.github/dependabot.yml`) watches Maven, npm, GitHub Actions and Docker base images.
- **CodeQL** (`.github/workflows/codeql.yml`) scans Java and TypeScript on every push and weekly, using the
  `security-extended` query set.
- `npm ci` and the Maven wrapper give reproducible builds from lock files.
- QR codes for 2FA are generated in the browser by a bundled library: the secret is never sent to a
  third-party QR service.

### A04 Cryptographic Failures
- Passwords: **BCrypt**, work factor 12, per-password salt.
- Two-factor secrets: **AES-256-GCM** with a fresh random nonce per value and a versioned format (`v1:`),
  key from `ITSM_DATA_KEY` (separate from the token-signing key).
- Refresh, reset and recovery tokens are stored only as **SHA-256 hashes**; secrets are compared in
  constant time.
- Cookies are `Secure` whenever the site is reached over HTTPS (`ITSM_FORWARD_HEADERS=framework` behind
  a cloud proxy).

### A05 Injection
- All database access goes through JPA/Hibernate with bound parameters; no SQL is built from strings.
- Bean Validation on every request body (lengths, formats, enums).
- React escapes all output; the app never uses `dangerouslySetInnerHTML`. Together with the strict CSP,
  an injected `<script>` in an incident description is shown as text and could not run anyway.
- E-mail subjects and CSV imports are length-limited and validated row by row.

### A06 Insecure Design
- The incident lifecycle is a **State pattern**: an illegal transition (e.g. closing an unresolved
  incident) is impossible by construction and returns `INVALID_STATE_TRANSITION`.
- Least privilege by default: Google sign-ups become Employees; admins must grant more.
- Abuse limits designed in from the start: rate limits, upload size and count limits, lockout.
- Uploaded files are checked by their **content (magic bytes)**, not their name, against an allow-list;
  SVG and HTML are refused because they can carry script; a signature scanner rejects known malware
  (EICAR test file). Downloads are sent with `nosniff` and `Content-Security-Policy: sandbox`, and only
  images are shown inline (everything else downloads), so even a disguised file cannot run as a page.

### A07 Authentication Failures
- **Rate limiting** (token buckets, per IP, before Spring Security): sign-in 10/min, 2FA 10 per 5 min,
  register 5 per 10 min, password reset 5 per 15 min, refresh 60/min, API 600/min. Excess gets HTTP 429
  with `Retry-After`.
- **Account lockout** after 5 failed passwords or 2FA codes, for 15 minutes; admins can unlock.
- **Password policy** (NIST SP 800-63B style): at least 8 characters, not only letters, not containing the
  e-mail name, and not in a list of commonly breached passwords.
- 2FA, recovery codes, and session management ("where you're signed in", sign out one device or all).
- Generic error messages: "wrong e-mail or password" never reveals which part was wrong.

### A08 Software or Data Integrity Failures
- Tokens are signed and checked for algorithm (HS256 only), issuer, expiry and **type**: a 2FA challenge
  token or a Google-sign-in state token cannot be used as an access token.
- `state`, `nonce` and PKCE stop forged or replayed Google sign-in responses.
- Optimistic locking (`@Version`) stops two technicians silently overwriting each other's changes.
- The inbound e-mail webhook needs a shared secret header, compared in constant time.

### A09 Security Logging & Alerting Failures
- The **audit trail** records every incident action and every security event: sign-ins, failures,
  lockouts, 2FA changes, password resets, role changes, session revocations, refresh-token reuse.
- **Admin → Security** shows a live overview: privileged accounts without 2FA, locked accounts, recent
  security events, and the sign-in policy in force. The audit page can filter to security events only.
- Reset links and other secrets are redacted from the e-mail log outside demo mode.

### A10 Mishandling of Exceptional Conditions
- One `GlobalExceptionHandler` turns every failure into an RFC 7807 problem response with a stable
  `code`; nothing falls through to a default error page.
- Each bulk-action item and each inbound e-mail runs in its own transaction, so one bad item cannot
  roll back or corrupt the others.
- Failures **fail closed**: an unreadable token, a missing session or a database error means "not signed
  in", never "allowed".
- If the database is down, the API answers 503 with a clear message instead of hanging.

## Security tests

All run in CI (`./mvnw verify`) on every push.

| Test | What it proves |
|---|---|
| SEC-01 | Refresh cookie is HttpOnly, SameSite=Strict, scoped to `/api/auth`; access token lives 15 min |
| SEC-02 | Refresh rotates the token; replaying a used token signs the whole session out |
| SEC-03 | Refresh and sign-out need the `X-Requested-With` header (CSRF defence in depth) |
| SEC-04 | Signing out revokes the session at once |
| SEC-05 | Deactivating an account invalidates its existing tokens |
| SEC-06 | A role change takes effect immediately; the role in an old token is never trusted |
| SEC-07 | Only an access token authenticates API calls (not a challenge or forged token) |
| SEC-08 | Users can see their sessions and sign other devices out |
| SEC-09 | Changing the password signs out other devices |
| SEC-10 | 2FA setup needs a correct code; wrong and replayed codes are refused |
| SEC-11 | A recovery code works once; wrong 2FA codes count towards lockout |
| SEC-12 | Forgot-password gives the same answer for unknown e-mails; links work once, for 30 min |
| SEC-13 | Self-registration only accepts company domains and can be switched off |
| SEC-14 | A company-account-only user cannot sign in with any password |
| SEC-15 | Admin security overview and 2FA reset |
| SEC-16 | More than 10 sign-ins a minute get HTTP 429 with `Retry-After` |
| SEC-17 | Strict CSP (no inline scripts) and the other security headers |
| SEC-18 | The live-update stream needs a one-time ticket; tokens never appear in URLs |
| SEC-19 | Demo accounts are listed only in demo mode |
| SSO-01–07 | Google sign-in: PKCE/state, role preserved, Employee default, personal/unverified e-mails refused, no account takeover, open-redirect blocked |
| ATT-01–02 | Disguised files, SVG/HTML and the EICAR test file are refused; downloads are sandboxed |
| ADM-01–04 | Role-change rules, CSV import, unlock, sign out everywhere |
| UNIT-01–08 | TOTP test vector, AES-GCM tamper detection, token bucket, password policy, file-type detection, deployment safety check |

## Configuration checklist for a real deployment

| Setting | Why |
|---|---|
| `ITSM_JWT_SECRET` | Random, 32+ characters. With the public default anyone could forge tokens. |
| `ITSM_DATA_KEY` | Random, 32+ characters, different from the JWT secret. Encrypts 2FA secrets. |
| `ITSM_DEMO_MODE=false` | Hides demo accounts and enables the startup safety check. |
| `ITSM_FORWARD_HEADERS=framework` | Behind a cloud load balancer, so cookies are marked `Secure`. |
| `ITSM_API_DOCS=false` | Hides the API description from the public. |
| `ITSM_PUBLIC_URL` | The real `https://` address, used in e-mail links and the Google redirect. |
| Allowed e-mail domains | Admin → Settings → Sign-in and access. |
| Require 2FA for privileged roles | Same page. |

[DEPLOYMENT.md](DEPLOYMENT.md) walks through these step by step.

## Known limitations

These are reasonable for a course project; a production system would address them.

- **Rate limits are per server instance** (in memory). Several instances would need a shared store
  such as Redis.
- **Malware scanning is signature-based** and only knows a few signatures. A real deployment would
  plug a ClamAV or cloud scanner into the `MalwareScanner` interface.
- **HS256** uses one shared secret. A system with several services would sign with RS256/ES256 keys so
  other services can verify without being able to sign.
- Encryption keys are read from environment variables, not a key-management service, and are not
  rotated automatically (the `v1:` prefix allows adding rotation later).
- No WebAuthn/passkeys yet; TOTP is the second factor.
- Attachments are stored in the database, which is simple and transactional but would move to object
  storage at scale.

## Reporting a vulnerability

Please do not open a public issue. Use GitHub's **Security → Report a vulnerability** on this
repository, or contact the team listed in the README. We aim to reply within a week.
