# Testing

## Strategy

| Level | What | Tools | Count |
|---|---|---|---|
| Unit | Domain rules, state machine, SLA clock, priority/assignment strategies, escalation chain, NLP, Naive Bayes, correlation, factories | JUnit 5, AssertJ, Mockito; plain `new` objects, no Spring | 61 |
| Integration | The real application over HTTP (MockMvc): security, the three demo workflows, search, validation, admin configuration, reports | Spring Boot Test, H2 in-memory, controllable clock | 19 |
| **Total** | | | **80** |

- Time-dependent rules use an injected `Clock`; tests replace it with a `MutableClock` and jump forward
  (e.g. 6 minutes past a P1 response deadline) instead of waiting.
- Integration tests run against the seeded demo organisation; each test runs in a rolled-back transaction.
- The same suite also passes against **PostgreSQL 16** (`./mvnw test -Dspring.datasource.url=jdbc:postgresql://…`).
- Coverage (JaCoCo): **78 % of lines**; core packages 84–97 %. Report: `backend/target/site/jacoco/index.html`.
- GitHub Actions runs the full suite on every push.

```sh
cd backend
./mvnw test                                  # all tests
./mvnw test -Dtest=IncidentLifecycleTest     # one class
```

## Documented test cases

Category key — **N** normal operation · **B** boundary · **I** invalid input · **E** exception · **A** authorisation · **R** business rule

| ID | Cat. | Scenario | Steps / input | Expected result | Test |
|---|---|---|---|---|---|
| TC-01 | N | Full lifecycle | assign → acknowledge → start → resolve → verify → close | Status CLOSED; states visited in order; SLA MET | `IncidentLifecycleTest.happyPath` |
| TC-02 | E | Skip states | close a NEW incident | `InvalidStateTransitionException`, status unchanged | `cannotCloseNewIncident` |
| TC-03 | E | Resolve too early | resolve an ASSIGNED incident | `InvalidStateTransitionException` | `cannotResolveFromAssigned` |
| TC-04 | A | Wrong technician | another technician / reporter acknowledges | `UnauthorizedOperationException` | `onlyAssigneeAcknowledges` |
| TC-05 | A | Employee assigns | employee calls assign | `UnauthorizedOperationException` | `employeeCannotAssign` |
| TC-06 | R | Off-duty technician | assign to technician who went off duty | `BusinessRuleViolationException` | `offDutyTechnicianRejected` |
| TC-07 | R | SLA pause | request info at 10 min, answer at 40 min | Status AWAITING_INFO→IN_PROGRESS; SLA PAUSED meanwhile; deadline +30 min | `awaitingInfoPausesSla` |
| TC-08 | N | Reopen | reporter reopens a RESOLVED incident | IN_PROGRESS; resolved time cleared | `reopen` |
| TC-09 | R | Feedback rules | before resolution; by technician; rating 6; twice | Rejected each time; one valid rating accepted | `feedbackRules` |
| TC-10 | R | Terminal state | add note to CLOSED (duplicate) incident | Rejected; no allowed transitions | `closedIsTerminal` |
| TC-11 | R | Self-duplicate | link incident as duplicate of itself | `BusinessRuleViolationException` | `notDuplicateOfItself` |
| TC-12 | I | Invalid incident | blank description; 0 affected users | `InvalidIncidentException` | `invalidInput` |
| TC-13 | A | Internal notes | employee writes internal note | Rejected; technician can | `internalNotes` |
| TC-14 | N | SLA deadlines | start P1 policy 5/30 | Response due +5, resolution due +30 | `SlaRecordTest.deadlines` |
| TC-15 | B | SLA boundary | evaluate exactly at deadline, then +1 s | No breach, then RESOLUTION breach | `boundary` |
| TC-16 | B | SLA warning | 67 %, 77 %, 80 % of window | Warning exactly once at 77 %; status AT_RISK | `warningOnce` |
| TC-17 | R | Response breach | no acknowledgement after 6 min | One RESPONSE breach, not repeated | `responseBreach` |
| TC-18 | N | Brief scenario priority | payment outage, CRITICAL×CRITICAL×CRITICAL, 500 users | Score 96 → P1 with rationale | `PriorityStrategyTest.paymentOutageIsP1` |
| TC-19 | B | Priority thresholds | scores 48, 36, 24, 10, 8, 1 | P1, P2, P2, P3, P4, P4 | `thresholds` (6 cases) |
| TC-20 | R | Security floor | LOW/LOW/LOW security incident | P2 (both strategies) | `securityFloor` |
| TC-21 | N | Skill-based assignment | DB incident; network/DB technicians | Only DB-skilled, least busy first | `AssignmentStrategyTest.skillBased` |
| TC-22 | N | Load-based assignment | workloads 0, 3, 1 | Idle technician chosen | `loadBased` |
| TC-23 | B | Capacity/availability | 5/5 active, off duty, 4/5 active | Only the 4/5 technician eligible | `capacityAndAvailability` |
| TC-24 | N | Smart recommendation | skilled+experienced vs idle unskilled | Skilled senior first, with reasons | `smart` |
| TC-25 | N | Duplicate detection | brief's three payment reports + printer jam | Payment reports similar (> 0.5); printer not | `IntelligenceTest.briefDuplicateExample` |
| TC-26 | N | AI categorisation | "cannot log in, SSO… password" | AUTHENTICATION, calibrated confidence, signal "login" | `classifier` |
| TC-27 | N | Correlation | failures on Payment, HR, ERP (+ unrelated Wi-Fi) | Root = Core Database (not Core Network) | `correlation` |
| TC-28 | R | Escalation chain | unacknowledged P1 at 4, 6, 8, 11, 16 min | none, Team Lead, cool-down, Admin, IT Manager | `EscalationChainTest.climbsChain` |
| TC-29 | N | User factory | create TEAM_LEAD / EMPLOYEE | Correct subclasses; password BCrypt-hashed | `UserAndCatalogRulesTest.factory` |
| TC-30 | I | Weak password / no skills | "short1", "allletters", technician without skills | Rejected | `validation` |
| TC-31 | R | Lock-out (domain) | 5 failed logins | Locked for 15 min, then unlocked | `lockout` |
| TC-32 | R | Dependency cycle | A→B→C, then C→A | Rejected | `noCycles` |
| TC-33 | N | Login | valid credentials | 200, JWT, role, skills | `SecurityIntegrationTest.login` |
| TC-34 | I | Invalid login | wrong password; unknown e-mail | 401 INVALID_CREDENTIALS, identical messages | `invalidLogin` |
| TC-35 | E | Brute force | 5 wrong passwords, then the right one | 423 ACCOUNT_LOCKED, still locked | `lockout` |
| TC-36 | A | No token | GET /api/incidents | 401 UNAUTHENTICATED | `unauthenticated` |
| TC-37 | I | Registration | valid; duplicate e-mail; bad e-mail & password | 201 EMPLOYEE; 409; 400 with field errors | `register` |
| TC-38 | A | Role-based access | technician→admin/reports, employee→roster/assign, lead→reports, manager→audit | 403 / 403 / 403 / 403 / 200 / 200 | `roles` |
| TC-39 | N | **Workflow 1** | employee reports without category | Auto-classified, prioritised, SLA set, ASSIGNED; assignee and reporter notified | `IncidentWorkflowIntegrationTest.workflowReportToAssignment` |
| TC-40 | N | **Workflow 2** | ack → start → internal note → request info → reporter answers → resolve → verify → close | All transitions audited with previous/new state; reporter notified; internal note hidden | `workflowInvestigationToClosure` |
| TC-41 | R | Duplicate refused | same reporter submits the same report twice | 409 DUPLICATE_INCIDENT with matches; accepted when acknowledged | `duplicateFromSameReporterRejected` |
| TC-42 | N | **Workflow 3** | related payment report; correlations; resolve parent | Flagged duplicate; cluster root CORE_DB; child auto-closed as confirmed duplicate | `duplicatesCorrelatedAndClosedWithParent` |
| TC-43 | R | SLA breach & escalation | new P1, clock +6 min, run monitor | Response breached, status BREACHED, escalated to Team Lead, lead notified | `slaBreachAndEscalation` |
| TC-44 | N | Search | priority, status, service, text, date range, SLA status | Correct subsets | `search` |
| TC-45 | I | Invalid report | blank title, 0 users; unknown service; invalid enum | 400 field errors; 404 SERVICE_NOT_FOUND; 400 | `validation` |
| TC-46 | E | Invalid transition via API | verify an AWAITING_INFO incident | 409 INVALID_STATE_TRANSITION with currentState | `invalidTransitionApi` |
| TC-47 | N | Change strategy at runtime | admin sets LOAD; invalid value | New incident uses Load-based; 422 for unknown | `switchStrategy` |
| TC-48 | N | New category at runtime | admin adds "IoT Devices" | Classifier immediately suggests IOT | `newCategoryLearned` |
| TC-49 | N | Reports | overview, workload, by service, trend, dashboard, CSV | All return data; CSV downloads | `reports` |

Additional tests (not numbered) cover the ITIL matrix, user multiplier boundaries (7 cases), round-robin,
experience-based ranking, seniority fit, synonym normalisation, service affinity, medians, manual escalation,
event recording, available actions per role, row-level security and technician availability.
