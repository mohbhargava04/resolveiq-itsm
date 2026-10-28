# REST API Reference

Base URL `http://localhost:8080/api`. All endpoints except login/register require
`Authorization: Bearer <token>`. Interactive documentation: **`/swagger-ui.html`**.
Errors use RFC 7807 problem JSON with a stable `code` (e.g. `INVALID_STATE_TRANSITION`, `DUPLICATE_INCIDENT`).

## Authentication

| Method | Path | Who | Purpose |
|---|---|---|---|
| POST | `/auth/login` | public | E-mail + password → JWT |
| POST | `/auth/register` | public | Self-register as an employee |
| GET | `/auth/me` | any | Current user |
| POST | `/auth/change-password` | any | Change own password |

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
| GET | `/notifications` · `/notifications/unread-count` | any | In-app inbox |
| POST | `/notifications/{id}/read` · `/notifications/read-all` | any | Mark read |
| GET | `/notifications/stream?access_token=` | any | Server-Sent Events: live notifications and incident updates |

## Catalogue, users, administration

| Method | Path | Who | Purpose |
|---|---|---|---|
| GET | `/services` · `/services/graph` · `/categories` · `/sla-policies` | any | Reference data; dependency graph with open-incident counts |
| GET | `/technicians` | technician+ | Roster with live workload |
| PUT | `/technicians/me/availability` | technician | `{available}` on/off duty |
| GET/POST | `/admin/users` · PUT `/admin/users/{id}` · POST `/admin/users/{id}/reset-password` | admin | User management |
| POST/PUT | `/admin/services[/{code}]` · `/admin/categories[/{code}]` · `/admin/sla-policies[/{id}]` | admin | Configuration |
| GET/PUT | `/admin/settings` | admin | Active priority/assignment strategy, auto-assignment, auto-escalation |
| GET | `/audit` | admin | Search audit log (`incident, actor, action, from, to`) |
| GET | `/admin/notifications/outbox` | admin | E-mail/SMS/in-app delivery log |
| POST | `/admin/sla-monitor/run` | admin | Run the SLA monitor immediately |
