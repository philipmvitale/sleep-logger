# Architecture

## Layer / Component Diagram

The API follows a classic layered architecture. The OpenAPI spec is the single source of truth for the HTTP contract —
the generator produces interfaces and DTOs that the controller implements directly.

```mermaid
flowchart TD
    Client([HTTP Client])

    subgraph openapi ["OpenAPI Code Generation"]
        Spec["sleep-api.yaml"]
        Generated["Generated: SleepApi interface\nCreateSleepLogRequest, SleepLogResponse,\nSleepStatsResponse, MoodFrequencies, ErrorResponse"]
        Spec --> Generated
    end

    subgraph controller ["Controller Layer"]
        SC["SleepController\n(DTO ↔ Domain mapping)"]
        GEH["GlobalExceptionHandler\n(Exception → HTTP status)"]
    end

    subgraph service ["Service Layer"]
        SS["SleepServiceImpl\n• Validation (duration, overlap, today-check)\n• Circular-mean time averaging\n• Timezone-aware 'today' via Clock"]
    end

    subgraph repository ["Repository Layer"]
        SLR["SleepLogRepository\n(interface)"]
        UR["UserRepository\n(interface)"]
        JSLR["JdbcSleepLogRepository"]
        JUR["JdbcUserRepository"]
        SLR --- JSLR
        UR --- JUR
    end

    subgraph db ["PostgreSQL"]
        PG["NamedParameterJdbcTemplate\nFlyway migrations · No ORM"]
    end

    subgraph domain ["Domain Models"]
        Models["SleepLog · NewSleepLog · SleepStats · User · Mood"]
    end

    subgraph exceptions ["Exceptions"]
        EX["ResourceNotFoundException → 404\nResourceConflictException → 409\nSleepLogInvalidException → 400"]
    end

    Client --> SC
    Generated -.->|implements| SC
    SC --> SS
    SS --> SLR
    SS --> UR
    JSLR --> PG
    JUR --> PG
    exceptions -.->|caught by| GEH
```

## Database ER Diagram

PostgreSQL 13 with Flyway-managed migrations. The `timezones` table is a reference table seeded from
`pg_timezone_names`. The `sleep_logs` table uses a GiST exclusion constraint to prevent overlapping sleep ranges per
user at the database level.

```mermaid
erDiagram
    timezones {
        TEXT name PK "Seeded from pg_timezone_names"
    }

    users {
        BIGINT id PK "GENERATED ALWAYS AS IDENTITY"
        TEXT timezone FK "References timezones(name)"
        TIMESTAMPTZ created_at "DEFAULT NOW()"
        TIMESTAMPTZ updated_at "Trigger: set_updated_at()"
    }

    sleep_logs {
        BIGINT id PK "GENERATED ALWAYS AS IDENTITY"
        BIGINT user_id FK "References users(id) ON DELETE CASCADE"
        mood_type mood "ENUM: BAD, OK, GOOD"
        TIMESTAMPTZ bed_time
        TEXT bed_timezone FK "References timezones(name)"
        TIMESTAMPTZ wake_time
        TEXT wake_timezone FK "References timezones(name)"
        TIMESTAMPTZ created_at "DEFAULT NOW()"
        TIMESTAMPTZ updated_at "Trigger: set_updated_at()"
    }

    timezones ||--o{ users: "timezone"
    timezones ||--o{ sleep_logs: "bed_timezone / wake_timezone"
    users ||--o{ sleep_logs: "user_id (CASCADE)"
```

**Constraints and indexes on `sleep_logs`:**

| Name                               | Type           | Detail                                                     |
|------------------------------------|----------------|------------------------------------------------------------|
| `wake_after_bed`                   | CHECK          | `wake_time > bed_time`                                     |
| `no_overlapping_sleep`             | EXCLUDE (GiST) | `(user_id WITH =, tstzrange(bed_time, wake_time) WITH &&)` |
| `idx_sleep_logs_user_id_wake_time` | INDEX          | `(user_id, wake_time DESC)`                                |

## Request Flow — Create Sleep Log

Traces `POST /api/v1/sleep-log` through all layers, showing the happy path and error branches.

```mermaid
sequenceDiagram
    participant C as Client
    participant SC as SleepController
    participant SS as SleepServiceImpl
    participant UR as UserRepository
    participant SLR as SleepLogRepository
    participant DB as PostgreSQL
    C ->> SC: POST /api/v1/sleep-log<br/>X-User-Id: 42<br/>{bedTime, wakeTime, mood}
    SC ->> SC: Map DTO → NewSleepLog
    SC ->> SS: createTodaySleepLog(42, newSleepLog)
    SS ->> UR: findUserById(42)
    UR ->> DB: SELECT from users
    DB -->> UR: User row
    UR -->> SS: User
    Note over SS: Validate:<br/>• bedTime < wakeTime<br/>• duration ≥ 30 min<br/>• duration < 24 hr<br/>• wakeTime is today (user TZ)
    SS ->> SLR: findLatestSleepLogByUserId(42)
    SLR ->> DB: SELECT latest by wake_time DESC
    DB -->> SLR: SleepLog or null
    SLR -->> SS: existing log?
    Note over SS: Check:<br/>• Log today? → 409<br/>• Overlap with previous? → 400
    SS ->> SLR: saveSleepLog(sleepLog)
    SLR ->> DB: INSERT + GiST constraint check
    DB -->> SLR: saved row with ID
    SLR -->> SS: SleepLog
    SS -->> SC: SleepLog
    SC ->> SC: Map SleepLog → SleepLogResponse
    SC -->> C: 201 Created {SleepLogResponse}
```

**Error paths** (handled by `GlobalExceptionHandler`):

| Condition                      | Exception                         | HTTP Status |
|--------------------------------|-----------------------------------|-------------|
| User not found                 | `ResourceNotFoundException`       | 404         |
| Log already exists for today   | `ResourceConflictException`       | 409         |
| Invalid duration or times      | `SleepLogInvalidException`        | 400         |
| DB overlap constraint violated | `DataIntegrityViolationException` | 409         |
| Missing `X-User-Id` header     | `MissingRequestHeaderException`   | 400         |
