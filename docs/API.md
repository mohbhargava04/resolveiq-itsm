# REST API Reference

Base URL `http://localhost:8080/api`. Everything except the public sign-in endpoints requires
`Authorization: Bearer <access token>`. Interactive documentation: **`/swagger-ui.html`** (open in
development; admin-only otherwise). Errors use RFC 7807 problem JSON with a stable `code` (e.g.
`INVALID_STATE_TRANSITION`, `DUPLICATE_INCIDENT`, `RATE_LIMITED`).

Access tokens last 15 minutes. The refresh token is an HttpOnly cookie that the browser sends only to
`/api/auth`; `POST /auth/refresh` exchanges it for a new access token. Cookie endpoints (`/auth/refresh`,
`/auth/logout`) need the header `X-Requested-With: ResolveIQ`. Sign-in endpoints are rate limited and
answer `429` with `Retry-After` when exceeded. See [SECURITY.md](SECURITY.md).

## Authentication and account

| Method | Path | Who | Purpose |
|---|---|---|---|
| GET | `/auth/options` | public | Sign-in methods, allowed domains, self-registration, demo accounts (demo mode) |
| POST | `/auth/login` | public | E-mail + password → session, or `{mfaRequired, challenge}` when 2FA is on |
| POST | `/auth/mfa/verify` | public | `{code, challenge}` (6-digit code or recovery code) → session |
| POST | `/auth/register` | public | Self-register as an employee (company domains only; can be switched off) |
| POST | `/auth/refresh` | refresh cookie | Rotate the refresh token, return a new access token |
| POST | `/auth/logout` | refresh cookie | End this session |
| POST | `/auth/password/forgot` · `/auth/password/reset` | public | E-mail a one-time link · `{token, newPassword}` |
| GET | `/auth/oauth/google/start` · `/callback` | public | Google sign-in (OIDC with PKCE); redirects back into the app |
| GET | `/auth/me` | any | Current user |
| PUT | `/auth/profile` · POST `/auth/profile/complete` | any | Edit profile · finish the first-sign-in profile step |
| POST | `/auth/change-password` | any | Change password; other devices are signed out |
| GET | `/auth/sessions` · DELETE `/auth/sessions/{id}` · POST `/auth/sessions/revoke-others` | any | Where you're signed in; sign devices out |
| GET | `/auth/mfa` · POST `/auth/mfa/setup` · `/enable` · `/disable` · `/recovery-codes` | any | Two-step verification: status, QR secret, turn on/off, new recovery codes |

## Incidents

| Method | Path | Who | Purpose |
|---|---|---|---|
| POST | `/incidents` | any | Report (auto-classify, prioritise, SLA, duplicate check, auto-assign) |
| GET | `/incidents` | any (employees: own only) | Search — `q, number, status*, priority*, category, service, assigneeId, reporterId, slaStatus, escalated, openOnly, mine, from, to, page, size, sort, direction` |
| GET | `/incidents/{number}` | viewer | Details incl. SLA, notes, `availableActions` |
| PATCH | `/incidents/{number}` | reporter/assignee/supervisor | Edit details (priority recalculated if inputs change) |
| GET | `/incidents/{number}/audit` | viewer | Complete audit history |
| POST | `/incidents/{number}/assign` | team lead+ | `{technicianId}` or `{strategy: SKILL|LOAD|ROUND_ROBIN|EXPERIENCE|SMART}` |
| POST | `/incidents/{number}/acknowledge` | assignee | Acknowledge (stops response clock) |
| POST | `/incidents/{number}/start` | assignee/supervisor | Start investigation |
| POST | `/incidents/{number}/request-info` | assignee/supervisor | `{text}` — pauses SLA |
| POST | `/incidents/{number}/provide-info` | reporter | `{text}` — resumes SLA |
| POST | `/incidents/{number}/resolve` | assignee/supervisor | `{summary, rootCause}` |
| POST | `/incidents/{number}/verify` | reporter/supervisor | Confirm the fix |
| POST | `/incidents/{number}/reopen` | reporter/supervisor | `{text}` reason |
| POST | `/incidents/{number}/close` | reporter/supervisor | `{text}` optional note |
| POST | `/incidents/{number}/notes` | reporter/staff | `{type: COMMENT|INVESTIGATION|INFO_RESPONSE, content, internal}` |
| POST | `/incidents/{number}/escalate` | assignee/supervisor | One level up |
| POST | `/incidents/{number}/reprioritise` | assignee/supervisor | `{}` recalculate or `{priority, reason}` override |
| POST | `/incidents/{number}/feedback` | reporter | `{rating 1-5, comment}` |
| POST | `/incidents/{number}/duplicate` | staff | `{parentNumber}` — close as duplicate |
| POST | `/incidents/{number}/duplicate/dismiss` | staff | Clear a duplicate warning |
| GET | `/incidents/{number}/similar` | viewer | Possible duplicates |
| GET | `/incidents/{number}/suggested-resolutions` | technician+ | Knowledge-base matches |
| GET | `/incidents/{number}/recommendations` | team lead+ | Ranked technicians with reasons (`?strategy=`) |
| GET | `/incidents/{number}/sla-risk` | technician+ | Predicted risk and factors |
| POST | `/incidents/bulk` | team lead+ | `{numbers[], action: ASSIGN|LINK_TO_MAJOR|MARK_DUPLICATE|RESOLVE|CLOSE, params{}}`; per-item results |

## Attachments

| Method | Path | Who | Purpose |
|---|---|---|---|
| GET | `/incidents/{number}/attachments` | viewer | List files |
| POST | `/incidents/{number}/attachments` | viewer | Multipart `file`; ≤ 5 MB, ≤ 10 per incident; type checked by content; malware-scanned |
| GET | `/attachments/{id}` | viewer | Download (images inline, others as download; sandboxed) |
| DELETE | `/attachments/{id}` | uploader / supervisor | Remove (not once the incident is closed) |

## Major incidents

| Method | Path | Who | Purpose |
|---|---|---|---|
| GET | `/major-incidents` | any | Active major incidents (the banner everyone sees) |
| POST | `/incidents/{number}/major` | team lead+ | Declare major: `{summary, link[]}` (incident numbers to attach) |
| POST | `/incidents/{number}/major/updates` | technician+ | `{stage: INVESTIGATING|IDENTIFIED|MONITORING, message}` (resolving the major sets RESOLVED) — sent to everyone affected |
| POST | `/incidents/{number}/link-major` | technician+ | `{majorNumber}` — attach a related report; it resolves with the major |
| POST | `/major-incidents/{number}/affected` | any | "I'm affected too" — files a linked report for the employee |

## Knowledge base

| Method | Path | Who | Purpose |
|---|---|---|---|
| GET | `/kb` | any | Search/browse (`q, category, service, status`); employees see published, employee-facing articles |
| POST | `/kb/suggest` | any | `{title, description}` → self-help articles while reporting |
| GET | `/kb/{id}` | any | Article (counts a view) |
| POST | `/kb/{id}/vote` · `/kb/{id}/solved-my-problem` | any | Helpful yes/no (one per person) · record a deflected report |
| GET | `/kb/draft-from-incident/{number}` | technician+ | Draft an article from a resolved incident |
| POST | `/kb` · PUT `/kb/{id}` · POST `/kb/{id}/publish` | technician+ | Write, edit, publish |
| POST | `/kb/{id}/archive` | team lead+ | Archive |

## E-mail in

| Method | Path | Who | Purpose |
|---|---|---|---|
| POST | `/inbound/email` | mail provider (`X-Inbound-Secret`) | `{from, subject, text}`: `[INC-n]` in the subject adds a reply or answers a question; otherwise a new incident |
| POST | `/admin/inbound/simulate` | admin | Same, from the admin screen |
| GET | `/admin/inbound` | admin | Received-mail log with what each message did |

## Intelligence (live form assistance)

| Method | Path | Who | Purpose |
|---|---|---|---|
| POST | `/intelligence/classify` | any | `{text}` → category suggestion with confidence and signals |
| POST | `/intelligence/duplicates` | any | `{title, description, serviceCode}` → likely duplicates before submitting |
| POST | `/intelligence/priority-preview` | any | `{impact, urgency, affectedUsers, serviceCode, categoryCode}` → priority + SLA |
| POST | `/intelligence/suggest-resolutions` | technician+ | Free-text knowledge-base search |
| GET | `/intelligence/correlations` | technician+ | Root-cause clusters |
| POST | `/intelligence/model/retrain` | admin | Retrain the classifier now |

## Dashboard, reports, notifications

| Method | Path | Who | Purpose |
|---|---|---|---|
| GET | `/dashboard` | any | Role-specific home screen data |
| GET | `/reports/overview` | team lead+ | KPIs: volumes, SLA compliance, MTTA, MTTR, CSAT |
| GET | `/reports/technician-workload` · `/by-service` · `/by-category` · `/by-priority` · `/by-status` · `/sla` · `/trend?days=` | team lead+ | Analytics |
| GET | `/reports/export.csv` | team lead+ | Incident register as CSV |
| GET | `/reports/knowledge` | team lead+ | Self-help: deflections, most viewed and least helpful articles |
| GET | `/notifications` · `/notifications/unread-count` | any | In-app inbox |
| POST | `/notifications/{id}/read` · `/notifications/read-all` | any | Mark read |
| POST | `/notifications/stream-ticket` | any | One-time ticket (60 s) for opening the live stream |
| GET | `/notifications/stream?ticket=` | ticket | Server-Sent Events: notifications, incident updates, major-incident banner |

## Catalogue, users, administration

| Method | Path | Who | Purpose |
|---|---|---|---|
| GET | `/services` · `/services/graph` · `/categories` · `/sla-policies` | any | Reference data; dependency graph with open-incident counts |
| GET | `/technicians` | technician+ | Roster with live workload |
| PUT | `/technicians/me/availability` | technician | `{available}` on/off duty |
| GET/POST | `/admin/users` · PUT `/admin/users/{id}` · POST `/admin/users/{id}/reset-password` | admin | User management (password or company-account-only) |
| POST | `/admin/users/{id}/role` | admin (MANAGER for manager role) | Change role; sessions are revoked |
| POST | `/admin/users/{id}/unlock` · `/reset-mfa` · `/revoke-sessions` | admin | Unlock, reset 2FA, sign out everywhere |
| POST | `/admin/users/import` | admin | CSV `email,name,role,department,skills` (skills `;`-separated) |
| POST/PUT | `/admin/services[/{code}]` · `/admin/categories[/{code}]` · `/admin/sla-policies[/{id}]` | admin | Configuration |
| GET/PUT | `/admin/settings` | admin | Strategies, auto-assignment/escalation, allowed e-mail domains, self-registration, Google auto-provisioning, 2FA for privileged roles |
| GET | `/admin/security` | admin | Security overview: privileged accounts without 2FA, locked accounts, recent events |
| GET | `/audit` | admin | Search audit log (`incident, actor, action, from, to, security=true`) |
| GET | `/admin/notifications/outbox` | admin | E-mail/SMS/in-app delivery log |
| POST | `/admin/sla-monitor/run` | admin | Run the SLA monitor immediately |
