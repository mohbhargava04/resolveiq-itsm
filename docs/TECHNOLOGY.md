# Technology Selection Report

## Stack at a glance

| Layer | Choice | Version |
|---|---|---|
| Language (backend) | Java | 21 (LTS) |
| Backend framework | Spring Boot (Web MVC, Data JPA, Security, Validation, Actuator, Mail) | 3.5 |
| Authentication | JWT (Nimbus JOSE via Spring OAuth2 Resource Server), BCrypt | — |
| ORM / migrations | Hibernate 6 / Flyway | 6.6 / 11 |
| Database | PostgreSQL (production), H2 in PostgreSQL mode (development & tests) | 16 / 2.3 |
| Real-time | Server-Sent Events (`SseEmitter`) | — |
| API documentation | springdoc-openapi (Swagger UI) | 2.8 |
| Testing | JUnit 5, AssertJ, Mockito, Spring MockMvc, JaCoCo coverage | — |
| Frontend | React + TypeScript + Vite (in progress) | 19 / 5 |
| DevOps | Maven wrapper, Docker (multi-stage), Docker Compose, GitHub Actions CI | — |
| Integrations | SMTP (any provider), Twilio SMS — both behind adapters with simulation fallbacks | — |

## The 9 questions from the brief

### 1. Why this programming language? — Java 21

- **It is the language of the course.** Every OOP concept we are assessed on — classes, abstract classes,
  interfaces, inheritance, polymorphism, encapsulation, exceptions, collections, generics — is expressed directly
  in Java, so the evaluator can see each concept in the code.
- **Strong static typing** catches errors at compile time; the compiler even checks that every event type is
  handled, because events are a `sealed interface` used in exhaustive pattern-matching `switch` statements.
- **Modern Java 21 features** let the design stay concise: records for immutable value objects (`Actor`,
  `PriorityAssessment`, all DTOs), sealed types, text blocks, `List.getFirst()`.
- **LTS release** (supported until 2031), mature tooling, and the dominant language for enterprise back ends —
  exactly the domain of this project.

### 2. Why this frontend technology? — React + TypeScript

- A **web** client works for every employee on any device with no installation, which an ITSM tool needs.
- Three very different role experiences (employee portal, technician workbench, manager command centre) are
  natural as composable React components; TypeScript types mirror the backend DTOs.
- Large ecosystem for what our UI needs: charts, a dependency-graph view, real-time updates, accessible components.
- Rejected: **JavaFX/Swing** (desktop only, employees could not report from their phones); **server-rendered
  Thymeleaf** (poor fit for live dashboards); **Angular** (heavier, slower to learn for the team).

### 3. Why this backend technology? — Spring Boot (and why it is still Java)

**Spring Boot is not a different language — it is the most widely used Java framework.** All 178 backend
classes are plain Java. The framework supplies infrastructure (HTTP server, JSON, transactions, security,
database access) so our effort goes into the *object-oriented domain design*, which is what the course
assesses. Our first prototype used the raw JDK HTTP server with a hand-written JSON parser; it collapsed into a
single tangled file, which is exactly why we moved to a framework.

Spring is itself built on the OOP principles taught in the course:

| Course concept | How Spring relies on it |
|---|---|
| Programming to interfaces | We depend on `PriorityStrategy`, `AssignmentStrategy`, `EmailGateway`…; Spring injects implementations |
| Composition over inheritance | Services are composed of collaborators passed in constructors (dependency injection) |
| Polymorphism | Spring injects *every* `AssignmentStrategy` bean into a list; adding a strategy class extends the system |
| Repository pattern | Spring Data repositories are interfaces; the implementation is generated |
| Observer pattern | We built our own `IncidentEventPublisher`/`IncidentObserver` (visible in the code), the same idea as Spring events |
| Exceptions | One `@RestControllerAdvice` maps our custom exception hierarchy to HTTP responses |

Our domain classes (`Incident`, the `IncidentState` classes, `SlaRecord`, strategies, escalation handlers,
the NLP and ML code) **do not depend on Spring at all** — the unit tests construct them with `new` and run in
milliseconds. That separation is itself good OO design.

Alternatives considered: **plain Java HTTP server** (our prototype — no security, persistence or validation,
became unmaintainable); **Jakarta EE / Quarkus** (valid, but smaller community resources for students);
**Node.js / Django / FastAPI** (would move the OOP core out of Java, defeating the purpose of the course).

### 4. Why this database? — PostgreSQL (H2 for development)

- Incidents, users, services and SLA policies are highly **relational** (foreign keys, joins for reporting,
  many-to-many service dependencies), so a relational database with constraints fits better than a document
  store such as MongoDB.
- PostgreSQL gives ACID transactions (an incident update and its audit record commit together), CHECK and
  foreign-key constraints as a second line of defence, good indexing for search, and is free and production-grade.
- **H2 in PostgreSQL mode** lets any team member run the project with zero setup (`./mvnw spring-boot:run`).
  The same Flyway migration runs on both, and the full test suite passes on both.
- **Flyway** versions the schema like code (`V1__initial_schema.sql`); Hibernate only *validates* the schema.

### 5. Why this architecture? — Layered, domain-centric monolith with a REST API

- Matches the "Choice 1: layered architecture" in the brief: Frontend → REST API → Business layer → Domain
  model → Repository → Database.
- A **rich domain model** keeps rules inside objects (the incident decides whether it may be closed), instead of
  scattered across services ("anaemic model").
- A **modular monolith** (packages by capability) is right for a 4-person, 1-month project: one deployable, no
  network calls between modules, easy to debug — while module boundaries (incident, sla, assignment,
  notification…) would allow splitting into services later.
- **Stateless JWT authentication** means any number of API instances can run behind a load balancer.

### 6. What alternatives did we consider?

| Decision | Chosen | Alternatives | Reason |
|---|---|---|---|
| Backend | Spring Boot | Plain JDK server, Quarkus, Node, Django | OOP in Java + production features |
| Frontend | React + TS | JavaFX, Thymeleaf, Angular, Flutter | Web reach, live dashboards, ecosystem |
| Database | PostgreSQL | MySQL, MongoDB, SQLite | Relational data, constraints, free |
| Auth | JWT | Server sessions, OAuth2 provider | Stateless, scalable, simple for SPA |
| Real-time | Server-Sent Events | WebSockets, polling | One-way server push is all we need; works through proxies; simpler |
| Classifier | Own Naive Bayes | External AI API, Weka | Explainable, offline, no cost; an LLM adapter can be added behind `CategoryClassifier` |
| Architecture | Modular monolith | Microservices | Team size and timeline; boundaries preserved for later |

### 7. Advantages and limitations

**Advantages:** strong typing end to end; clear separation of concerns; every algorithm pluggable; works offline
with zero setup; production path with PostgreSQL and Docker; automated tests on every push (CI); interactive
API documentation.

**Limitations:** Spring has a learning curve and adds start-up time (~10 s); the Naive Bayes classifier and
TF-IDF similarity are lexical — they understand synonyms we listed, not arbitrary paraphrases (an embedding or
LLM adapter would improve this); reports are computed in memory, fine for thousands of incidents but not
millions; SSE connections are held per server instance.

### 8. How does the technology support object-oriented design?

- JPA maps our class hierarchy directly: `@Inheritance(SINGLE_TABLE)` stores `Employee`, `Technician`,
  `TeamLead`, `Administrator`, `ItManager` in one table with a discriminator; `@Embeddable` maps composition
  (`SlaRecord`, `Resolution`, `Feedback`); `@ManyToMany` maps the service dependency graph.
- Dependency injection makes the Strategy, Observer and Factory patterns almost free to wire: implementations
  are discovered automatically.
- Hibernate works through fields, so entities can keep **no public setters** (encapsulation).
- Bean Validation, exception handlers and transactions are declarative, keeping cross-cutting code out of the
  domain classes.

### 9. How would the system evolve if users increased significantly?

| Pressure | Step |
|---|---|
| More API traffic | Run several API instances behind a load balancer (already stateless); container orchestration (Kubernetes) |
| SLA monitor duplicated across instances | Distributed lock (ShedLock) or move the monitor to one scheduled worker |
| Database load | Read replicas for reports and search; table partitioning of incidents/audit by month; connection pooling (HikariCP already used) |
| Reports | Replace in-memory aggregation with SQL aggregates or a reporting warehouse; cache dashboards |
| Search | Full-text index (PostgreSQL `tsvector`) or Elasticsearch |
| Notifications | Queue (RabbitMQ/Kafka) between the observer and channels; retries with back-off |
| Live updates | Move SSE fan-out to Redis pub/sub so any instance can push to any user |
| Intelligence | Pre-computed vector embeddings for similarity; scheduled model retraining |
| Organisation growth | Split modules into services along existing package boundaries (notification and intelligence first) |

## Security considerations

| Threat / requirement | Mitigation |
|---|---|
| Password theft | BCrypt (cost 12, per-password salt); hashes never serialised to clients |
| Brute force | Account locks for 15 minutes after 5 failed attempts; all attempts audited |
| User enumeration | Same error and similar timing for unknown e-mail and wrong password |
| Unauthorised operations | URL rules + `@PreAuthorize` with a role hierarchy (Manager > Admin > Team Lead > Technician) + domain-level checks (only the assignee can acknowledge) |
| Data leakage between employees | Row-level rule: employees see only their incidents (others return 404, not 403); internal notes hidden from employees |
| Token tampering / expiry | HMAC-SHA256 signed JWT, issuer validated, 8-hour expiry; deactivated users rejected immediately |
| Injection | JPA parameter binding / Criteria API (no string-built SQL); CSV export neutralises formula injection |
| Invalid input | Bean Validation on every request; domain invariants re-checked |
| Lost updates | Optimistic locking (`@Version`) → 409 Conflict |
| Browser attacks | CSP, frame options, referrer policy headers; CORS restricted to the frontend origin; CSRF not applicable to header-based tokens |
| Error leakage | Stack traces never returned; uniform RFC 7807 errors |
| Secrets | JWT secret, DB and SMTP credentials from environment variables |
