# Sleep Logger

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=philipmvitale_sleep-logger&metric=alert_status&token=0723d2877c40a826cf0596b897f414347dbbdce0)](https://sonarcloud.io/summary/new_code?id=philipmvitale_sleep-logger)
[![Lines of Code](https://sonarcloud.io/api/project_badges/measure?project=philipmvitale_sleep-logger&metric=ncloc&token=0723d2877c40a826cf0596b897f414347dbbdce0)](https://sonarcloud.io/summary/new_code?id=philipmvitale_sleep-logger)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=philipmvitale_sleep-logger&metric=coverage&token=0723d2877c40a826cf0596b897f414347dbbdce0)](https://sonarcloud.io/summary/new_code?id=philipmvitale_sleep-logger)
[![Duplicated Lines (%)](https://sonarcloud.io/api/project_badges/measure?project=philipmvitale_sleep-logger&metric=duplicated_lines_density&token=0723d2877c40a826cf0596b897f414347dbbdce0)](https://sonarcloud.io/summary/new_code?id=philipmvitale_sleep-logger)

[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=philipmvitale_sleep-logger&metric=bugs&token=0723d2877c40a826cf0596b897f414347dbbdce0)](https://sonarcloud.io/summary/new_code?id=philipmvitale_sleep-logger)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=philipmvitale_sleep-logger&metric=vulnerabilities&token=0723d2877c40a826cf0596b897f414347dbbdce0)](https://sonarcloud.io/summary/new_code?id=philipmvitale_sleep-logger)
[![Code Smells](https://sonarcloud.io/api/project_badges/measure?project=philipmvitale_sleep-logger&metric=code_smells&token=0723d2877c40a826cf0596b897f414347dbbdce0)](https://sonarcloud.io/summary/new_code?id=philipmvitale_sleep-logger)
[![Technical Debt](https://sonarcloud.io/api/project_badges/measure?project=philipmvitale_sleep-logger&metric=sqale_index&token=0723d2877c40a826cf0596b897f414347dbbdce0)](https://sonarcloud.io/summary/new_code?id=philipmvitale_sleep-logger)

[![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=philipmvitale_sleep-logger&metric=reliability_rating&token=0723d2877c40a826cf0596b897f414347dbbdce0)](https://sonarcloud.io/summary/new_code?id=philipmvitale_sleep-logger)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=philipmvitale_sleep-logger&metric=security_rating&token=0723d2877c40a826cf0596b897f414347dbbdce0)](https://sonarcloud.io/summary/new_code?id=philipmvitale_sleep-logger)
[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=philipmvitale_sleep-logger&metric=sqale_rating&token=0723d2877c40a826cf0596b897f414347dbbdce0)](https://sonarcloud.io/summary/new_code?id=philipmvitale_sleep-logger)

A REST API for tracking sleep habits, built with Kotlin, Spring Boot, and PostgreSQL. Users can log nightly sleep data,
retrieve today's entry, and view 30-day aggregate statistics including average bed/wake times and mood frequency
breakdowns.

## Tech Stack

- **Language:** Kotlin 1.6 / Java 17
- **Framework:** Spring Boot 2.7
- **Database:** PostgreSQL 13 with Flyway migrations
- **Data access:** `NamedParameterJdbcTemplate` (no ORM)
- **API contract:** OpenAPI 3.0 with code generation
- **Build:** Gradle (Kotlin DSL)
- **Testing:** JUnit 5, MockK, Testcontainers, Kover (90% minimum coverage)
- **CI:** GitHub Actions + SonarQube

## Getting Started

### Prerequisites

- Docker and Docker Compose
- JDK 17 (for local development)
- cURL (for running CLI scripts)
- jq (for running CLI scripts)
- npm (for architecture diagram validation only)

### Run with Docker

```bash
docker compose up --build
```

This starts PostgreSQL on port 5432 and the API on port 8080. Flyway runs migrations automatically on startup.

### Run locally

```bash
cd sleep
./gradlew bootRun
```

Requires a PostgreSQL instance at `localhost:5432` (or configure via `SPRING_DATASOURCE_URL`).

## API

The full API spec is rendered via Swagger UI on GitHub Pages:
**https://philipmvitale.github.io/sleep-logger/**

The source contract lives in `sleep/src/main/resources/openapi/sleep-api.yaml`.
A simple bash client wrapping cURL (`scripts/sleep-client.sh`) is provided for quick manual testing.
A postman collection is also available at `resources/Sleep API.postman_collection.json` for testing.

All endpoints require an `X-User-Id` header to identify the user.

The user id MUST belong to a user in the `users` table. A script (`scripts/seed-db.sh`) provides a user with ID 1 with
30 days of data in `America/New_York`, and a user with ID 2 with no data in `America/Los_Angeles` that you can use to
test the API.

The user's default timezone at the time of creating a record (not the offset in the provided ISO-8601
timestamp) is used to persist the time zone / offset needed for local time calculations as all times are stored in UTC.

There is no API to create or modify users, but you can modify the `users` table directly in the database to use
different time zones.

### Endpoints

| Method | Path                  | Description                 |
|--------|-----------------------|-----------------------------|
| `POST` | `/api/v1/sleep-log`   | Log last night's sleep      |
| `GET`  | `/api/v1/sleep-log`   | Get today's sleep log       |
| `GET`  | `/api/v1/sleep-stats` | Get 30-day sleep statistics |

### Create a sleep log

```bash
curl -X POST http://localhost:8080/api/v1/sleep-log \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 1" \
  -d '{
    "bedTime": "2024-01-14T22:30:00Z",
    "wakeTime": "2024-01-15T06:45:00Z",
    "mood": "GOOD"
  }'
```

**Response (201):**

```json
{
  "bedTime": "2024-01-14T22:30:00Z",
  "bedTimeZone": "UTC",
  "wakeTime": "2024-01-15T06:45:00Z",
  "wakeTimeZone": "UTC",
  "durationMinutes": 495,
  "mood": "GOOD"
}
```

### Get today's sleep log

```bash
curl http://localhost:8080/api/v1/sleep-log -H "X-User-Id: 1"
```

### Get 30-day statistics

```bash
curl http://localhost:8080/api/v1/sleep-stats -H "X-User-Id: 1"
```

**Response (200):**

```json
{
  "dateFrom": "2023-12-17T00:00:00Z",
  "dateTo": "2024-01-15T00:00:00Z",
  "averageDurationMinutes": 465,
  "averageBedTime": "23:00:00",
  "averageWakeTime": "06:45:00",
  "moodFrequencies": {
    "badFrequency": 5,
    "okFrequency": 12,
    "goodFrequency": 13
  }
}
```

Mood values: `BAD`, `OK`, `GOOD`

### CLI test script

A bash script is included for quick manual testing:

```bash
# Seed DB
./scripts/seed-db.sh

# Get today's log
./scripts/sleep-client.sh today

# Get 30-day stats
./scripts/sleep-client.sh stats

# Log sleep
./scripts/sleep-client.sh log 2024-01-14T22:30:00Z 2024-01-15T06:45:00Z GOOD

# Specify a different user
./scripts/sleep-client.sh -u 2 today

# Un-seed DB
./scripts/unseed-db.sh
```

Configure via environment variables: `SLEEP_API_URL` (default `http://localhost:8080`), `SLEEP_USER_ID` (default `1`).

## Project Structure

```
sleep/
  src/
    main/
      kotlin/.../sleep/
        controller/        # REST controllers (implement generated API interfaces)
        service/           # Business logic and validation
        repository/        # SQL queries via NamedParameterJdbcTemplate
        model/             # Domain models
        exception/         # Domain exceptions and DB constraint mapping
        configuration/     # Spring configuration (database)
      resources/
        openapi/           # API contract (sleep-api.yaml)
        db/migration/      # Flyway SQL migrations
    test/                  # Unit tests (MockK, no database)
    it/                    # Integration tests (Testcontainers + PostgreSQL)
```

The API contract lives in `sleep-api.yaml`. The OpenAPI Generator plugin produces request/response DTOs and controller
interfaces at build time – controllers implement these generated interfaces directly.

## Build & Test

```bash
cd sleep

# Full build (compile + tests + lint + coverage)
./gradlew build

# Unit tests only
./gradlew test

# Integration tests only (requires Docker)
./gradlew integrationTest

# Lint
./gradlew runKtlintCheck

# Coverage report (enforces 90% minimum)
./gradlew koverVerify
```

## CI

GitHub Actions runs on every push to `main` and on pull requests (`.github/workflows/build.yml`). The pipeline executes
`./gradlew build sonar`, which compiles the project, runs unit tests, integration tests,
enforces linting, and minimum code coverage, before publishing the results to SonarCloud. Branch protections
on `main` require both a passing build and SonarCloud quality gate before merging.

CD / deployment is out of scope for this project.

## Architecture

See [docs/architecture.md](docs/architecture.md) for detailed architecture diagrams including:

- **Layer/component diagram** — OpenAPI generation, controller, service, repository, and database layers
- **Database ER diagram** — tables, relationships, constraints, and indexes
- **Request flow** — sequence diagram tracing a create-sleep-log request through all layers

Validate the Mermaid diagrams with:

```bash
npx -p @mermaid-js/mermaid-cli mmdc -i docs/architecture.md -o /tmp/validation-check.md
```

## Design Decisions

- **API-first:** The OpenAPI spec is the single source of truth for the API contract. DTOs and interfaces are generated,
  not handwritten.
- **No ORM:** Uses Spring JDBC directly for full control over queries and transparent SQL.
- **Circular mean for time averaging:** Bed/wake time averages use circular-mean calculations to correctly handle times
  crossing midnight.
- **Timezone-aware storage:** Bed and wake times store their original timezone offsets, preserving the user's local time
  context.
- **Flyway migrations:** Schema changes are versioned and applied automatically on startup.
- **AuthN/AuthZ:** AuthN/AuthZ is out of scope for this project. While this project contains production-grade
  code, it should absolutely not be used in production as-is. Users are simply identified by their database ID in a
  header for this assignment.

## Original Assignment

See [ASSIGNMENT.md](./docs/ASSIGNMENT.md) for the original assignment prompt.
