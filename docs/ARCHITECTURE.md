# Architecture & Object-Oriented Design

## 1. System architecture

ResolveIQ is a layered, stateless REST application. The browser (React) talks to a Spring Boot API over
HTTPS/JSON with a JWT; the API holds all business logic in a rich domain model; persistence is PostgreSQL.
Real-time updates are pushed with Server-Sent Events. A background scheduler runs the SLA monitor.

```mermaid
flowchart TB
    subgraph Client["Client tier"]
        UI["React + TypeScript SPA<br/>(Employee · Technician · Manager views)"]
    end

    subgraph API["Spring Boot application (Java 21)"]
        direction TB
        SEC["Security filter chain<br/>JWT · role hierarchy · CORS · headers"]
        WEB["REST controllers + DTOs<br/>GlobalExceptionHandler (RFC 7807)"]
        APP["Application services<br/>IncidentService · AssignmentService · CatalogService · ReportService"]
        DOM["Domain model<br/>Incident (State pattern) · User hierarchy · SlaRecord · events"]
        subgraph ENG["Engines"]
            PRI["Priority strategies"]
            ASG["Assignment strategies"]
            ESC["Escalation chain"]
            AI["Intelligence<br/>TF-IDF · Naive Bayes · correlation · risk"]
        end
        OBS["Observers<br/>Audit · Notifications · Live updates · Knowledge base"]
        REPO["Spring Data repositories"]
        SCHED["SLA monitor (every 30 s)"]
    end

    subgraph EXT["Infrastructure & integrations"]
        DB[("PostgreSQL 16<br/>Flyway migrations")]
        SMTP["SMTP server"]
        SMS["Twilio SMS"]
    end

    UI -- "REST / JSON + JWT" --> SEC --> WEB --> APP
    OBS -. SSE live events .-> UI
    APP --> DOM
    APP --> ENG
    DOM -- "domain events" --> OBS
    APP --> REPO --> DB
    SCHED --> APP
    OBS -- "Adapter" --> SMTP
    OBS -- "Adapter" --> SMS
```

### Layer responsibilities

| Layer | Responsibility | Never does |
|---|---|---|
| Web (`web`) | HTTP mapping, validation of request shape, DTO mapping | Business rules |
| Application services | Use-case orchestration: load → call domain → save → publish events | Decide lifecycle rules |
| Domain (`incident.domain`, `user.domain`, `sla`, `catalog`) | Business rules, invariants, permissions, state transitions | Talk to HTTP or SQL |
| Engines (`priority`, `assignment`, `escalation`, `intelligence`) | Pluggable algorithms behind interfaces | Persist data |
| Observers (`audit`, `notification`, `live`) | React to domain events | Change incident state |
| Persistence (`*Repository`) | Data access via Spring Data JPA | Business logic |

Packages are organised **by business capability** (incident, sla, assignment…) rather than by technical
layer, so each capability can be understood, tested and changed on its own.

---

## 2. Use case diagram

```mermaid
flowchart LR
    E((Employee))
    T((Technician))
    L((Team Lead))
    A((Administrator /<br/>IT Manager))
    S((SLA Monitor<br/>&lt;&lt;system&gt;&gt;))

    subgraph ResolveIQ
        UC1([Register / log in])
        UC2([Report incident])
        UC3([Track status & view resolution])
        UC4([Add information / answer request])
        UC5([Verify, reopen, close, give feedback])
        UC6([View assigned queue & workload])
        UC7([Acknowledge / investigate / add notes])
        UC8([Request information])
        UC9([Resolve with resolution details])
        UC10([Escalate / re-prioritise / link duplicate])
        UC11([Assign technician / view recommendations])
        UC12([View reports & dashboards])
        UC13([Manage users, services, categories])
        UC14([Configure SLA policies & strategies])
        UC15([View audit log & notification log])
        UC16([Monitor SLA, warn, record breaches])
        UC17([Auto-escalate overdue incidents])
    end

    E --- UC1 & UC2 & UC3 & UC4 & UC5
    T --- UC1 & UC6 & UC7 & UC8 & UC9 & UC10
    L --- UC11 & UC12
    L -. inherits .-> T
    A --- UC13 & UC14 & UC15 & UC12
    A -. inherits .-> L
    S --- UC16 & UC17
```

---

## 3. Class diagram (domain core)

```mermaid
classDiagram
    direction TB

    class BaseEntity {
        <<abstract>>
        -Long id
        -Instant createdAt
        -Instant updatedAt
        -long version
    }

    class User {
        <<abstract>>
        -String fullName
        -String email
        -String passwordHash
        -boolean active
        -int failedLoginAttempts
        -Instant lockedUntil
        +getRole()* Role
        +canHandleIncidents() boolean
        +recordFailedLogin(now, max, duration) boolean
        +deactivate()
    }
    class Employee
    class Technician {
        -Set~String~ skills
        -int maxActiveIncidents
        -boolean available
        -ExperienceLevel experienceLevel
        -Instant lastAssignedAt
        +hasSkill(code) boolean
        +isAssignable() boolean
        +goOffDuty()
    }
    class TeamLead
    class Administrator
    class ItManager

    BaseEntity <|-- User
    User <|-- Employee
    User <|-- Technician
    Technician <|-- TeamLead
    User <|-- Administrator
    Administrator <|-- ItManager
    Technician "0..*" --> "0..1" TeamLead : reports to

    class Incident {
        -String number
        -String title
        -String description
        -Impact impact
        -Urgency urgency
        -int affectedUsers
        -Priority priority
        -IncidentStatus status
        -EscalationLevel escalationLevel
        +classify(Classification, Actor, Instant)
        +assignTo(Technician, strategy, Actor, Instant)
        +acknowledge(Actor, Instant)
        +startWork(Actor, Instant)
        +requestInformation(question, Actor, Instant)
        +resolve(Resolution, Actor, Instant)
        +verify(Actor, Instant)
        +close(note, Actor, Instant)
        +escalate(level, User, reason, Actor, Instant)
        +evaluateSla(Instant) List~Signal~
        +availableActions(Actor) Set~IncidentAction~
        +pullEvents() List~IncidentEvent~
    }
    class SlaRecord {
        <<embeddable>>
        -Instant responseDueAt
        -Instant resolutionDueAt
        -boolean responseBreached
        -boolean resolutionBreached
        -Instant pausedAt
        +start(SlaPolicy, Instant)
        +evaluate(Instant) List~Signal~
        +pause(Instant)
        +resume(Instant)
        +status(Instant) SlaStatus
    }
    class Resolution {
        <<embeddable>>
        -String summary
        -String rootCause
    }
    class Feedback {
        <<embeddable>>
        -int rating
        -String comment
    }
    class IncidentNote {
        -NoteType type
        -boolean internal
        -String content
    }
    class ItService {
        -String code
        -Criticality criticality
        +addDependency(ItService)
        +transitiveDependencies() Set~ItService~
    }
    class Category {
        -String code
        -String keywords
        -boolean securitySensitive
    }
    class SlaPolicy {
        -Priority priority
        -int responseMinutes
        -int resolutionMinutes
        -int warningThresholdPercent
    }

    BaseEntity <|-- Incident
    Incident *-- "1" SlaRecord : composition
    Incident *-- "0..1" Resolution
    Incident *-- "0..1" Feedback
    Incident *-- "0..*" IncidentNote : composition
    Incident --> "1" User : reporter
    Incident --> "0..1" Technician : assignee
    Incident o-- "1" ItService : affected service
    Incident o-- "0..1" Category
    Incident --> "0..1" Incident : duplicateOf
    ItService "0..*" --> "0..*" ItService : dependsOn
    SlaPolicy --> "0..1" ItService

    class IncidentState {
        <<interface>>
        +status() IncidentStatus
        +allowedTransitions() Set
        +classify(...)
        +assign(...)
        +acknowledge(...)
        +resolve(...)
        +close(...)
    }
    class AbstractIncidentState {
        <<abstract>>
        #reject(action) InvalidStateTransitionException
    }
    IncidentState <|.. AbstractIncidentState
    AbstractIncidentState <|-- NewState
    AbstractIncidentState <|-- ClassifiedState
    AbstractIncidentState <|-- AssignedState
    AbstractIncidentState <|-- InProgressState
    AbstractIncidentState <|-- ResolvedState
    AbstractIncidentState <|-- ClosedState
    Incident ..> IncidentState : delegates to current state
```

### Strategies, observers, factories and adapters

```mermaid
classDiagram
    direction LR
    class PriorityStrategy {
        <<interface>>
        +assess(PriorityContext) PriorityAssessment
    }
    PriorityStrategy <|.. WeightedScoringPriorityStrategy
    PriorityStrategy <|.. ItilMatrixPriorityStrategy
    PriorityEngine o-- "2..*" PriorityStrategy

    class AssignmentStrategy {
        <<interface>>
        +rank(AssignmentRequest, List~TechnicianProfile~) List~AssignmentCandidate~
    }
    class AbstractAssignmentStrategy {
        <<abstract>>
        +rank(...) List
        #isEligible(...) boolean
        #score(...)* Score
    }
    AssignmentStrategy <|.. AbstractAssignmentStrategy
    AbstractAssignmentStrategy <|-- SkillBasedAssignmentStrategy
    AbstractAssignmentStrategy <|-- LoadBasedAssignmentStrategy
    AbstractAssignmentStrategy <|-- RoundRobinAssignmentStrategy
    AbstractAssignmentStrategy <|-- ExperienceBasedAssignmentStrategy
    AbstractAssignmentStrategy <|-- SmartAssignmentStrategy
    AssignmentService o-- "5" AssignmentStrategy

    class IncidentObserver {
        <<interface>>
        +onIncidentEvent(IncidentEvent)
    }
    IncidentEventPublisher o-- "*" IncidentObserver
    IncidentObserver <|.. AuditTrailObserver
    IncidentObserver <|.. NotificationObserver
    IncidentObserver <|.. LiveUpdateObserver
    IncidentObserver <|.. KnowledgeBaseObserver

    class NotificationChannel {
        <<interface>>
        +type() ChannelType
        +deliver(NotificationMessage) Notification
    }
    NotificationChannel <|.. AbstractNotificationChannel
    AbstractNotificationChannel <|-- InAppNotificationChannel
    AbstractNotificationChannel <|-- EmailNotificationChannel
    AbstractNotificationChannel <|-- SmsNotificationChannel
    NotificationChannelFactory o-- NotificationChannel
    NotificationObserver --> NotificationPolicy
    NotificationObserver --> NotificationChannelFactory

    class EmailGateway {
        <<interface>>
        +send(to, subject, body)
    }
    EmailGateway <|.. SmtpEmailGateway
    EmailGateway <|.. ConsoleEmailGateway
    EmailNotificationChannel --> EmailGateway
    class SmsGateway {
        <<interface>>
    }
    SmsGateway <|.. TwilioSmsGateway
    SmsGateway <|.. SimulatedSmsGateway
    SmsNotificationChannel --> SmsGateway

    class EscalationHandler {
        <<abstract>>
        -EscalationHandler next
        +handle(Incident, Instant) Optional
        #triggerReason(...)*
    }
    EscalationHandler <|-- TeamLeadEscalationHandler
    EscalationHandler <|-- AdministratorEscalationHandler
    EscalationHandler <|-- ItManagerEscalationHandler
    EscalationHandler --> EscalationHandler : next
```

---

## 4. Incident lifecycle (State pattern)

```mermaid
stateDiagram-v2
    [*] --> NEW : employee reports
    NEW --> CLASSIFIED : auto-classify, prioritise, start SLA
    NEW --> CLOSED : confirmed duplicate
    CLASSIFIED --> ASSIGNED : strategy or manual assignment
    CLASSIFIED --> CLOSED : confirmed duplicate
    ASSIGNED --> ACKNOWLEDGED : assignee acknowledges (response SLA stops)
    ASSIGNED --> ASSIGNED : re-assigned
    ASSIGNED --> CLOSED : confirmed duplicate
    ACKNOWLEDGED --> IN_PROGRESS : start investigation
    ACKNOWLEDGED --> ASSIGNED : re-assigned
    IN_PROGRESS --> AWAITING_INFO : request info (SLA paused)
    AWAITING_INFO --> IN_PROGRESS : reporter answers (SLA resumes)
    IN_PROGRESS --> RESOLVED : resolution recorded (resolution SLA stops)
    IN_PROGRESS --> ASSIGNED : re-assigned
    RESOLVED --> VERIFIED : reporter confirms
    RESOLVED --> IN_PROGRESS : reporter reopens
    VERIFIED --> CLOSED : closed
    CLOSED --> [*]
```

Each state is a class (`IncidentStates.NewState`, `ClassifiedState`, …) extending `AbstractIncidentState`,
which rejects every operation by default ("deny by default"). A state overrides only what it allows, so an
illegal transition such as `NEW → CLOSED` is impossible by construction and raises
`InvalidStateTransitionException` (HTTP 409).

---

## 5. Workflow 1 — sequence of a new report

```mermaid
sequenceDiagram
    actor Emp as Employee
    participant C as IncidentController
    participant S as IncidentService
    participant D as DuplicateDetector
    participant NB as NaiveBayesClassifier
    participant F as IncidentFactory
    participant P as PriorityEngine
    participant R as SlaPolicyResolver
    participant A as AssignmentService
    participant I as Incident
    participant Pub as IncidentEventPublisher
    participant O as Observers

    Emp->>C: POST /api/incidents
    C->>S: report(command, reporter)
    S->>D: findSimilarOpenIncidents()
    S->>NB: classify(text) (if no category chosen)
    S->>F: create(...) → NEW incident
    S->>P: assess(impact, urgency, criticality, users)
    S->>R: resolve(service, priority)
    S->>I: classify(category, assessment, slaPolicy)
    Note over I: State NEW → CLASSIFIED<br/>SLA clock starts
    S->>A: autoAssign(incident)
    A->>I: assignTo(best technician)
    Note over I: CLASSIFIED → ASSIGNED
    S->>Pub: publishAll(incident.pullEvents())
    Pub->>O: Audit trail · notifications · live update · model retraining
    C-->>Emp: 201 Created (incident, similar incidents, AI suggestion)
```

---

## 6. OOP concepts — where to find them

| Concept | Implementation |
|---|---|
| **Encapsulation** | `Incident`, `User`, `SlaRecord` have **no public setters**; state changes only through intention-revealing methods that enforce invariants (`assignTo`, `recordFailedLogin`, `pause`). Collections are exposed as unmodifiable views. The password hash never leaves the server. |
| **Abstraction** | `PriorityStrategy`, `AssignmentStrategy`, `NotificationChannel`, `EmailGateway`, `CategoryClassifier`, `IncidentState` describe *what*, hiding *how*. `IncidentService.report()` hides a 10-step workflow behind one call. |
| **Inheritance** (genuine *is-a*) | `TeamLead` **is a** `Technician`; `ItManager` **is an** `Administrator`; every user **is a** `User`. All entities extend `BaseEntity` (identity, timestamps, optimistic locking). Inheritance is *not* used where *has-a* fits (e.g. SLA is composed, not inherited). |
| **Polymorphism** | `user.getRole()`, `canHandleIncidents()` differ per subclass; any `PriorityStrategy` / `AssignmentStrategy` / `NotificationChannel` / `EscalationHandler` can be substituted at runtime. |
| **Interfaces** | 9 contracts: `IncidentState`, `PriorityStrategy`, `AssignmentStrategy`, `IncidentObserver`, `NotificationChannel`, `EmailGateway`, `SmsGateway`, `CategoryClassifier`, plus Spring Data repository interfaces. |
| **Composition** | `Incident` owns its `SlaRecord`, `Resolution`, `Feedback` (embedded) and `IncidentNote`s (cascade + orphan removal) — they cannot exist without it. |
| **Association / Aggregation** | `Incident → reporter`, `Incident → assignee` (association); `Incident → ItService/Category` (aggregation: shared, independent life cycle); `ItService ↔ ItService` dependency graph; `Technician → TeamLead`. |
| **Exception handling** | 17 custom exceptions under `ItsmException`, each with an `ErrorCode` → HTTP status; one `GlobalExceptionHandler` produces RFC 7807 JSON. Database outages → 503, concurrent edits → 409 (optimistic locking). |
| **Collections** | `EnumMap` (state registry, settings), `EnumSet` (allowed transitions, channels), `TreeSet` (sorted unique skills), `LinkedHashMap` (ordered reports), `CopyOnWriteArrayList` (thread-safe observer list), `ConcurrentHashMap` (live connections), `ArrayDeque` (BFS over the dependency graph), sparse `HashMap` vectors (TF-IDF). |
| **Generics** | `SimilarityEngine.rank<T>(...)`, `PageResponse<T>`, repositories. |
| **Modern Java** | Records for value objects and DTOs; a **sealed interface** for events with exhaustive pattern-matching `switch`; text blocks; `List.getFirst()` (Java 21). |

## 7. Design patterns (9)

| Pattern | Where | Why |
|---|---|---|
| **Strategy** | `PriorityStrategy` (2), `AssignmentStrategy` (5) | Algorithms are swapped at runtime from the admin settings without redeploying |
| **State** | `IncidentState` + 9 state classes | Lifecycle rules live in one class per state; illegal transitions impossible |
| **Observer** | `IncidentEventPublisher` + 4 `IncidentObserver`s | Audit, notifications, live dashboard and ML retraining react to events without the incident knowing them |
| **Factory** | `UserFactory`, `IncidentFactory`, `NotificationChannelFactory` | Centralises construction (right subclass, hashed password, unique number, enabled channels) |
| **Adapter** | `EmailGateway` ← `SmtpEmailGateway`/`ConsoleEmailGateway`; `SmsGateway` ← `TwilioSmsGateway`/`SimulatedSmsGateway` | Third-party APIs plug in behind our own interface; simulation and real delivery are interchangeable |
| **Chain of Responsibility** | `EscalationHandler` chain | Team Lead → System Admin → IT Manager; add a level by adding a handler |
| **Template Method** | `AbstractAssignmentStrategy.rank()`, `AbstractNotificationChannel.deliver()` | Fixed algorithm skeleton, subclasses supply only the varying step |
| **Repository** | Spring Data repositories | Domain asks for data by intent; persistence technology is replaceable |
| **Builder** | `AuditRecord.builder()` | Readable construction of records with many optional fields |

## 8. Extensibility checklist (what changes for common requirements)

| New requirement | Change needed |
|---|---|
| New incident category | Admin adds it in the UI — **no code** |
| New SLA tier for a service | Admin adds a policy — **no code** |
| New assignment algorithm | One class implementing `AssignmentStrategy` (auto-discovered) |
| New notification channel (Slack, WhatsApp) | One `NotificationChannel` class + enum value |
| New e-mail provider | One `EmailGateway` adapter |
| New escalation level | One `EscalationHandler`, linked into the chain |
| New reaction to incident activity (e.g. webhook) | One `IncidentObserver` |
| New lifecycle state (e.g. ON_HOLD) | One state class + registry entry |
