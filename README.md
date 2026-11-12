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
