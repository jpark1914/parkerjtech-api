# Parker J Tech API

Spring Boot backend for Parker J Tech's Smart CRM demo. This first iteration supports anonymous visitor workspaces, fictional lead intake, deterministic priority, follow-up tasks, and activity history. It will connect to the `jordan-portfolio` React application in a later iteration.

## Run locally

Requires JDK 21. The included Maven wrapper provides Maven; a global installation is unnecessary.

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

The API listens on `http://localhost:8080`. The `local` profile permits HTTP session cookies. The default configuration uses Secure cookies for HTTPS and binds to `127.0.0.1`.

H2 persists domain data and JDBC sessions in `./data/smart-crm`. Restarting preserves an unexpired workspace when the client retains its cookie. Configure `CRM_DATABASE_URL`, `CRM_DATABASE_USERNAME`, and `CRM_DATABASE_PASSWORD` as needed. Database files and credentials are ignored by Git. Flyway owns schema changes; the H2 web console is not included.

## Try the API

With the server running, use the curl demonstration (requires curl and Python 3):

```bash
bash scripts/smoke-api.sh
```

For another local port:

```bash
CRM_BASE_URL=http://localhost:8081 bash scripts/smoke-api.sh
```

The script bootstraps two visitors, creates a fictional lead, checks its follow-up and activity, and verifies the second visitor cannot retrieve it. It uses temporary cookie jars and deletes those files when finished. The fictional lead remains until workspace expiry.

See the [API guide](docs/api/smart-crm-first-iteration.md) for request/response examples, cookie and CSRF handling, validation rules, and error codes.

## Verify

```bash
./mvnw verify
```

Tests use disposable H2 databases, including a temporary file-backed database for restart verification. They cover priority boundaries, rollback, workspace isolation, CSRF, validation, capacity races, expiration, and transactional cleanup. After dependencies have been downloaded, `./mvnw -o verify` runs against the local Maven cache.

The dependency-managed Flyway version currently warns that H2 2.4.240 is newer than its latest verified version. H2 integration checks exercise the actual managed versions; PostgreSQL migration compatibility has not been tested.

## Console logging

The application uses Spring Boot's default SLF4J/Logback logger. Standard ANSI console colors are enabled: INFO green, WARN yellow, and ERROR red. Set `SPRING_OUTPUT_ANSI_ENABLED=never` to disable colors when needed. Successful CRM requests print at INFO, rejected requests at WARN, and unexpected failures at ERROR. Logs appear in the console running the backend; when launched in an IDE, use its Run console.

A committed lead produces a line such as:

```text
INFO ... localTime=2026-11-13T12:00:00-06:00 Alex Morgan + 550e8400-e29b-41d4-a716-446655440000 has been created as a lead
```

Action logs cover each endpoint and the main service sub-actions:

| Action | Logged details |
| --- | --- |
| Initialize/recover demo session | Created or recovered workspace ID and expiration for new workspaces |
| Create a lead | Input/capacity checks, name + ID, assigned priority, follow-up task ID/status/deadline, each activity event type/ID |
| Retrieve one lead or list leads | Name + ID for each lead and list count, including empty lists |
| Retrieve tasks | Count, task IDs, related lead IDs, status and deadline |
| Retrieve activity | Count, event IDs/types and related lead IDs |
| Clean expired workspaces | Candidate count, skipped workspaces, removed lead/task/activity counts and workspace IDs |

Lead, priority, task, activity-creation, and workspace-deletion success messages are logged after transaction commit, so a rollback does not print a false success. Request summaries also include method, path, status, and duration without cookies, CSRF tokens, query strings, or request bodies. Lead names are kept on one log line.

Log times default to `America/Chicago`, including daylight-saving changes. Set `CRM_LOG_TIME_ZONE` to another Java timezone ID to change them. Database/API timestamps remain UTC. The `local` profile enables exception stack traces for debugging; the default profile prints exception types and safe context. API error responses continue to hide exception internals. Restart the backend after updating the code to see the new logs.

## Structure

Java packages under `com.parkerjtech.backendserviceapi`:

| Package | Responsibility |
| --- | --- |
| `controller` | HTTP endpoints and MVC error handling |
| `model` | Domain records, enums, request/response DTOs |
| `repository` | Handwritten, workspace-scoped JDBC queries and row mapping |
| `service` | Business rules, transactions, reads, expiration and cleanup |
| `utils` | SQL catalog, JDBC conversions, session resolution, security error serialization |
| `config` | Spring, clock, security and JSON wiring |
| `exception` | Application errors |

Application runtime SQL lives in `src/main/resources/sqlqueries.props`, loaded explicitly as Java properties. Queries use named JDBC parameters; values are never interpolated into SQL. Missing required queries fail startup. Flyway DDL lives in `src/main/resources/db/migration`; Spring Session manages its own runtime SQL.

## Demo behavior and next steps

- Each anonymous cookie session starts with an empty workspace and an absolute 24-hour lifetime. Tabs sharing the cookie share the workspace.
- Creating a lead assigns priority, creates one internal follow-up task, and records three activity events in one transaction. No email or external message is sent.
- Workspaces allow up to 100 leads. Expired access is denied immediately; scheduled cleanup deletes expired domain records in bounded batches every 15 minutes.
- A follow-up is due 24 hours after creation, so its deadline can outlast the temporary workspace. This iteration demonstrates task generation only.
- Repeating a POST creates another lead. Task-rule deduplication is separate from HTTP request idempotency.

React integration, status transitions, sample data, search/editing, simulated time, reset, analytics, outbound notifications, and deployment are deferred. Before public launch, verify hosting capacity, HTTPS/proxy configuration, request limits and global abuse controls. The API currently targets local demonstrations.

[Approved design](docs/superpowers/specs/2026-11-11-smart-crm-first-iteration-design.md) · [Implementation plan](docs/superpowers/plans/2026-11-11-smart-crm-first-iteration.md)
