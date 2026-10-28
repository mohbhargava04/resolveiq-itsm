# ResolveIQ — Intelligent IT Service Management & Incident Resolution System

CS F213 / MAC F212 Object-Oriented Programming · Semester I, 2026-27 · Group project

ResolveIQ manages the complete lifecycle of IT incidents — **Report → Classify → Prioritise → Assign →
Investigate → Resolve → Verify → Close** — with automatic prioritisation, SLA monitoring, escalation,
notifications, a full audit trail and seven intelligent features.

| | |
|---|---|
| **Backend** | Java 21 · Spring Boot 3.5 · Spring Security (JWT) · JPA/Hibernate · Flyway |
| **Database** | PostgreSQL 16 (production) · H2 (zero-setup development) |
| **Frontend** | React + TypeScript (in progress — see `docs/TEAM_GUIDE.md`) |
| **Quality** | 80 automated tests (unit + integration), 78% line coverage, GitHub Actions CI |
| **API docs** | Swagger UI at `http://localhost:8080/swagger-ui.html` |

---

## Run it

**Prerequisite:** Java 21 (`java -version`). Nothing else — the Maven wrapper downloads Maven, and the
development profile uses an embedded database.

```sh
cd backend
./mvnw spring-boot:run          # first run downloads dependencies (~2 min)
```

Open **http://localhost:8080/swagger-ui.html**, call `POST /api/auth/login`, click **Authorize** and paste the token.

```sh
./mvnw test                     # run all 80 tests
ITSM_RESET_DEMO=true ./mvnw spring-boot:run   # rebuild the live demo scenario (use before recording)
docker compose up --build       # production-like stack with PostgreSQL (from the repo root)
```

### Demo accounts — password `Password@123` for all

| Role | Login | What they see |
|---|---|---|
| Employee | `ava@resolveiq.test` | Own incidents; one awaiting her answer, one awaiting her verification |
| Employee | `priya@`, `rohan@`, `arjun@`, `sneha@` | Other reporters |
| Technician | `rahul@resolveiq.test` | An unacknowledged P1 (Wi-Fi) that is being escalated |
| Technician | `meera@`, `kabir@`, `isha@`, `dev@` | Different skills, workloads and seniority |
| Team Lead | `neha@resolveiq.test`, `vikram@` | Assignment, recommendations, reports |
| System Admin | `admin@resolveiq.test` | Users, services, categories, SLA policies, strategies, audit log |
| IT Manager | `manager@resolveiq.test` | Everything, top of the escalation chain |

---

## How the brief's requirements are met

| Requirement | Where |
|---|---|
| 3+ user roles | 5 roles via a class hierarchy: `Employee`, `Technician` → `TeamLead`, `Administrator` → `ItManager` |
| Incident create / view / update / assign / resolve / close | `IncidentController`, `IncidentService`, `Incident` |
| Priority from impact, urgency, criticality, users | `WeightedScoringPriorityStrategy`, `ItilMatrixPriorityStrategy` |
| ≥ 2 assignment strategies | **5**: Skill, Load, Round-robin, Experience, Smart |
| SLA calculation, tracking, violation detection, warnings | `SlaRecord`, `SlaPolicyResolver`, `SlaMonitorService` (background, every 30 s) |
| ≥ 2 notification mechanisms | **3**: In-app (live via SSE), E-mail (SMTP adapter or simulated), SMS (Twilio adapter or simulated) |
| Complete audit history | `AuditTrailObserver` → immutable `AuditRecord` (who, when, action, previous → new state) |
| ≥ 5 search criteria | **13**: text, number, status, priority, category, service, technician, reporter, SLA status, escalated, open-only, date from/to |
| ≥ 2 intelligent features | **7**: duplicate detection, correlation, auto-escalation, smart recommendation, SLA risk prediction, auto-categorisation (Naive Bayes ML), suggested resolutions |
| ≥ 2 design patterns | **9**: Strategy, State, Observer, Factory, Adapter, Chain of Responsibility, Template Method, Repository, Builder |
| Persistence | PostgreSQL / H2 with Flyway-versioned schema |
| Security | BCrypt, JWT, role hierarchy, row-level access, lockout, validation, security headers |
| New categories without code changes | Categories are data; the classifier learns new ones from their keywords instantly |
| Invalid state transitions prevented | State pattern — e.g. `NEW → CLOSED` throws `InvalidStateTransitionException` |

## Documentation

| Document | Contents |
|---|---|
| [REQUIREMENTS](docs/REQUIREMENTS.md) | Problem analysis, stakeholders, functional/non-functional requirements, business rules |
| [ARCHITECTURE](docs/ARCHITECTURE.md) | Architecture & UML diagrams (class, state, sequence, use case), OOP concepts, design patterns |
| [ALGORITHMS](docs/ALGORITHMS.md) | Priority, assignment, SLA, escalation and every intelligent feature, with formulas |
| [DATABASE](docs/DATABASE.md) | ER diagram, schema, relationships, sample data |
| [TECHNOLOGY](docs/TECHNOLOGY.md) | Technology selection report (the 9 questions), why Spring Boot is still Java, scaling, security |
| [TESTING](docs/TESTING.md) | Test strategy and 49 documented test cases |
| [API](docs/API.md) | REST endpoint reference |
| [TEAM GUIDE](docs/TEAM_GUIDE.md) | Module ownership, demo script, roadmap |

## Repository layout

```
backend/            Spring Boot API (Java 21)
  src/main/java/com/resolveiq/
    incident/       aggregate root, State pattern lifecycle, events, search, service
    priority/       priority Strategy pattern
    assignment/     assignment strategies (Strategy + Template Method)
    sla/            SLA policies, SLA clock, background monitor
    escalation/     Chain of Responsibility escalation
    intelligence/   NLP, TF-IDF similarity, Naive Bayes, correlation, risk prediction
    notification/   Observer → channels (Factory) → gateways (Adapter)
    audit/          append-only audit trail (Observer, Builder)
    user/ catalog/ settings/ report/ security/ live/ web/ config/
  src/main/resources/db/migration/   Flyway SQL schema
  src/test/         unit and integration tests
docs/               design documentation (Mermaid diagrams render on GitHub)
legacy/             earlier prototype, kept for reference only
```
