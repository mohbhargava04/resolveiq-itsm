# Testing

## Strategy

| Level | What | Tools | Count |
|---|---|---|---|
| Backend unit | Domain rules, state machine, SLA clock, priority/assignment strategies, escalation chain, NLP, Naive Bayes, correlation, factories; security building blocks (TOTP, AES-GCM, token bucket, password policy, file-type detection, reply parsing, startup safety check) | JUnit 5, AssertJ, Mockito; plain `new` objects, no Spring | 69 |
| Backend integration | The real application over HTTP (MockMvc): sessions and tokens, 2FA, password reset, Google sign-in (fake identity provider), rate limits and headers, the three demo workflows, attachments, knowledge base, major incidents, bulk actions, inbound e-mail, user administration, search, reports | Spring Boot Test, H2 in-memory, controllable clock | 60 |
| Frontend unit & component | API client session handling (silent refresh, single-flight, CSRF header), formatting, UI building blocks, the sign-in page (demo accounts, errors, 2FA step) | Vitest, Testing Library, jsdom; `fetch` replaced by a fake server per test | 16 |
| Accessibility | 23 screens × light and dark themes, every role | axe-core (WCAG 2.1 A/AA + best practices) driven by Playwright | 0 violations |
| **Total automated** | | | **145** (+ accessibility scan) |

- Time-dependent rules use an injected `Clock`; tests replace it with a `MutableClock` and jump forward
  (e.g. 6 minutes past a P1 response deadline) instead of waiting.
- Integration tests run against the seeded demo organisation; each test runs in a rolled-back transaction.
- The same suite also passes against **PostgreSQL 16** (`./mvnw test -Dspring.datasource.url=jdbc:postgresql://…`).
- Coverage (JaCoCo): **82 % of lines**; core packages 84–97 %. Report: `backend/target/site/jacoco/index.html`
  (after `./mvnw verify`). The lowest package is the real Google adapter, whose HTTP calls to Google are
  replaced in tests by a fake implementation of the same `IdentityProvider` interface.
- GitHub Actions runs both suites on every push; CodeQL scans Java and TypeScript for security bugs.

```sh
cd backend
./mvnw test                                  # all backend tests
./mvnw test -Dtest=IncidentLifecycleTest     # one class
cd ../frontend
npm test                                     # all frontend tests (npm run test:watch while coding)
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

## Week 3 test cases: security, collaboration and administration

| ID | Cat. | Scenario | Expected result | Test |
|---|---|---|---|---|
| SEC-01 | N | Sign in | Refresh cookie HttpOnly, SameSite=Strict, path `/api/auth`; access token valid 15 min | `SessionSecurityIntegrationTest` |
| SEC-02 | E | Stolen refresh token replayed | Rotation on each refresh; replay after the grace window revokes the whole session | 〃 |
| SEC-03 | A | Cross-site refresh (CSRF) | Refresh/sign-out without `X-Requested-With` refused | 〃 |
| SEC-04 | N | Sign out | Access token stops working immediately | 〃 |
| SEC-05 | R | Account deactivated | Existing tokens rejected on the next request | 〃 |
| SEC-06 | R | Role changed | New role applies at once; role inside an old token ignored | 〃 |
| SEC-07 | I | Wrong token type / forged token | 2FA challenge or forged JWT cannot call the API | 〃 |
| SEC-08 | N | Sessions list | Own devices listed; others can be signed out | 〃 |
| SEC-09 | R | Password changed | Other devices signed out; fresh session returned | 〃 |
| SEC-10 | N/E | Two-step verification | Setup needs a valid code; sign-in asks for a code; wrong and replayed codes refused | `MfaAndPasswordResetIntegrationTest` |
| SEC-11 | R | Recovery codes, lockout | A recovery code works once; wrong codes count towards lockout | 〃 |
| SEC-12 | R | Forgot password | Same answer for unknown e-mails; link single-use, 30 min | 〃 |
| SEC-13 | R | Registration domains | Only company domains; can be switched off | 〃 |
| SEC-14 | A | Company-account-only user | No password works | 〃 |
| SEC-15 | N | Admin security overview | Shows 2FA gaps; admin can reset someone's 2FA | 〃 |
| SEC-16 | B | Brute force | 11th sign-in in a minute → 429 with `Retry-After` | `RateLimitAndHeadersIntegrationTest` |
| SEC-17 | N | Security headers | Strict CSP without inline scripts, nosniff, Permissions-Policy, COOP/CORP | 〃 |
| SEC-18 | A | Live stream | Needs a one-time ticket; tokens never in URLs; ticket cannot be reused | 〃 |
| SEC-19 | N | Demo mode | Demo accounts listed only in demo mode | 〃 |
| SSO-01 | N | Start Google sign-in | Redirect has state, PKCE (S256), nonce, domain hint; state cookie HttpOnly | `GoogleSignInIntegrationTest` |
| SSO-02 | R | Pre-registered technician | Linked on first Google sign-in, keeps the admin-given role | 〃 |
| SSO-03 | R | New company person | Becomes Employee, asked to complete profile | 〃 |
| SSO-04 | I | Personal / unverified / forged | Refused | 〃 |
| SSO-05 | E | Account takeover attempt | A second Google account cannot claim a linked account | 〃 |
| SSO-06 | R | Auto-provisioning off | Only pre-registered people get in | 〃 |
| SSO-07 | E | Open redirect | Only same-site paths accepted after sign-in | 〃 |
| ATT-01 | I | Disguised and dangerous files | Real PNG accepted; HTML renamed `.png`, SVG and EICAR refused | `AttachmentAndKnowledgeIntegrationTest` |
| ATT-02 | A | Download privacy | Only people who can see the incident; served with nosniff and sandbox CSP | 〃 |
| KB-01 | A | Knowledge search | Finds by meaning; internal runbooks hidden from employees | 〃 |
| KB-02 | N | Self-help while reporting | Matching articles suggested; "this fixed it" counts a deflection | 〃 |
| KB-03 | R | Voting | One vote per person; changing a vote moves it | 〃 |
| KB-04 | A | Writing articles | Technicians draft from a resolved incident; employees cannot write | 〃 |
| MAJ-01 | N | Declare major incident | Team lead declares from a correlation cluster, linking related incidents | `MajorIncidentAndEmailIntegrationTest` |
| MAJ-02 | N | Major incident lifecycle | Updates reach everyone affected; "I'm affected too"; resolving the major resolves every linked incident | 〃 |
| BULK-01 | R | Bulk actions | One action on many incidents; each item succeeds or fails on its own | 〃 |
| MAIL-01 | N/A | E-mail reply | Answers the open question and restarts the SLA; forged secret and outsiders refused | 〃 |
| MAIL-02 | N | New e-mail | Opens an incident with the service guessed from the text | 〃 |
| ADM-01 | I | CSV import | People pre-registered with roles; bad rows reported, not fatal | `UserAdministrationIntegrationTest` |
| ADM-02 | R | Role-change rules | Technician with history cannot be demoted; nobody changes their own role | 〃 |
| ADM-03 | N | Unlock, sign out everywhere | Locked account can sign in again; all its sessions end | 〃 |
| ADM-04 | I | Allowed domains | Validated and tidied | 〃 |
| UNIT-01–08 | N/B/E | Security building blocks | RFC 6238 test vector, ±1 step drift only; AES-GCM tamper detection; token-bucket refill; password policy; magic-byte detection; reply parsing; domain lists; startup refuses dev secrets | `SecurityBuildingBlocksTest` |
| FE-01–07 | N/E | API client | Bearer header, nothing in localStorage; 401 → one refresh → retry; concurrent refreshes share one request; CSRF header on cookie endpoints; failed refresh ends the session; problem JSON → `ApiError`; query strings | `frontend/src/lib/api.test.ts` |
| FE-08–10 | N/B | Formatting | Durations, SLA time left/overdue, initials, labels | `format.test.ts` |
| FE-11–12 | N | UI building blocks | Skill chips add/dedupe/remove by keyboard; full-page empty state is an `h1` | `ui.test.tsx` |
| FE-13–16 | N/I | Sign-in page | Demo accounts folded until opened, one-click sign-in; wrong password message; 2FA step accepts digits only and needs six; Google button explains when not configured | `Login.test.tsx` |

