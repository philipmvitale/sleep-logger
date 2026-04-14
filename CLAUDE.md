# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

All Gradle commands run from the `sleep/` directory:

```bash
cd sleep

# Full build (compile + unit tests + integration tests + lint + coverage)
./gradlew build

# Run all tests and checks
./gradlew check

# Unit tests only
./gradlew test

# Integration tests only (uses Testcontainers — requires Docker running)
./gradlew integrationTest

# Run a single test class
./gradlew test --tests "com.noom.interview.fullstack.sleep.SleepApplicationTests"

# Run a single integration test
./gradlew integrationTest --tests "com.noom.interview.fullstack.sleep.TestControllerIT"

# Generate OpenAPI classes
./gradlew openApiGenerate

# Lint (ktlint)
./gradlew runKtlintCheck

# Auto-fix lint issues
./gradlew runKtlintFormatOverIntegrationTestSourceSet runKtlintFormatOverKotlinScripts runKtlintFormatOverMainSourceSet runKtlintFormatOverTestSourceSet

# Coverage report (Kover, enforces 90% minimum)
./gradlew koverVerify

# Validate Mermaid diagrams in docs/architecture.md
npx -p @mermaid-js/mermaid-cli mmdc -i docs/architecture.md -o /tmp/validation-check.md
```

Run the full stack with Docker: `docker compose up --build` (Postgres on 5432, API on 8080).

## Architecture

Kotlin 1.6 + Spring Boot 2.7 REST API backed by PostgreSQL 13. Java 17. No ORM — uses `NamedParameterJdbcTemplate`
(Spring JDBC) directly.

### API-First with OpenAPI Generator

The API contract is defined in `sleep/src/main/resources/openapi/sleep-api.yaml`. The OpenAPI Generator Gradle plugin
generates request/response DTOs and interfaces for each tag into `build/generated/openapi/`. The controllers implement
generated interfaces — never edit API interface or DTOs by hand; change the YAML spec instead.

### Layer Structure

- **Controller** (`sleep/src/main/kotlin/com/noom/interview/fullstack/sleep/controller/`) — implements generated API
  interface, maps between models and generated DTOs
- **Service** (`sleep/src/main/kotlin/com/noom/interview/fullstack/sleep/service/`) — business logic
- **Repository** (`sleep/src/main/kotlin/com/noom/interview/fullstack/sleep/repository/`) — SQL via
  `NamedParameterJdbcTemplate`
- **Domain model** (`sleep/src/main/kotlin/com/noom/interview/fullstack/sleep/model/`) — internal representation.
- **Exceptions** (`sleep/src/main/kotlin/com/noom/interview/fullstack/sleep/exception/`) — domain exceptions
  (`ResourceNotFoundException`, `ResourceConflictException`, `SleepLogInvalidException`) and DB constraint
  exceptions (DbConstraints.kt`).
- **Exception handling** (
  `sleep/src/main/kotlin/com/noom/interview/fullstack/sleep/controller/GlobalExceptionHandler.kt`) — translates domain
  exceptions to HTTP error responses.

### Database

PostgreSQL with Flyway migrations in `sleep/src/main/resources/db/migration/`.
Spring Configuration in `sleep/src/main/kotlin/com/noom/interview/fullstack/sleep/db/DatabaseConfiguration.kt`.

### Testing

- **Unit tests** (`src/test/`) — use MockK for mocking, `@ActiveProfiles("unittest")` disables Flyway.
- **Integration tests** (`src/it/`) — extend `AbstractIntegrationTest` which starts a PostgreSQL Testcontainers
  instance. These run a full Spring context with `MockMvc`. Uses SpringMockK for mocking within the Spring context.
- GitHub Actions CI (`.github/workflows/build.yml`) runs `./gradlew build sonar` which includes both test suites.
