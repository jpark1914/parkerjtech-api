# Smart CRM Editing and Workflow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. This is a handoff for a separate backend agent; the preparing agent must not execute it.

**Goal:** Let an anonymous demo visitor edit leads, change stages and complete follow-ups through the existing isolated CRM API.

**Architecture:** Extend the existing responsibility packages and JDBC repositories. Serialize mutations with the workspace row lock; update domain data and activity atomically and log success after commit. Preserve first-iteration endpoints and session behavior.

**Tech Stack:** Java 21, existing Spring Boot/Maven versions, JDBC, Flyway, H2, Spring Session JDBC, SLF4J/Logback, JUnit/AssertJ.

**Spec:** `docs/superpowers/specs/2026-11-13-smart-crm-mutations-agent-handoff-design.md` — approved 2026-11-13; read it in full.

## Global Constraints

- Work only in `parkerjtech-api`; do not modify `jordan-portfolio` or `/Users/jordan_home_drive/Desktop/Dev/AISkillFolder`.
- Keep Java 21, explicit JDBC, `sqlqueries.props`, Flyway and responsibility packages; no JPA or dependency upgrades.
- Preserve 24-hour absolute workspace expiry, session ownership, CSRF and the 100-lead creation capacity. Capacity must not block edits.
- New mutations return 200 with complete DTOs and `Cache-Control: no-store`; IDs/times/priority/ownership remain server-controlled.
- No notes, custom tasks, reopening, deletion, new automation, outbound messages, frontend or deployment work.
- No-op mutations preserve event counts and timestamps. Never rewrite applied V1/V2 migrations or clear the user's database.
- Inspect current Git state and use isolation when needed; do not reset other work or push/merge/deploy without authorization.

## Review Focus

- Identical decimal values with different scales, e.g. `5000` and `5000.00`, must be no-ops (Task 2).
- Completion retried concurrently must preserve one timestamp and one event (Task 4).
- A mutation queued on a lock past expiry must fail after the lock is acquired (Task 5).
- A populated V2 database may have different generated H2 constraint names (Task 1).
- A timeout or expired workspace must not result in a frontend mutation being replayed into a new workspace; document this contract (Task 5).

## File and interface map

Production Java paths below are relative to `src/main/java/com/parkerjtech/backendserviceapi/`; tests are relative to `src/test/java/com/parkerjtech/backendserviceapi/`.

- `model/Lead.java`: retain all existing fields/enums.
- `model/CrmTask.java`, `model/TaskResponse.java`: append nullable `Instant completedAt`; add `CrmTask.Status.COMPLETED`.
- `model/ActivityEvent.java`: add `LEAD_UPDATED`, `LEAD_STAGE_CHANGED`, `FOLLOW_UP_COMPLETED`.
- Create `model/ChangeLeadStageRequest.java`: record with required `Lead.Stage stage`.
- Create `model/UpdateTaskStatusRequest.java`: record with required `CrmTask.Status status`; service accepts only COMPLETED.
- Reuse `CreateLeadRequest` and its validated `CreateLeadCommand` for PUT's identical editable field set. Avoid duplicate field validators; a shared rename is unnecessary for this iteration.
- `repository/LeadRepository.java`: add `int updateDetails(Lead lead)`, `int updateStage(Lead lead)`, `int touchActivity(UUID workspaceId, UUID leadId, Instant now)`.
- `repository/TaskRepository.java`: add `Optional<CrmTask> find(UUID workspaceId, UUID taskId)`, `int complete(UUID workspaceId, UUID taskId, Instant completedAt)`; the UPDATE also requires status OPEN.
- `service/LeadService.java`: add `Lead update(UUID workspaceId, UUID leadId, CreateLeadCommand command)`, `Lead changeStage(UUID workspaceId, UUID leadId, Lead.Stage stage)`.
- `service/TaskService.java`: add `CrmTask complete(UUID workspaceId, UUID taskId, CrmTask.Status targetStatus)`.
- Existing lead/task controllers map these methods; shared error handling and logging keep their existing mechanisms.
- `src/main/resources/sqlqueries.props`, `config/SqlQueryConfiguration.java`: add and validate named query keys for the above operations.
- New migration: `src/main/java/db/migration/V3__crm_mutation_support.java` (Flyway `BaseJavaMigration`) to discover and replace the relevant unnamed H2 checks deterministically. If V3 has been allocated by newer work, take the next unused version.

## Task 1: Upgrade persistent tasks and activity safely

**Files:** New Java migration above; model task/response/event classes; task row mapping and insert bindings; affected fixtures and tests; `integration/SchemaMigrationTest.java`; create `integration/CrmMutationMigrationTest.java`.

**Interfaces:** Produces task records with nullable `completedAt`, OPEN/COMPLETED persistence, and the three new event values. Existing task creation supplies null; task listing includes the new field.

- [ ] Write migration tests: migrate a disposable DB to V2, insert a workspace/lead/OPEN task/initial events using explicit column lists, migrate to latest, and assert all IDs/values survive and `completed_at` is null. Also migrate an empty DB to latest. Repeat the upgrade with altered generated check names so the migration cannot rely on one name.
- [ ] Add constraint assertions: OPEN/null and COMPLETED/non-null succeed; OPEN/non-null, COMPLETED/null, unknown status and unknown event type fail. Existing foreign keys and initial-task uniqueness still reject invalid rows.
- [ ] Run `./mvnw -o -B -Dtest=CrmMutationMigrationTest,SchemaMigrationTest test`; confirm failure is due to missing schema support.
- [ ] Implement the migration using the Flyway connection and H2 metadata to identify only the status/type checks on the intended tables; quote metadata identifiers correctly and fail clearly if the expected constraints cannot be identified. Replace them with stable named checks and add the nullable UTC timestamp/consistency check. Migration SQL is schema work; runtime application SQL stays in `sqlqueries.props`.
- [ ] Extend records/mappings and update every constructor/fixture. Replace positional task INSERTs in existing tests with explicit columns to prevent schema-order bugs. Preserve existing JSON field names and list order.
- [ ] Rerun the targeted tests and `./mvnw -o -B test`; require zero failures before committing this independently usable schema/model change.
- [ ] Commit only this task's files: `feat: support CRM completion and mutation events in persistence`.

## Task 2: Edit lead business fields

**Files:** Lead controller/service/repository; query properties/catalog; create `integration/LeadUpdateApiTest.java`; extend `integration/ApplicationLoggingTest.java` and existing rollback helpers as needed.

**Interfaces:** Consumes existing create-input validation, priority policy and Task 1 events. Produces `LeadService.update(...) -> Lead` and `PUT /api/demo/leads/{id} -> LeadResponse`.

- [ ] Write HTTP/service assertions using existing `HttpTestClient.send("PUT", path, body, csrfHeaders)`: valid edits return 200/no-store; names are trimmed; null/omitted contact preference clears it; identity/stage/createdAt and the single task's ID/dueAt remain unchanged. Verify the GET result matches the response.
- [ ] Pin priority values at 999.99/1000/4999.99/5000 and capacity behavior at 100 leads. On a real edit assert one LEAD_UPDATED event, an additional PRIORITY_ASSIGNED only if priority changes, and updated lead timestamps. Compare monetary values numerically for no-op detection; an unchanged payload with `5000` versus stored `5000.00` must preserve timestamps/event count.
- [ ] Assert invalid/unknown/server-owned fields and missing required fields return 400 with existing field errors. Explicitly verify missing/foreign lead 404 and missing CSRF 403. Force an event-write failure and verify rollback restores the lead/priority with no committed-success logs.
- [ ] Run `./mvnw -o -B -Dtest=LeadUpdateApiTest test`; verify the new route/behavior fails before implementing.
- [ ] Add scoped SQL key `lead.updateDetails` and required-key registration. Implement `updateDetails(Lead)` updating only business fields, priority and modification/activity times; require exactly one affected row. In the service lock the workspace, read the clock/check expiry, load the owned lead, validate input, detect no-op, recalculate priority, update and insert events atomically. Return the updated domain record.
- [ ] Add `@PutMapping("/{id}")` with `@Valid CreateLeadRequest`, session ownership and no-store. Log the changed fields and name/ID only after commit. Extend activity ordering to put LEAD_UPDATED before PRIORITY_ASSIGNED at equal timestamps while preserving original event order.
- [ ] Run `./mvnw -o -B -Dtest=LeadUpdateApiTest,ApplicationLoggingTest,LeadCreationServiceTest,CrmApiTest test`; require zero failures and commit: `feat: allow demo visitors to edit leads`.

## Task 3: Change sales stages

**Files:** New stage request record; lead controller/service/repository; SQL/catalog; create `integration/LeadStageApiTest.java`; logging tests.

**Interfaces:** Produces `LeadService.changeStage(...) -> Lead` and `PATCH /api/demo/leads/{id}/stage -> LeadResponse`.

- [ ] Write tests moving through NEW, CONTACTED, QUALIFIED, WON, LOST and back to NEW. Assert a real change updates both lead timestamps and records one LEAD_STAGE_CHANGED with old/new values; identical stage is a no-op. Assert details, priority, task status/dueAt/count remain unchanged, including moves into WON/LOST.
- [ ] Assert null/unknown/numeric stage, extra fields and invalid UUID return 400, foreign/missing lead returns 404, and missing/invalid CSRF returns 403. Force event failure to verify rollback and absence of success logs.
- [ ] Run `./mvnw -o -B -Dtest=LeadStageApiTest test`; confirm failure before implementation.
- [ ] Implement `ChangeLeadStageRequest(@NotNull Lead.Stage stage)`, `lead.updateStage`, required-key registration and `LeadService.changeStage`. Use the workspace-first lock/expiry/no-op/transaction pattern from the approved design. Change only stage and timestamps. Add the PATCH route with the existing DTO and error contract, plus post-commit transition logging.
- [ ] Run `./mvnw -o -B -Dtest=LeadStageApiTest,LeadUpdateApiTest,ApplicationLoggingTest test`; require zero failures and commit: `feat: support demo lead stage changes`.

## Task 4: Complete follow-up tasks

**Files:** New task status request record; task controller/service/repository; lead touch method; SQL/catalog; create `integration/TaskCompletionApiTest.java`, `integration/TaskCompletionConcurrencyTest.java`; logging tests.

**Interfaces:** Consumes Task 1 completion fields/events. Produces `TaskService.complete(...) -> CrmTask` and `PATCH /api/demo/tasks/{id}/status -> TaskResponse`; task listing continues to include OPEN and COMPLETED.

- [ ] Write tests completing an OPEN task, including one past its due date. Assert 200/no-store, COMPLETED, UTC completedAt, unchanged task ID/description/rule/dueAt/createdAt, one FOLLOW_UP_COMPLETED identifying the task, and both associated lead timestamps updated without changing stage.
- [ ] Advance the injected clock, repeat completion and assert original completedAt/lead timestamps/event count persist. Run two completion requests concurrently and assert one transition/event and equal completion timestamps in responses. Task ordering remains dueAt then ID, including completed tasks.
- [ ] Assert OPEN/null/unknown/numeric target and extra fields return 400; foreign/missing task returns 404 TASK_NOT_FOUND; invalid CSRF returns 403. Force an event failure and assert task, lead timestamps and activity roll back without success logs.
- [ ] Run `./mvnw -o -B -Dtest=TaskCompletionApiTest,TaskCompletionConcurrencyTest test`; confirm the expected missing-behavior failures.
- [ ] Implement the request record and service target validation, `task.find`, `task.complete`, `lead.touchActivity` and required-key registration. Lock/check workspace before loading the task; derive its lead ID from stored data. Return existing completed tasks unchanged. Otherwise write task/lead/event in one transaction and require one affected row for task/lead writes. Add PATCH mapping, nullable completion DTO mapping and after-commit action logging.
- [ ] Run `./mvnw -o -B -Dtest=TaskCompletionApiTest,TaskCompletionConcurrencyTest,ApplicationLoggingTest test`; require zero failures and commit: `feat: complete demo follow-ups with activity history`.

## Task 5: Verify lifecycle and deliver the frontend contract

**Files:** New `integration/CrmMutationLifecycleTest.java`; existing restart and cleanup concurrency tests; README; API guide; optionally extend `scripts/smoke-api.sh` with documented mutation assertions.

**Interfaces:** Produces verified end-to-end contracts for all three routes and additive response/event fields; no frontend implementation.

- [ ] Write lifecycle tests for all three routes without a workspace, after absolute expiry and with foreign IDs. Use valid CSRF when isolating workspace errors. In a deterministic lock test hold a workspace lock, queue a mutation, advance the injected clock past expiresAt, release the lock and assert 401 with no writes. Coordinate with latches/barriers rather than arbitrary sleeps.
- [ ] Verify cleanup/mutation serialization leaves either a committed valid mutation before cleanup or an expired rejection, never orphaned records. Verify details, stage and completion survive a fresh application context against a disposable file DB. Verify concurrent details/stage mutations preserve each other's field sets.
- [ ] Run `./mvnw -o -B -Dtest=CrmMutationLifecycleTest,SessionRestartPersistenceTest,WorkspaceCleanupConcurrencyTest test`; implement only missing lifecycle protections in the owning service until green.
- [ ] Update README and API guide with routes, full payloads, 200 responses, nullable completedAt, new event values, TASK_NOT_FOUND, retry/no-op semantics and unrestricted stage movement. Remove these specific deferrals while retaining unrelated deferred scope. Explain refresh of all three lists after mutations, OPEN-only next-action selection, and that expired/ambiguous requests must not be silently replayed into a fresh workspace.
- [ ] Run `./mvnw -o -B verify` and `git diff --check`; require BUILD SUCCESS and no whitespace errors. If extending the smoke script, run it only against an agent-owned disposable backend, never the user's live data. Do not stop the user's running app.
- [ ] Review the final diff against the approved design and report actual commands/results, migration behavior, remaining limitations and frontend contract. Commit: `docs: document and verify CRM editing workflow`. Leave pushing/merging/deploying to explicit authorization.

## Self-review

All three mutations, no-op semantics, event ordering, logs, ownership, validation, absolute expiry, capacity-at-edit, rollback, migration upgrades, restart and cleanup are assigned to tasks above. Review Focus has a corresponding assertion in Tasks 1, 2, 4 or 5. Interface names match the file map; the plan adds no new public routes beyond the approved three.
