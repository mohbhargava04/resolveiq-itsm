# Team Guide — ownership, demo script, roadmap

The brief says every member must present a module in the video and understand the whole system. Each person
owns one module below: read its files, run its tests, and be ready to explain the "talking points".

## Module ownership

### Member 1 — Domain model, lifecycle & OOP design
- **Files:** `incident/domain/*` (`Incident`, `IncidentState`, `AbstractIncidentState`, `IncidentStates`),
  `user/domain/*`, `common/BaseEntity`, `common/exception/*`
- **Tests:** `IncidentLifecycleTest`, `UserAndCatalogRulesTest`
- **Talking points:** why `TeamLead extends Technician` (is-a); why the incident has no setters; how the State
  pattern makes `NEW → CLOSED` impossible; composition (SLA record, notes) vs association (reporter, service);
  the custom exception hierarchy and how it becomes HTTP errors; domain events.

### Member 2 — Priority, SLA, assignment & escalation
- **Files:** `priority/*`, `sla/*`, `assignment/*`, `escalation/*`
- **Tests:** `PriorityStrategyTest`, `SlaRecordTest`, `AssignmentStrategyTest`, `EscalationChainTest`
- **Talking points:** the priority formula and thresholds (ALGORITHMS.md §1); Strategy pattern and switching
  strategies at runtime; Template Method in `AbstractAssignmentStrategy`; SLA pause and the 30-second monitor;
  Chain of Responsibility for escalation.

### Member 3 — Intelligent features, notifications & frontend
- **Files:** `intelligence/*`, `notification/*` (Observer, Factory, Adapter), `live/*`, `frontend/` (see `docs/DESIGN.md`)
- **Also:** `knowledge/*` (self-help deflection), `notification/inbound/*` (two-way e-mail), the major-incident
  and conversation screens, accessibility
- **Tests:** `IntelligenceTest`, `AttachmentAndKnowledgeIntegrationTest`, `MajorIncidentAndEmailIntegrationTest`,
  frontend `npm test`
- **Talking points:** TF-IDF + synonyms for duplicates; knowledge-base deflection reusing the same engine; Naive Bayes classifier and calibration; correlation
  over the dependency graph; predictive SLA risk; the Observer → Policy → Factory → Channel → Adapter chain for
  notifications; SSE live updates; three role-specific UIs.

### Member 4 — Persistence, security, API, testing & DevOps
- **Files:** `db/migration/*`, `*Repository`, `security/*` (sessions, MFA, OAuth, rate limiting, crypto),
  `attachment/*`, `web/*`, `config/*`, `Dockerfile`, `docker-compose.yml`, `render.yaml`, `.github/*`
- **Tests:** `SecurityIntegrationTest`, `SessionSecurityIntegrationTest`, `MfaAndPasswordResetIntegrationTest`,
  `GoogleSignInIntegrationTest`, `RateLimitAndHeadersIntegrationTest`, `IncidentWorkflowIntegrationTest`
- **Talking points:** ER design and single-table inheritance; Flyway migrations V1 → V2; access token + rotating
  refresh cookie and why (SECURITY.md); token version for instant revocation; Google sign-in with PKCE and
  company domains; TOTP built from RFC 6238; rate limiting before Spring Security; safe uploads; OWASP Top 10:2025
  mapping; 129 backend tests on H2 and PostgreSQL; CI, CodeQL, Dependabot; Docker and Render.

## Demo script (15–20 minutes)

Before recording: `ITSM_RESET_DEMO=true ./mvnw spring-boot:run` so the live scenario is fresh, then open http://localhost:8080. Record in light mode and switch to dark once to show the theme toggle.

1. **Intro (each member, 30 s)** — name and module.
2. **Workflow 1 — report → prioritise → assign → notify** (Member 3/1)
   - Log in as **Priya** (employee). Start typing "Cannot log in to HR portal… SSO password". Show the live AI
     category suggestion, priority preview and duplicate warning. Submit.
   - Show the result: AI category, P-level with the explanation, SLA deadlines, auto-assigned technician.
   - Switch to the assignee: the notification has appeared live.
3. **Workflow 2 — investigate → SLA → resolve → notify** (Member 2/1)
   - As the technician: acknowledge, start, add an internal note, request information (SLA shows *paused*).
   - As Priya: answer. As the technician: open *suggested resolutions*, resolve with root cause.
   - As Priya: notification "resolved" → verify → close → 5-star feedback. Open the **audit trail**.
4. **Workflow 3 — duplicates, correlation, escalation** (Member 3/2)
   - As **Neha** (team lead): the dashboard shows the **Core Database correlation cluster** (Payment, HR, ERP,
     DB alert), the duplicate "Unable to make payment" linked to INC-1037, and the unassigned ERP incident with
     ranked recommendations — assign it.
   - The Wi-Fi **P1 that Rahul never acknowledged** has escalated Team Lead → Admin → Manager; show the
     escalation in its audit trail and the SMS/e-mail entries in the admin **notification log**.
   - Resolve INC-1037: the duplicate closes automatically and its reporter is notified.
5. **Major incident** (Member 3/2) — on the command centre's Core Database cluster, click **Declare major
   incident**: the related reports are linked and a red banner appears for everyone. Post an update
   (*Cause identified*). As **Ava**, see the banner and press **I'm affected too**. Resolve the major incident:
   every linked incident resolves with it.
6. **Self-help and e-mail** (Member 3) — as Ava, start reporting "account locked after password change": a
   knowledge article appears; open it and click **This fixed it** (no ticket created; *Solved by self-help* goes
   up). As admin, **E-mail & SMS → Received e-mail → Simulate**: a reply tagged `[INC-…]` answers a technician's
   question and restarts the SLA.
7. **Security** (Member 4) — as admin, **Profile & security**: turn on two-step verification by scanning the QR
   code, sign out, sign in again with the code. Show *where you're signed in*. **Admin → Security** overview.
   Briefly show SECURITY.md's OWASP table and a security test.
8. **Administration** (Member 4) — switch assignment strategy to Load-based; add a new category "IoT Devices"
   and show the classifier recognising it immediately; change a user's role; CSV import; allowed domains.
9. **Engineering** (Member 4) — `./mvnw test` (129) and `npm test` (16) passing, CI and CodeQL on GitHub,
   Docker Compose with PostgreSQL, the deployed Render link.
10. **Manager reports** — KPIs (MTTA, MTTR, SLA compliance, CSAT), self-help report, workload, trends, CSV export.

## Roadmap to the 22 November deadline

| Week | Dates | Deliverables | Status |
|---|---|---|---|
| 1 | 23–29 Oct | Backend: domain, patterns, security, SLA, escalation, 7 intelligent features, 80 tests, docs | **Done** |
| 2 | 29 Oct–5 Nov | Frontend: design system, light/dark themes, login, employee portal, technician workbench, command centre, service map, reports, all admin screens, live notifications, ⌘K | **Done** |
| 3 | 3–12 Nov | Security hardening (rotating sessions, Google sign-in, 2FA, password reset, rate limiting, strict CSP, CodeQL/Dependabot, OWASP write-up); knowledge base & self-help, major incidents, attachments, conversation, two-way e-mail, bulk actions, profile & sessions, admin security; new sign-in page; accessibility pass (0 axe violations); frontend tests; Docker + Render deployment | **Done** |
| 4 | 13–19 Nov | Innovation extras (LLM adapter for classification/resolutions, chatbot/voice reporting), deploy to Render, polish from team feedback | Next |
| — | 19–22 Nov | PDF report (assembled from these docs), screenshots, video recording, upload | |

## Git workflow

- `main` is always green (CI). Work on short branches: `feature/frontend-login`, `feature/admin-screens`.
- Each member commits their own module so contributions are visible in the history.
- Never commit `backend/data/` (local database) or secrets.

## Before submitting: fill in the placeholders

- Group number and teammates' names and IDs: `frontend/src/lib/project.ts` (shown on the sign-in page; then
  run `npm run build:backend`) and the table at the top of `README.md`.
- Module owners above: replace "Member 2/3/4" with names.
