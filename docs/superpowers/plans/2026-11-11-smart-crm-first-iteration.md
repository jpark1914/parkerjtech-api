# Smart CRM First API Iteration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a cookie-scoped CRM API that persists fictional leads, generates follow-up tasks and activity atomically, and expires anonymous workspaces after 24 hours.

**Architecture:** A single Spring MVC application uses controllers, transactional services, and handwritten JDBC repositories. Application SQL is loaded from `sqlqueries.props`; Flyway owns the H2 domain and Spring Session schema. Spring Security protects mutations with CSRF while visitors remain anonymous.

**Tech Stack:** Java 21, existing Spring Boot version subject to baseline verification, Maven wrapper, Spring JDBC/NamedParameterJdbcTemplate, H2, Flyway, Spring Session JDBC, Spring Security, Jakarta Validation, JUnit and Spring HTTP/database tests.

**Spec:** `docs/superpowers/specs/2026-11-11-smart-crm-first-iteration-design.md` (approved 2026-11-11).

## Global Constraints

- Work in `/Users/jordan_home_drive/Desktop/Dev/parkerjtech-api`, branch `feature/smart-crm-first-itr`; inspect status before edits and preserve unrelated work.
- Java 21 and the existing Spring Boot repository. Preserve the existing Boot version if it resolves and works; stop and report an unavailable or incompatible release before choosing a replacement.
- JDBC persistence, with application queries referenced from `src/main/resources/sqlqueries.props`. No JPA or generated data repositories.
- File-backed H2 initially. Tests use disposable databases, never the developer's persistent database.
- Workspace expiration after 24 hours; cleanup every 15 minutes. Enforce expiry on requests before physical cleanup.
- All domain IDs are server-generated UUIDs; timestamps represent UTC instants. Use one injected `Clock`.
- Required trimmed name (1–100 characters), company (1–150), service interest, source, and estimated value.
- `serviceInterest`: WEBSITE, CUSTOM_APPLICATION, BUSINESS_AUTOMATION. `source`: WEBSITE, SOCIAL_MEDIA, REFERRAL, OTHER. Optional `contactPreference`: EMAIL or PHONE.
- `estimatedValue`: USD, 0 through 1,000,000 inclusive, at most two decimal places. Use BigDecimal.
- HIGH for value >= 5,000; MEDIUM >= 1,000 and < 5,000; LOW below 1,000. New leads start NEW.
- One OPEN task with rule key INITIAL_FOLLOW_UP, due 24 hours after lead creation; three events: LEAD_CREATED, PRIORITY_ASSIGNED, FOLLOW_UP_CREATED.
- Proposed limit: 100 leads per workspace, enforced atomically. These defaults were approved with the design.
- Server-managed sessions determine ownership. No client-supplied workspace selector; no login requirement. Mutations require CSRF.
- Lists use `{ "items": [...] }`; visitor responses use Cache-Control: no-store. No frontend or deployment changes.
- Schema SQL stays in Flyway migrations; Spring Session's framework-owned SQL is exempt from `sqlqueries.props`.

## Review Focus

- Two simultaneous writes at the 100-lead boundary: only one succeeds, with no partial automation (Task 5).
- Expiry while a create request waits for the workspace lock: recheck the clock after locking and reject the stale write (Tasks 5 and 7).
- A fabricated, expired, or replaced session cookie: deny domain access without creating leads in an unexpected new workspace (Tasks 4 and 6).
- Strings containing SQL punctuation and unexpected JSON fields: bind literal text, reject ownership overrides, and return safe errors (Tasks 3 and 6).
- Reusing an on-disk database and cookie after restart: persist the session association, require a fresh CSRF bootstrap if necessary, and retain the original workspace (Task 8).

## File map and conventions

Production Java root `src/main/java/com/parkerjtech/backendserviceapi/`; test root `src/test/java/com/parkerjtech/backendserviceapi/`. Paths below are repository-relative. Keep the existing application name/package to avoid an unrelated rename.

- `repository/`: all handwritten JDBC repositories and row mapping.
- `controller/`: HTTP endpoints and MVC exception handling.
- `model/`: domain records, nested enums, request/response DTOs and list envelopes.
- `service/`: transactions, priority policy, domain reads and cleanup scheduling.
- `utils/`: SQL catalog, JDBC value conversion, session resolution and security error serialization.
- `config/`: Spring wiring, Clock, JSON, datasource/profile and security configuration.
- `exception/`: application exception types.
- Resources: `application.yaml`, `application-local.yaml`, `sqlqueries.props`, `db/migration/V1__crm_schema.sql`, `db/migration/V2__jdbc_sessions.sql`.
- Tests mirror the corresponding responsibility packages; shared fixtures live in `support/`, full HTTP and restart checks in `integration/`.

Public JSON field names are camelCase. Decimal amounts serialize as JSON numbers; ISO timestamps include UTC offset/Z. All DTOs omit workspace association except bootstrap metadata. Use Java records where suitable; enum definitions may be nested in their domain record. Implement methods below as the stable internal interfaces; private helpers are builder discretion.

## Task 1: Establish a verified JDBC foundation

**Files:** Modify `pom.xml`, `src/main/resources/application.yaml`, existing `BackendserviceapiApplicationTests.java`; create `config/TimeConfiguration.java`, `src/main/resources/application-local.yaml`, `src/test/resources/application-test.yaml`, `support/MutableClock.java`.

**Interfaces:** Produces an injectable `Clock` bean (UTC) and `MutableClock(Instant initial)` with `setInstant(Instant)` for deterministic tests. Local database URL is `jdbc:h2:file:./data/smart-crm`; test profile uses a unique disposable in-memory datasource unless explicitly overridden by restart tests.

- [ ] Run `java -version` and `./mvnw -version`, then baseline `./mvnw test`. Record results. If the declared Maven distribution, Boot parent, or dependencies cannot resolve, diagnose network versus unavailable artifact and report the blocker before changing versions. Do not silently substitute a release.
- [ ] Write `config/JdbcFoundationTest.java`: assert `NamedParameterJdbcTemplate`, `Clock`, and a JDBC datasource exist; clock zone is UTC; test datasource is not the local file URL. Run `./mvnw -Dtest=JdbcFoundationTest test`; verify the new expectations fail before implementing their configuration.

  Required test `providesUtcClockAndIsolatedJdbcDatasource` includes `assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC)` and `assertThat(dataSource.getConnection().getMetaData().getURL()).doesNotContain("./data/smart-crm")`; close the connection with try-with-resources.
- [ ] Update dependencies: basic JDBC starter instead of generated Data JDBC repositories; retain Web MVC, Flyway, H2, security and JDBC session support; add validation and matching test support. Remove MongoDB/reactive, REST-client and their test starters; defer PostgreSQL driver/module. Select Boot-version-appropriate artifact names from official docs/resolved metadata. Keep Lombok configuration unless its removal is needed; do not introduce Lombok usage just to retain it.
- [ ] Configure local/test profiles, UTC clock, file datasource, H2 console disabled, no schema auto-generation, session schema initialization disabled, no-store defaults, and tests isolated from local data. Keep JDBC session storage disabled only in the narrow foundation test until Task 2 supplies its schema; the normal app will be verified after Task 2. Add no pretend baseline success assertion.
- [ ] Run `./mvnw -Dtest=JdbcFoundationTest test`; expect zero failures. Commit foundation files with `build: prepare JDBC CRM foundation`. Do not include later task files in this commit.

## Task 2: Own the SQL catalog and database schema

**Files:** Create `utils/SqlQueries.java`, `config/SqlQueryConfiguration.java`, both Flyway migrations, `sqlqueries.props`; test `utils/SqlQueriesTest.java`, `integration/SchemaMigrationTest.java`.

**Interfaces:** `SqlQueries(Resource resource, Set<String> requiredKeys)` loads explicit Java properties; `String require(String key)` returns nonblank SQL or throws a configuration exception. Configuration supplies the complete required key set listed in Task 3. Repositories use the catalog; DDL does not.

- [ ] Write catalog tests with inline test resources: `assertThrows(..., () -> new SqlQueries(resource, Set.of("lead.insert")))` for missing/blank keys; assert continuation lines load with named parameters intact. Write schema test asserting Flyway creates all four domain tables, task uniqueness, same-workspace lead foreign keys, and session tables. Run `./mvnw -Dtest=SqlQueriesTest,SchemaMigrationTest test`; expect missing implementation/schema failures.

  Define configuration failures as `IllegalStateException`. Required tests `rejectsMissingRequiredQuery`, `rejectsBlankRequiredQuery`, and `loadsContinuationLines` assert that exception or `assertThat(queries.require("lead.find")).contains(":workspaceId", ":leadId")` respectively. Schema test `rejectsTaskForLeadInAnotherWorkspace` expects `DataIntegrityViolationException` from a mismatched child insert.
- [ ] Implement the catalog and `V1__crm_schema.sql`: UUID primary keys, timestamp-with-time-zone instants, DECIMAL(12,2) amount, length/check constraints matching the spec, non-null required fields, workspace ownership, unique `(workspace_id,id)` on leads and composite child foreign keys. Task rule uniqueness is `(workspace_id,lead_id,automation_rule_key)`. Index workspace lists and workspace expiry.
- [ ] Add `V2__jdbc_sessions.sql` using the official H2 schema from the **resolved Spring Session dependency version**, including its indexes/attribute table. Retain the upstream schema attribution. Flyway is the sole schema owner; verify JDBC session initialization is `never` and migration runs before session access.
- [ ] Populate named queries for Task 3 using prepared parameters, no visitor interpolation. Add a predicate on workspace ownership to every child read/write, including insert-from-workspace/lead guards. Cleanup queries must include workspace ID and the locked expiry cutoff; global expired-candidate selection is the deliberate cleanup exception.
- [ ] Run `./mvnw -Dtest=SqlQueriesTest,SchemaMigrationTest test` and the existing context test with the disposable test profile. Expect zero failures and successful session-schema availability. Commit with `feat: add CRM SQL catalog and migrations`.

## Task 3: Implement workspace-scoped JDBC repositories

**Files:** Create `model/DemoWorkspace.java`, `repository/WorkspaceRepository.java`, `model/Lead.java`, `repository/LeadRepository.java`, `model/CrmTask.java`, `repository/TaskRepository.java`, `model/ActivityEvent.java`, `repository/ActivityRepository.java`; tests `integration/JdbcRepositoriesTest.java` and shared `support/DatabaseFixtures.java`.

**Interfaces:**

- `DemoWorkspace(UUID id, Instant createdAt, Instant expiresAt)`; repository `insert(DemoWorkspace)`, `Optional<DemoWorkspace> find(UUID id)`, `Optional<DemoWorkspace> lock(UUID id)`, `List<UUID> findExpiredIds(Instant cutoff, int limit)`, `int deleteExpired(UUID id, Instant cutoff)`.
- `Lead(UUID id, UUID workspaceId, String name, String company, ServiceInterest serviceInterest, Source source, BigDecimal estimatedValue, ContactPreference contactPreference, Stage stage, Priority priority, Instant createdAt, Instant updatedAt, Instant lastActivityAt)` with nested enums from the spec. Repository `insert(Lead)`, `List<Lead> list(UUID workspaceId)`, `Optional<Lead> find(UUID workspaceId, UUID leadId)`, `int count(UUID workspaceId)`, `int deleteByWorkspace(UUID workspaceId)`.
- `CrmTask(UUID id, UUID workspaceId, UUID leadId, String description, Instant dueAt, Status status, String automationRuleKey, Instant createdAt)`; OPEN is the only exposed status in this slice. Repository `boolean insertInitialIfAbsent(CrmTask task)`, `List<CrmTask> list(UUID workspaceId)`, `int deleteByWorkspace(UUID workspaceId)`.
- `ActivityEvent(UUID id, UUID workspaceId, UUID leadId, Type type, String description, Instant createdAt)`; nested Type contains the three event names. Repository `insert(ActivityEvent)`, `List<ActivityEvent> list(UUID workspaceId)`, `int deleteByWorkspace(UUID workspaceId)`.
- Required query keys: `workspace.insert`, `workspace.find`, `workspace.lock`, `workspace.expiredIds`, `workspace.deleteExpired`; `lead.insert`, `lead.list`, `lead.find`, `lead.count`, `lead.deleteByWorkspace`; `task.insertInitialIfAbsent`, `task.list`, `task.deleteByWorkspace`; `activity.insert`, `activity.list`, `activity.deleteByWorkspace`.

- [ ] Write repository tests inserting two workspaces and leads: cross-workspace lookup is empty; lists/counts are scoped; same-workspace composite foreign keys reject mismatched tasks/events. Assert `BigDecimal("4999.99")`, UUIDs, optional contact preference and UTC timestamps round-trip. A name such as `Sam '); DROP TABLE crm_lead; --` remains literal text and tables remain readable. Run `./mvnw -Dtest=JdbcRepositoriesTest test`; expect failures before repository implementation.

  Required tests include `hidesLeadFromOtherWorkspace` with `assertThat(leads.find(workspaceB.id(), leadA.id())).isEmpty()` and `roundTripsSqlPunctuationAsText` with `assertThat(leads.find(workspaceA.id(), leadA.id()).orElseThrow().name()).isEqualTo(leadA.name())`.
- [ ] Implement parameter maps and explicit row mapping with `NamedParameterJdbcTemplate` and catalog lookups. Leads sort `created_at DESC, id`; tasks `due_at, id`; activity `created_at, lead_id, CASE type` (creation, priority, follow-up), `id`, so a single lead's equal-time events remain causally ordered.
- [ ] Implement `insertInitialIfAbsent` using a guarded insert while the caller holds the workspace row lock, backed by the unique constraint. Return false only for an existing matching rule, not for SQL/foreign-key errors. Callers must use it transactionally under the lock. Test two calls produce one task and a foreign-key error is not swallowed.
- [ ] Run `./mvnw -Dtest=JdbcRepositoriesTest,SqlQueriesTest,SchemaMigrationTest test`; expect zero failures. Commit with `feat: add workspace-scoped JDBC repositories`.

## Task 4: Bootstrap anonymous sessions and enforce workspace ownership

**Files:** Create `service/WorkspaceService.java`, `utils/WorkspaceSessionResolver.java`, `controller/SessionController.java`, `model/SessionResponse.java`, `config/SecurityConfiguration.java`, `exception/ApiException.java`, `utils/ApiProblemWriter.java`, `controller/ApiExceptionHandler.java`; test `integration/WorkspaceSessionApiTest.java`.

**Interfaces:** `WorkspaceService.bootstrap(UUID existingWorkspaceId)` returns a persisted active `DemoWorkspace` or creates one; `DemoWorkspace requireActive(UUID id)` throws bootstrap-required if missing/expired. `WorkspaceSessionResolver.bootstrap(HttpServletRequest)` binds a server-selected workspace under session attribute `demoWorkspaceId`; `UUID requireWorkspaceId(HttpServletRequest)` reads an existing session only, verifies expiry, and clears an expired association. `SessionResponse(UUID workspaceId, Instant createdAt, Instant expiresAt, String csrfToken, String csrfHeaderName)` is the bootstrap body.

- [ ] Write real HTTP session tests (random port, cookie-aware client): bootstrap returns expiry exactly 24 hours from fixed clock, opaque HttpOnly session cookie, and a usable CSRF token/header. Same cookie returns same workspace; separate jar returns a different one; fabricated cookie never recovers another workspace. At exact expiry the old association is rejected, then bootstrap returns a new empty workspace. Run `./mvnw -Dtest=WorkspaceSessionApiTest test`; expect 404/missing behavior initially.

  Required tests `reusesWorkspaceForSameCookie`, `isolatesSeparateCookieJars`, and `expiresAtExactDeadline` assert `assertThat(second.workspaceId()).isEqualTo(first.workspaceId())`, different IDs for separate jars, and a new ID after `clock.setInstant(first.expiresAt())` and re-bootstrap. Test the expired association through `WorkspaceSessionResolver` until Task 6 adds the domain GET endpoint.
- [ ] Implement bootstrap/resolution, transactions for new workspaces, and server-side JDBC sessions. Do not create a session from `requireWorkspaceId`. Make workspace expiry authoritative regardless of sliding session inactivity or cookie refresh. Domain methods in Task 5 recheck active state after locking; resolver checks alone do not authorize writes.
- [ ] Configure anonymous access to explicit `/api/demo/**` routes, CSRF enabled for unsafe methods, form/basic login disabled, safe JSON security handlers, no-store, and no permissive CORS. Return bootstrap token explicitly from the framework token object, preserving its expected request encoding. Default cookie Secure=true, HttpOnly=true, SameSite=Lax, path `/`, max age 24h; local/test HTTP overrides Secure=false. Set session inactivity to 24h and enable persistent JDBC sessions. A cross-site origin must not receive credentialed CORS permission.
- [ ] Implement safe Problem Details with `code` and `fieldErrors`. Codes: `WORKSPACE_REQUIRED` (401), `CSRF_INVALID` (403), `INVALID_REQUEST` (400), `LEAD_NOT_FOUND` (404), `WORKSPACE_LIMIT_REACHED` (409), `INTERNAL_ERROR` (500). MVC and security handlers use the same shape. CSRF may reject a cookie-less POST before workspace resolution: document 403 unless the request carries a valid token. Test 401 via a safe domain GET after Task 6; no silent login redirects.
- [ ] Run `./mvnw -Dtest=WorkspaceSessionApiTest test`; expect zero failures. Commit with `feat: bootstrap isolated anonymous CRM sessions`.

## Task 5: Create leads and automation in one transaction

**Files:** Create `model/CreateLeadCommand.java`, `service/PriorityPolicy.java`, `service/LeadService.java`; test `service/PriorityPolicyTest.java`, `integration/LeadCreationServiceTest.java`, `integration/LeadCapacityConcurrencyTest.java`.

**Interfaces:** `CreateLeadCommand(String name, String company, Lead.ServiceInterest serviceInterest, Lead.Source source, BigDecimal estimatedValue, Lead.ContactPreference contactPreference)`; `Lead.Priority PriorityPolicy.assign(BigDecimal value)`; `Lead LeadService.create(UUID workspaceId, CreateLeadCommand command)`. Read methods added in Task 6. Service depends on Task 3 repositories and Clock; callers never supply stage/priority/timestamps.

- [ ] Write parameterized priority tests for 0, 999.99, 1000, 4999.99, 5000, and 1000000. Write service test at fixed `2026-11-11T12:00:00Z`: created stage NEW, priority HIGH for 5000, exactly one OPEN task due `2026-11-12T12:00:00Z`, three semantically ordered events and identical creation instants. Run `./mvnw -Dtest=PriorityPolicyTest,LeadCreationServiceTest test`; verify initial failures.

  Required `assignsPriorityAtThresholds` uses parameter pairs `0/LOW`, `999.99/LOW`, `1000/MEDIUM`, `4999.99/MEDIUM`, `5000/HIGH`, `1000000/HIGH`. Test `createsOneCompleteAutomationBundle` includes `assertThat(tasks.list(workspace.id())).singleElement().extracting(CrmTask::dueAt).isEqualTo(Instant.parse("2026-11-12T12:00:00Z"))` and checks the three event types in order.
- [ ] Implement immutable command and policy; implement `@Transactional create`: lock workspace, read Clock **after** acquiring lock, recheck absolute expiry, check count < 100, trim/validate command, persist lead, guarded initial task and three events. Use the single post-lock instant throughout. Include the applied threshold in priority activity and make task wording explicit that no message was sent.
- [ ] Add rollback integration test with a test-only injected failure from `ActivityRepository.insert` after lead/task insertion. Assert original exception propagates and persisted lead/task/event counts remain unchanged. Add repeated-rule test using the same lead under workspace lock: second insertion returns false, task count remains one; repeated POST semantics will create a separate lead.
- [ ] Add concurrency tests with independent transactions, barriers/latches and bounded timeouts (no arbitrary sleeps): from 99 leads, two creates produce one success/one limit exception and exactly 100 complete lead bundles; a create waiting for a lock fails if the clock advances past expiry before acquiring it. Ensure test fixtures finish setup transactions before starting threads.
- [ ] Run `./mvnw -Dtest=PriorityPolicyTest,LeadCreationServiceTest,LeadCapacityConcurrencyTest test`; expect zero failures and no orphaned automation. Commit with `feat: create leads with transactional follow-up automation`.

## Task 6: Expose validated lead, task and activity APIs

**Files:** Create `model/CreateLeadRequest.java`, `model/LeadResponse.java`, `controller/LeadController.java`, `model/TaskResponse.java`, `service/TaskService.java`, `controller/TaskController.java`, `model/ActivityResponse.java`, `service/ActivityService.java`, `controller/ActivityController.java`, `model/ItemsResponse.java`, `config/JsonConfiguration.java`; modify `LeadService`, API error handler; test `integration/CrmApiTest.java`, `model/CreateLeadRequestTest.java`.

**Interfaces:** `ItemsResponse<T>(List<T> items)`; `LeadService.list(UUID workspaceId): List<Lead>` and `get(UUID workspaceId, UUID leadId): Lead`; `TaskService.list(UUID workspaceId): List<CrmTask>`; `ActivityService.list(UUID workspaceId): List<ActivityEvent>`. Read services check active workspace in a transaction before returning results. DTOs expose spec fields minus `workspaceId`. CreateLeadRequest has exactly the six command fields and converts to a validated CreateLeadCommand after trimming name/company.

- [ ] Write request validation tests for missing/null/blank strings, trimmed length 100/101 and company 150/151, unknown enums, negative amount, 1000000/1000000.01, 1.001 precision, null required amount, and omitted/null contact preference. Run `./mvnw -Dtest=CreateLeadRequestTest test`; expect failures before validation implementation.
- [ ] Add HTTP tests for six specified routes: bootstrap, POST 201 plus Location `/api/demo/leads/{id}`, GET lead, lead/task/activity envelopes and stable ordering. Use actual cookies/CSRF rather than only security test shortcuts. Separate cookie jars see empty lists and receive 404 for another visitor's lead. Domain GET without bootstrap is 401. Include same-timestamp leads to verify tie-breaking.

  Required HTTP tests: `createsLeadWithLocationAndAutomation`, `otherVisitorCannotRetrieveLead`, `rejectsOwnershipOverride`, `requiresBootstrapForReads`, `rejectsMutationWithoutCsrf`, and `rejectsExpiredWorkspaceWithoutInserting`. With the chosen HTTP client assert status codes `201`, `404`, `400`, `401`, `403`, `401` respectively, and deserialize response DTOs/envelopes to assert IDs and row counts, not just statuses.
- [ ] Implement DTO validation, mappings, read services/controllers and JSON unknown-field rejection using the resolved Boot/Jackson version's official API. Return BigDecimal as a JSON number and ISO UTC timestamps; no persistence record serialization or extra workspace fields. Reject attempts to supply `workspaceId`, `id`, `stage`, `priority`, timestamps, unknown fields, malformed UUID/JSON, or invalid enum values with 400.
- [ ] Add security/error tests: POST without/wrong CSRF is 403; valid CSRF with expired workspace is 401 without data creation; stale/fabricated cookies cannot mutate another workspace; unknown ownership query parameters cannot select another workspace. Capacity returns 409. A SQL error returns generic 500 with no SQL/stack/session values. All responses including errors are no-store. Test no login redirect and no credentialed wildcard CORS.
- [ ] Run `./mvnw -Dtest=CreateLeadRequestTest,CrmApiTest,WorkspaceSessionApiTest test`; expect zero failures. Commit with `feat: expose validated CRM lead and activity APIs`.

## Task 7: Expire and clean workspaces safely

**Files:** Create `service/WorkspaceCleanupService.java`, `service/WorkspaceCleanupTransaction.java`, `service/WorkspaceCleanupScheduler.java`; test `integration/WorkspaceCleanupTest.java` and `integration/WorkspaceCleanupConcurrencyTest.java`; modify application cleanup settings.

**Interfaces:** `int WorkspaceCleanupService.cleanupExpired(Instant cutoff, int batchSize)` finds a bounded candidate list, calls the separate proxied `@Transactional boolean WorkspaceCleanupTransaction.deleteIfExpired(UUID id, Instant cutoff)`, and returns deleted workspace count. Scheduler calls with Clock.instant() every 15 minutes. Do not rely on self-invoked `@Transactional` methods.

- [ ] Write cleanup test with active, exactly-expired, and older workspaces plus lead/task/event rows. Assert expired children/parents disappear, active records survive, a second run returns zero, and session schema remains intact. Run `./mvnw -Dtest=WorkspaceCleanupTest test`; expect missing cleanup failures.

  Required `deletesOnlyExpiredWorkspaceBundles` starts with two expired and one active workspace: `assertThat(cleanup.cleanupExpired(now, 100)).isEqualTo(2)`, followed by `assertThat(cleanup.cleanupExpired(now, 100)).isZero()`, active lead still present, and zero children for both deleted IDs.
- [ ] Implement bounded candidate selection, row locking, expiry recheck, child-first deletion (activity, tasks, leads, workspace), and per-workspace transaction. Handle an already-deleted candidate as a no-op. Add scheduler with configurable 900000ms delay and disable it in deterministic tests. Spring Session owns cleanup of its expired rows.
- [ ] Add two-connection concurrency tests: hold workspace lock while a lead bundle is created; cleanup waits and preserves it if not expired, or deletes the entire expired bundle after commit. Reverse order: deletion wins the lock and a queued create rejects missing workspace. Assert no partial task/event rows; test lock waits with latches and bounded futures.
- [ ] Run `./mvnw -Dtest=WorkspaceCleanupTest,WorkspaceCleanupConcurrencyTest,LeadCapacityConcurrencyTest test`; expect zero failures. Commit with `feat: clean up expired CRM workspaces safely`.

## Task 8: Prove persistence and document the API demonstration

**Files:** Create `integration/SessionRestartPersistenceTest.java`, `docs/api/smart-crm-first-iteration.md`; modify `README.md`, `.gitignore` only if needed for temporary cookies/demo artifacts.

**Interfaces:** Documentation defines exact JSON examples matching DTOs and security behavior. Restart test starts/stops two application contexts sequentially against one temporary H2 file and sends the same HTTP cookie to both.

- [ ] Write restart test: bootstrap and create a 5000-value lead in context A; stop cleanly; start context B on the same temporary datasource with Flyway validate-on-restart; bootstrap using original cookie, assert original workspace/lead/task/events remain. Retrieve a new token if required; create a second lead. Use clock before expiry, fresh ports, HTTP cookie persistence, and close contexts before deleting temporary files. Run `./mvnw -Dtest=SessionRestartPersistenceTest test`; investigate and fix any actual persistence gap without weakening the assertion.

  Required `preservesWorkspaceAndLeadAcrossRestart` asserts `assertThat(afterRestart.workspaceId()).isEqualTo(beforeRestart.workspaceId())`, GET original lead returns 200 with the same ID, task list contains its one initial task, and activity contains its three events. The second POST proves the recovered session can still mutate with a refreshed token.
- [ ] Update README from the stale JPA/setup-only text: Java 21 requirement, profile-aware run command `./mvnw spring-boot:run -Dspring-boot.run.profiles=local`, file persistence path, test commands, SQL-query conventions, exact API route table, JSON examples, status/error table, and links to approved design/plan. Document only functionality verified by execution.
- [ ] Add cookie-aware curl walkthrough: `GET /api/demo/session` saving a temporary cookie jar; extract csrfToken/header with Python or jq explicitly identified; POST a fictional lead; GET lead/task/activity; repeat with a separate jar to show isolation. Use safe shell quoting and temporary files; never commit session cookies. Explain POST retries create additional leads, task deadlines may exceed workspace lifespan, local Secure=false, and bootstrap after 401. State React will use same-origin/development proxy; do not modify `jordan-portfolio`.
- [ ] Run `./mvnw verify` once for the full test suite and package verification; expect BUILD SUCCESS, zero test failures and a packaged application. If packaging changes are needed, resolve them and rerun affected tests plus verify. Run `git diff --check`.
- [ ] Start the packaged app locally on an available localhost port with a temporary file-backed database and the local profile; execute the documented bootstrap/create/read/isolation flow using real cookies/tokens. Confirm 201, three matching activity records, one follow-up task, and second-visitor isolation. Stop the app and remove only this smoke run's temporary artifacts.
- [ ] Commit with `docs: document and verify the first CRM API iteration`. Report tests actually run, API demonstration result, remaining limitations, and any blockers. No deployment, push, merge or PostgreSQL-readiness claim is implied by completing this plan.

## Self-review and coverage

- Intent/scope: Tasks 4–6 deliver the complete API slice; Task 8 makes it reviewable without React.
- JDBC/SQL/migrations: Tasks 1–3; application queries are centralized while framework SQL/DDL retain their proper owners.
- Session lifecycle/security: Tasks 4, 6–8; session expiry does not override absolute workspace expiry, and cookie persistence is proved across restart.
- Domain rules/transactions: Tasks 3 and 5; precision, deterministic priority, deduplication and rollback are exercised.
- Ownership/limits/cleanup: Tasks 3–7; all five Review Focus conditions have assigned tests.
- Deferred scope stays deferred. No status transitions, seeding, frontend, external notifications, or deployment are part of these tasks.

Execution: native, approved 2026-11-12. Owner requested responsibility-based packages during Task 4; all remaining file paths above reflect that change.
