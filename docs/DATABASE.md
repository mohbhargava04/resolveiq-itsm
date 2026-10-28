# Database Design

The schema is created by Flyway from `backend/src/main/resources/db/migration/V1__initial_schema.sql` and runs
unchanged on PostgreSQL 16 and H2 (PostgreSQL mode). Hibernate validates the mapping against it on start-up.

## ER diagram

```mermaid
erDiagram
    USERS ||--o{ TECHNICIAN_SKILLS : has
    USERS |o--o{ USERS : "team lead of"
    USERS ||--o{ INCIDENTS : reports
    USERS |o--o{ INCIDENTS : "assigned to"
    USERS |o--o{ INCIDENTS : "escalated to"
    USERS ||--o{ NOTIFICATIONS : receives
    USERS |o--o{ INCIDENT_NOTES : writes
    IT_SERVICES ||--o{ INCIDENTS : affects
    IT_SERVICES ||--o{ SERVICE_DEPENDENCIES : "depends on"
    IT_SERVICES |o--o{ SLA_POLICIES : "specific to"
    CATEGORIES |o--o{ INCIDENTS : classifies
    INCIDENTS ||--o{ INCIDENT_NOTES : contains
    INCIDENTS |o--o{ INCIDENTS : "duplicate of"
    INCIDENTS ||--o{ AUDIT_RECORDS : "history (by number)"

    USERS {
        bigint id PK
        varchar user_type "EMPLOYEE|TECHNICIAN|TEAM_LEAD|ADMIN|MANAGER"
        varchar full_name
        varchar email UK
        varchar password_hash
        boolean active
        int failed_login_attempts
        timestamptz locked_until
        int max_active_incidents "technicians only"
        boolean available "technicians only"
        varchar experience_level "technicians only"
        bigint team_lead_id FK
    }
    TECHNICIAN_SKILLS {
        bigint technician_id PK, FK
        varchar skill PK "category code"
    }
    IT_SERVICES {
        bigint id PK
        varchar code UK
        varchar name
        varchar criticality
        int user_base
        boolean active
    }
    SERVICE_DEPENDENCIES {
        bigint service_id PK, FK
        bigint depends_on_id PK, FK
    }
    CATEGORIES {
        bigint id PK
        varchar code UK
        varchar keywords
        boolean security_sensitive
    }
    SLA_POLICIES {
        bigint id PK
        varchar priority "P1..P4"
        bigint service_id FK "NULL = default"
        int response_minutes
        int resolution_minutes
        int warning_threshold_percent
    }
    INCIDENTS {
        bigint id PK
        varchar number UK "INC-1001"
        varchar title
        varchar description
        bigint reporter_id FK
        bigint category_id FK
        bigint service_id FK
        varchar impact
        varchar urgency
        int affected_users
        varchar priority
        double priority_score
        varchar status
        bigint assignee_id FK
        timestamptz sla_response_due_at
        timestamptz sla_resolution_due_at
        boolean sla_response_breached
        boolean sla_resolution_breached
        varchar escalation_level
        varchar resolution_summary
        int feedback_rating
        bigint duplicate_of_id FK
        bigint version "optimistic lock"
    }
    INCIDENT_NOTES {
        bigint id PK
        bigint incident_id FK
        bigint author_id FK
        varchar note_type
        boolean internal
        varchar content
    }
    AUDIT_RECORDS {
        bigint id PK
        timestamptz occurred_at
        bigint actor_id
        varchar actor_name
        varchar action
        varchar incident_number
        varchar previous_state
        varchar new_state
        varchar description
    }
    NOTIFICATIONS {
        bigint id PK
        bigint recipient_id FK
        varchar channel "IN_APP|EMAIL|SMS"
        varchar subject
        varchar delivery_status "SENT|SIMULATED|FAILED"
        boolean is_read
    }
    SYSTEM_SETTINGS {
        varchar setting_key PK
        varchar setting_value
    }
```

## Design decisions

| Decision | Reason |
|---|---|
| **Single-table inheritance** for users | Most queries ("all active users", "who reported this?") span roles; technician columns are nullable |
| **SLA record embedded** in `incidents` (`sla_*` columns) | Composition: one-to-one, same life cycle, always read with the incident; no join |
| Resolution and feedback embedded | Same reasoning |
| `incident_number_seq` database sequence | Unique human-friendly numbers (`INC-1001`) even under concurrent creation |
| Audit records keep **name snapshots** and incident number, not FKs only | History must survive renames and deactivation; append-only, never updated |
| Categories and services are **rows, not enums** | Administrators add them at runtime without code changes |
| `service_dependencies` self many-to-many with a CHECK against self-loops | Dependency graph for correlation; cycles prevented in the domain |
| `version` column on every entity | Optimistic locking prevents lost updates |
| CHECK constraints (priority values, rating 1–5, targets > 0, affected users ≥ 1) | Database enforces invariants even if application code is bypassed |
| Indexes on status, priority, assignee, reporter, service, created_at, audit number/time | Back the 13 search filters and dashboards |
| `TIMESTAMP WITH TIME ZONE`, stored in UTC | Correct SLA arithmetic across zones |

## Sample data (seeded on first start)

| Table | Rows | Examples |
|---|---|---|
| users | 14 | 5 employees, 5 technicians, 2 team leads, 1 administrator, 1 IT manager |
| it_services | 13 | Core Network & DNS → Core Database → Identity & SSO → Payment Gateway / ERP / HR Portal / Email / Learning Portal… |
| categories | 9 | Hardware, Software, Network, Database, Cloud, Security (security-sensitive), Authentication, Application, Storage |
| sla_policies | 7 | 4 defaults (the brief's table) + Payment Gateway P1/P2 and Core Database P1 overrides |
| incidents | 47 | 36 resolved & closed over the past month (knowledge base) + 11 live incidents forming the demo scenario |
| audit_records | ~550 | Generated by the real domain methods |
