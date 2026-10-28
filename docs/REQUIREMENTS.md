# Problem Analysis & Requirements Specification

## 1. Problem analysis

**Problem.** An organisation with hundreds of employees depends on many IT services. When one fails, reports
arrive by phone, e-mail and chat; nobody sees the whole picture. Critical incidents wait behind trivial ones,
technicians are overloaded unevenly, deadlines are missed unnoticed, five people report the same outage
separately, and there is no reliable history to learn from.

**Stakeholders**

| Stakeholder | Interest |
|---|---|
| Employees / customers | Report quickly, know what is happening, get a fix |
| Technicians | A clear, prioritised queue; context; past fixes for similar problems |
| Team leads | Balanced workload, nothing critical left unowned |
| System administrators | Configure services, categories, SLAs and rules without code changes |
| IT manager | Service health, SLA compliance, accountability, trends |
| Organisation | Less downtime, lower cost, auditability |

**Existing challenges → our response**

| Challenge (from the brief) | ResolveIQ feature |
|---|---|
| Delayed response | Instant auto-assignment, notifications, response SLA, escalation |
| Incorrect prioritisation | Explainable weighted priority algorithm |
| Inefficient technician allocation | 5 assignment strategies incl. smart recommendation with workload |
| SLA violations | Per-service SLA policies, warnings at 75 %, predictive risk, breach records |
| Poor visibility of service health | Role dashboards, service dependency map, reports |
| Duplicate reports | TF-IDF duplicate detection, pre-submit warning, auto-closure with the parent |
| Incomplete history | Immutable audit trail of every action with previous/new state |
| Recurring problems | Correlation across dependent services; suggested resolutions from history |

**Assumptions**

1. Every user has a unique organisational e-mail; employees may self-register, staff accounts are created by admins.
2. Services and their dependencies are maintained by administrators.
3. SLA clocks run 24×7 (business-hours calendars are a possible extension).
4. Real e-mail/SMS delivery is optional; without credentials the system simulates delivery and logs it.
5. One technician owns an incident at a time; team leads also handle incidents.
6. Times are stored in UTC and displayed in Indian Standard Time.

## 2. User roles

| Role | Inherits | Capabilities |
|---|---|---|
| Employee | — | Register/login, report, view own incidents, track status, add information, answer requests, verify/reopen, close, feedback |
| Technician | — | View queue and workload, acknowledge, investigate, notes (public/internal), request information, resolve, escalate, re-prioritise, link duplicates, toggle availability, knowledge-base suggestions |
| Team Lead | Technician | Assign/re-assign, recommendations, reports, first escalation level |
| System Administrator | Team Lead | Manage users, technicians, services, categories, SLA policies, strategies and settings; audit log; notification log |
| IT Manager | Administrator | Top escalation level; can create other managers |

## 3. Functional requirements

| ID | Requirement |
|---|---|
| FR-01 | Users authenticate with e-mail and password; employees can self-register |
| FR-02 | An employee reports an incident with title, description, affected service, impact, urgency, affected users and optional category |
| FR-03 | The system classifies the incident (manual or AI-suggested category) |
| FR-04 | The system calculates priority P1–P4 from impact, urgency, business criticality and affected users, with an explanation |
| FR-05 | The system determines the applicable SLA (service-specific or default) and calculates response and resolution deadlines |
| FR-06 | The system recommends and (optionally) automatically assigns a technician using the configured strategy |
| FR-07 | Team leads can assign or re-assign manually or by any strategy |
| FR-08 | Technicians acknowledge, start, add notes, request information, resolve with resolution details |
| FR-09 | Reporters answer information requests, verify or reopen resolutions, close, and give 1–5 feedback |
| FR-10 | The lifecycle enforces valid transitions only (State pattern) |
| FR-11 | The SLA monitor continuously detects approaching deadlines and breaches and records them |
| FR-12 | Unacknowledged or overdue high-priority incidents escalate Team Lead → System Admin → IT Manager |
| FR-13 | Relevant people are notified in-app (live), by e-mail and (for critical events) SMS |
| FR-14 | Every action is recorded in an immutable audit trail with timestamp, user, action, incident, previous and new state, description |
| FR-15 | Incidents can be searched and filtered by 13 criteria with paging and sorting |
| FR-16 | Reports: overview KPIs, technician workload, by service/category/priority/status, SLA performance, trends, CSV export |
| FR-17 | Possible duplicates are detected before and after submission; confirmed duplicates close with their parent |
| FR-18 | Correlated incidents are grouped under a probable root-cause service |
| FR-19 | Suggested resolutions from similar past incidents are offered to technicians |
| FR-20 | Predictive SLA risk is shown for active incidents |
| FR-21 | Administrators manage users, services (with dependencies), categories, SLA policies, strategies and settings at runtime |

## 4. Non-functional requirements

| ID | Category | Requirement |
|---|---|---|
| NFR-01 | Security | BCrypt passwords, JWT, role-based and row-level authorisation, lockout, input validation (see TECHNOLOGY.md) |
| NFR-02 | Maintainability | Layered, capability-oriented packages; algorithms behind interfaces; ≥ 75 % test coverage |
| NFR-03 | Extensibility | New categories, SLA policies and strategies without code changes or redeploy |
| NFR-04 | Reliability | Transactions; optimistic locking; notification failures never break the business operation; SLA monitor survives failed runs |
| NFR-05 | Performance | Typical API responses < 300 ms on the demo data set; indexed search columns |
| NFR-06 | Scalability | Stateless API; horizontal scaling path documented |
| NFR-07 | Usability | Distinct UI per role; only valid actions offered (`availableActions`); clear error messages |
| NFR-08 | Auditability | Append-only audit records that cannot be updated through the application |
| NFR-09 | Portability | Runs with only Java 21 (H2) or in Docker with PostgreSQL |
| NFR-10 | Observability | Health and metrics endpoints (Actuator); structured logs for SLA monitor and deliveries |

## 5. Business rules

| ID | Rule |
|---|---|
| BR-INC-1 | Title, description, affected service, impact and urgency are mandatory; at least one affected user |
| BR-INC-2 | Only the assigned technician may acknowledge an incident |
| BR-INC-3 | Only team leads, administrators and managers (or the automation) may assign |
| BR-INC-4 | Only the reporter (or a supervisor) may verify, reopen or provide requested information |
| BR-INC-5 | Feedback can be given only by the reporter, only once, only after resolution, rating 1–5 |
| BR-INC-6 | Employees see only their own incidents and never see internal notes |
| BR-INC-7 | Closed incidents are terminal (no notes or transitions) |
| BR-INC-8 | A near-identical report from the same reporter within 72 h is refused unless explicitly acknowledged |
| BR-PRI-1 | Priority = weighted score (see ALGORITHMS.md); thresholds 48 / 24 / 10 |
| BR-PRI-2 | A manual priority override requires a reason and is audited |
| BR-PRI-3 | Security-sensitive categories are never below P2 |
| BR-SLA-1 | A service-specific SLA policy overrides the default policy for the same priority |
| BR-SLA-2 | Each priority must always have an active default policy |
| BR-SLA-3 | The resolution clock pauses while awaiting the reporter's information |
| BR-SLA-4 | Warnings are raised at the policy's threshold (default 75 %); each warning and breach is recorded once |
| BR-ASG-1 | Off-duty or deactivated technicians cannot be assigned |
| BR-ASG-2 | A technician at capacity cannot receive more work (default 5 active, team leads 3) |
| BR-ESC-1 | Escalation moves one level at a time with a cool-down of one response window |
| BR-USR-1 | Passwords: 8–72 characters with at least one letter and one digit |
| BR-USR-2 | Accounts lock for 15 minutes after 5 consecutive failed logins |
| BR-USR-3 | Administrators cannot deactivate themselves; only a manager can create a manager |
| BR-CAT-1 | Service dependencies must not form a cycle |
