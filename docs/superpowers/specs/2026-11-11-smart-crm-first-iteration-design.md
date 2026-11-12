# Smart CRM: first API iteration

Date: 2026-11-11
Branch: `feature/smart-crm-first-itr`
Status: Proposed design; awaiting owner approval

## Intent

Demonstrate real lead intake and follow-up automation for Parker J Tech through an API first. The future consumer is the owner's `jordan-portfolio` React application. This iteration should be demonstrable with curl or Postman without building a frontend or deploying infrastructure.

Success means two anonymous visitors can independently create fictional leads, retrieve persisted results, and see the generated follow-up tasks and explanatory activity. A refresh or backend restart preserves an unexpired workspace when the client retains its session cookie.

## Confirmed decisions

- Java 21 and the existing Spring Boot repository.
- JDBC persistence, with application queries referenced from `src/main/resources/sqlqueries.props`.
- File-backed H2 initially.
- Anonymous, isolated visitor workspaces from the first iteration.
- Workspace expiration after 24 hours.
- Lead creation and retrieval, automatic priority assignment, initial follow-up generation, and task/activity retrieval.
- API requests are the demonstration surface; React integration follows later.

Details marked as proposals below are engineering defaults selected for review, rather than requirements supplied by the owner.

## Existing code and constraints

The repository contains a generated entry point, an application-name setting, and one context-loading test. There is no CRM domain implementation. The POM declares Spring Boot 4.1.2, Java 21, Spring Data JDBC, reactive MongoDB, security, JDBC sessions, Web MVC, Flyway, H2, PostgreSQL, a REST client, and Lombok. These versions have not yet been build-verified in this planning session.

The implementation should validate the generated build before extending it. Preserve the existing Boot version if it resolves and works; stop and report an unavailable or incompatible release before choosing a replacement. No production infrastructure or React configuration has been inspected.

## Approaches considered

1. JPA with H2: fits the earlier handoff, but introduces ORM conventions and does not match the owner's preference for explicit SQL.
2. JDBC with H2: explicit queries, parameter binding, row mapping, and transaction boundaries. Selected by the owner, including the requirement for `sqlqueries.props`.

Proposed implementation within the selected approach: use `NamedParameterJdbcTemplate` with handwritten repositories. Spring Data JDBC's generated repositories are unnecessary for this slice. Retain JDBC access and replace the Data JDBC starter with the basic JDBC starter if needed to avoid unused repository infrastructure. Remove reactive MongoDB and unused REST-client dependencies and their tests. Keep Spring Security, JDBC sessions, Web MVC, H2, and Flyway; add request validation. PostgreSQL-specific dependencies can be deferred until migration work. Retain existing Lombok only if useful; do not make domain design depend on it.

## Scope and boundaries

Included:

- Establish an anonymous workspace and CSRF-protected cookie session.
- Persist a fictional lead with a server-assigned stage and priority.
- Persist one initial follow-up task and explanatory activity atomically.
- List leads, fetch one lead, list tasks, and list activity for the current workspace.
- Enforce an absolute workspace lifetime and clean up expired domain data.
- Provide repeatable API examples and meaningful automated tests.

Deferred: React changes, deployment, sample lead seeding, editing/search/status changes, completing tasks, manual reset, simulated time, inactivity rules, metrics, LLM features, outbound messages, accounts, and PostgreSQL migration. An initial workspace is empty.

Anti-goals: shared visitor data, SQL assembled from user input, partially saved automation, claims of real outbound notifications, and schema changes driven by ORM auto-generation. Public launch is a separate milestone requiring infrastructure verification and public abuse controls.

## Architecture and query organization

Use a single Spring Boot application and one H2 datasource:

`HTTP controller -> application service -> JDBC repository -> H2`

Organize CRM code under the existing root package, with separate workspace, lead, task, activity, configuration, and error-handling responsibilities. Use explicit request/response DTOs rather than exposing persistence objects. Controllers handle HTTP concerns; services enforce ownership, business rules, and transactions; repositories bind parameters and map rows.

Application runtime SQL belongs in `sqlqueries.props`, using names such as `lead.insert`, `lead.findByWorkspaceAndId`, and `workspace.deleteExpired`. Load this classpath resource explicitly as Java properties; the `.props` extension must not rely on Boot's automatic configuration-file discovery. A small query catalog fails startup on a missing or blank required query. Prefer a dedicated catalog rather than injecting raw SQL into controllers or services.

Bind values through named parameters such as `:workspaceId`; never substitute visitor text into SQL. Multiline properties use normal continuation syntax. Flyway DDL remains in `db/migration/*.sql`. Framework-owned Spring Session queries remain managed by Spring; `sqlqueries.props` governs our own runtime queries.

## Proposed workspace and session lifecycle

- `GET /api/demo/session` bootstraps the client. It creates or recovers the anonymous workspace and returns its ID, creation/expiration timestamps, and a CSRF token plus the header name to send with mutations.
- Store only the server-selected workspace association in the server-side JDBC session. The cookie carries an opaque session identifier; no visitor-selected workspace ID is trusted. This replaces the handoff's illustrative signed client association with a server-side session.
- Use one workspace per session. Reloads and tabs sharing that cookie share the workspace; separate cookie jars and browsers are isolated. Initial session creation should be completed before a client sends parallel domain requests.
- Other API routes require an established workspace and return `401` with a stable bootstrap-required error code if the session/workspace is missing or expired. They never silently create a replacement during a lead mutation.
- `expiresAt = createdAt + 24 hours`, measured in UTC with an injected `Clock`. It is absolute, not extended by reads or writes. Session inactivity settings must not outlive this authorization check; workspace expiry is enforced independently on every domain request.
- At expiry, discard the session's workspace association and deny domain access immediately, even before physical cleanup. Calling the bootstrap endpoint again establishes a fresh empty workspace. Invalid cookie values cannot select a workspace.
- Proposed cleanup: run every 15 minutes and delete expired workspace domain records transactionally, children first. Spring Session handles its own expired session cleanup. Never remove a live H2 database file.
- Lock the workspace row when creating a lead, then recheck expiry and capacity inside the transaction. Cleanup locks/rechecks the workspace before deletion, preventing an expired workspace from accepting a concurrent write or losing half of a transaction.
- Use a persistent HttpOnly session cookie with a maximum lifetime of 24 hours and SameSite=Lax. Secure is enabled under HTTPS; a local development profile permits HTTP. Use JDBC session schema managed explicitly by Flyway and disable conflicting automatic session schema initialization.
- Keep Spring Security enabled with anonymous access to these demo routes and CSRF protection on mutations. Do not introduce a generated login requirement for the demo. API security errors return JSON.

## Proposed data model

All domain IDs are server-generated UUIDs; timestamps represent UTC instants. Store monetary amounts as decimal values, never floating point. Use explicit SQL types and migrations, with PostgreSQL portability considered but not claimed as tested.

| Table | Essential fields |
| --- | --- |
| demo_workspace | id, created_at, expires_at |
| crm_lead | id, workspace_id, name, company, service_interest, source, estimated_value, contact_preference, stage, priority, created_at, updated_at, last_activity_at |
| crm_task | id, workspace_id, lead_id, description, due_at, status, automation_rule_key, created_at |
| crm_activity_event | id, workspace_id, lead_id, type, description, created_at |

Each child belongs to a workspace; tasks/events must reference a lead in that same workspace, enforced by composite foreign keys where appropriate. Add indexes for workspace-scoped list queries and expiration cleanup. Tasks have a unique constraint on `(workspace_id, lead_id, automation_rule_key)` for the initial follow-up rule.

Proposed validation:

- Required trimmed name (1–100 characters), company (1–150), service interest, source, and estimated value.
- `serviceInterest`: WEBSITE, CUSTOM_APPLICATION, BUSINESS_AUTOMATION.
- `source`: WEBSITE, SOCIAL_MEDIA, REFERRAL, OTHER.
- `estimatedValue`: USD, 0 through 1,000,000 inclusive, at most two decimal places. USD is fixed for this slice.
- Optional `contactPreference`: EMAIL or PHONE. No actual email address or phone number is collected in this slice.
- Reject client attempts to set IDs, workspace ownership, stage, priority, or timestamps. Use an explicit unknown-field rejection policy for request DTOs.
- Proposed limit: 100 leads per workspace, enforced atomically. This bounds list responses and per-workspace growth; it does not replace global public-launch rate limiting.

## Proposed initial automation

All new leads start at NEW. Reserve the later stages CONTACTED, QUALIFIED, WON, and LOST without exposing transition endpoints yet.

Priority is transparent and deterministic: HIGH for an estimated value of at least USD 5,000; MEDIUM from USD 1,000 up to USD 4,999.99; LOW below USD 1,000. These thresholds are proposed demo defaults, not measured sales guidance.

One service transaction:

1. Validate and lock/check the workspace.
2. Insert the lead and assigned priority.
3. Insert an OPEN initial follow-up task, rule key `INITIAL_FOLLOW_UP`, due 24 hours after creation.
4. Record LEAD_CREATED, PRIORITY_ASSIGNED, and FOLLOW_UP_CREATED events. Descriptions state why the rule fired and identify the automation as an internal task, not a sent message.
5. Commit all records or roll everything back.

Use one clock instant for this transaction. Equal-time events have explicit semantic ordering in the activity query, with an ID tie-breaker. Rule re-evaluation for the same lead cannot create a duplicate initial task; use the database uniqueness constraint and conflict-safe insertion in a way that does not hide unrelated errors. Repeating the HTTP POST creates a new lead: request-level idempotency keys are deferred and should not be implied.

The follow-up due date demonstrates a realistic business deadline; because the workspace expires 24 hours after bootstrap, the task may disappear before it is due. This iteration demonstrates task creation only. Inactivity and time simulation remain separate future work.

## API contract

Base path: `/api/demo`. All responses are JSON; timestamps use ISO 8601 UTC and UUIDs are strings. Apply `Cache-Control: no-store` to visitor responses. List responses use `{ "items": [...] }`, with deterministic ordering and no pagination in the bounded first slice.

| Method and route | Behavior |
| --- | --- |
| GET /session | Bootstrap/recover session; return workspace metadata and CSRF token/header name |
| POST /leads | Validate and create lead plus automation; return 201 with lead DTO and Location header |
| GET /leads | List current workspace leads, newest first, ID tie-breaker |
| GET /leads/{id} | Fetch current workspace lead; return 404 for missing or another workspace's lead |
| GET /tasks | List current workspace tasks by due time, then ID |
| GET /activity | List current workspace activity chronologically, with stable tie-breaking |

Lead DTOs include the validated input fields, ID, stage, priority, and timestamps. Task DTOs include ID, lead ID, description, due time, status, rule key, and creation time. Activity DTOs include ID, lead ID, type, description, and creation time. Ownership is derived from the session, not a workspace parameter.

Use Problem Details-style errors with a stable application `code`, HTTP status, safe detail, and field errors for invalid input. Distinguish 400 invalid payload/UUID, 401 bootstrap required, 403 missing/invalid CSRF, 404 inaccessible lead, 409 workspace lead limit, and 500 unexpected failures. Do not expose SQL, stack traces, or session values. Document decimal serialization and example payloads for the React consumer.

## React compatibility

Prefer same-origin `/api` proxying for deployment and a development proxy for `jordan-portfolio`. Keep CORS closed by default; do not enable wildcard credentialed origins. Future frontend integration must inspect that repository's actual dev server and deployment settings before choosing proxy or explicit CORS configuration.

The client flow is bootstrap, retain the cookie/token, POST a lead with the returned CSRF header, and refresh leads/tasks/activity. Bootstrap again after a 401. No frontend code changes or login flow are needed for API testing. Include cookie-jar curl examples and CSRF extraction instructions in the README.

## Verification and acceptance

- Unit tests: priority boundaries, validation, UTC clock behavior, and expiry at exactly the deadline.
- Database integration tests against disposable H2: Flyway migrations, query catalog loading, row mapping, workspace-scoped reads, cross-workspace foreign-key rejection, atomic rollback, and duplicate-task prevention.
- HTTP integration tests: separate cookie sessions, bootstrap/token flow, CSRF enforcement, create/read/list contracts, 404 for another workspace's ID, field errors, expiry denial before cleanup, and capacity handling.
- Exercise lead creation versus cleanup/capacity concurrency where locking correctness matters.
- Verify scheduled cleanup removes expired children/workspaces and preserves active data.
- Verify a lead and its JDBC session survive application restart using a temporary file-backed H2 database and the original cookie.
- Run the Maven test suite and package build, then a cookie-aware API smoke demonstration. Never point tests at the developer's persistent database.

Done means the documented API flow works, isolation and automation are demonstrated by tests, data persists locally, and documentation identifies the deferred public-launch work. No claims of production readiness or PostgreSQL compatibility follow solely from passing H2 tests.

## Builder discretion and next step

The builder may choose DTO/row-mapper class names, exact query-key names, index names, test fixtures, cleanup batch sizing, and small helper abstractions consistent with this design. Avoid a generic rules engine or speculative infrastructure.

After owner approval, write the implementation plan with concrete files, task order, tests, and verification commands. Product code changes begin only after that planning step.
