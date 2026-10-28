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
- **Files:** `intelligence/*`, `notification/*` (Observer, Factory, Adapter), `live/*`, `frontend/`
- **Tests:** `IntelligenceTest`
- **Talking points:** TF-IDF + synonyms for duplicates; Naive Bayes classifier and calibration; correlation
  over the dependency graph; predictive SLA risk; the Observer → Policy → Factory → Channel → Adapter chain for
  notifications; SSE live updates; three role-specific UIs.

### Member 4 — Persistence, security, API, testing & DevOps
- **Files:** `db/migration/V1__initial_schema.sql`, `*Repository`, `security/*`, `web/*`,
  `config/DemoDataSeeder`, `Dockerfile`, `docker-compose.yml`, `.github/workflows/ci.yml`
- **Tests:** `SecurityIntegrationTest`, `IncidentWorkflowIntegrationTest`
- **Talking points:** ER design and single-table inheritance; Flyway; JWT + BCrypt + role hierarchy + lockout;
  row-level security; RFC 7807 errors; 80 tests on H2 and PostgreSQL; CI; Docker.

## Demo script (15–20 minutes)

Before recording: `ITSM_RESET_DEMO=true ./mvnw spring-boot:run` so the live scenario is fresh.

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
5. **Administration** (Member 4) — switch assignment strategy to Load-based; add a new category "IoT Devices"
   and show the classifier recognising it immediately; SLA policy editor; user management.
6. **Engineering** (Member 4) — Swagger UI, `./mvnw test` (80 passing), CI on GitHub, Docker Compose with PostgreSQL.
7. **Manager reports** — KPIs (MTTA, MTTR, SLA compliance, CSAT), workload, trends, CSV export.

## Roadmap to the 22 November deadline

| Week | Dates | Deliverables | Status |
|---|---|---|---|
| 1 | 23–29 Oct | Backend: domain, patterns, security, SLA, escalation, 7 intelligent features, 80 tests, docs | **Done** |
| 2 | 30 Oct–5 Nov | Frontend foundation: design system, login, role shells, employee portal, live notifications | Next |
| 3 | 6–12 Nov | Technician workbench (queue, kanban, incident view), manager command centre (charts, service map), admin screens | |
| 4 | 13–19 Nov | Innovation extras (LLM adapter for classification/resolutions, chatbot/voice reporting), cloud deployment, polish | |
| — | 19–22 Nov | PDF report (assembled from these docs), screenshots, video recording, upload | |

## Git workflow

- `main` is always green (CI). Work on short branches: `feature/frontend-login`, `feature/admin-screens`.
- Each member commits their own module so contributions are visible in the history.
- Never commit `backend/data/` (local database) or secrets.
