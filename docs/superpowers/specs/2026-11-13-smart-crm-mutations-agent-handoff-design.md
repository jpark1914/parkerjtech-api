# Smart CRM editing and workflow: design and agent handoff

Date: 2026-11-13

Status: Design approved by Jordan on 2026-11-13. Backend implementation is assigned to a separate agent; this chat prepares the handoff and plan only. Read the companion implementation plan before editing application code.

## Intent and sequence

Extend the tested first-iteration API so visitors can edit their fictional leads, change sales stages, and complete generated follow-up tasks. These operations enable an interactive CRM page in the separate `jordan-portfolio` React application.

This document is the backend agent's handoff. Jordan's current agent will separately prepare the reusable backend skill in `/Users/jordan_home_drive/Desktop/Dev/AISkillFolder` and the portfolio frontend design and implementation plan. The backend agent owns only `parkerjtech-api`; do not edit the portfolio or skill library.

## Context to read

Repository: `/Users/jordan_home_drive/Desktop/Dev/parkerjtech-api`.

Read `README.md`, `docs/api/smart-crm-first-iteration.md`, the original design and plan under `docs/superpowers/`, relevant production code and tests. Inspect the current branch and working tree before edits. The reviewed baseline was commit `2d00b66` on `feature/smart-crm-first-itr`; do not reset to it or overwrite newer work. Coordinate branch/worktree choices with the active workspace. Do not merge, push, or deploy without authorization for those actions.

Current architecture: Java 21, Spring Boot, Maven wrapper, explicit JDBC, Flyway, file-backed H2, Spring Session JDBC, anonymous workspaces and CSRF. SQL is loaded from `src/main/resources/sqlqueries.props`. Packages are organized by responsibility: `controller`, `model`, `service`, `repository`, `utils`, `config`, `exception`.

## Goals and boundaries

- Edit the same business fields accepted during lead creation.
- Change among the existing five lead stages.
- Complete an OPEN follow-up and expose its completion time.
- Keep meaningful activity history and transaction-aware action logs.
- Preserve existing creation, retrieval, session, expiry and cleanup behavior.
- Document stable request/response contracts for the frontend agent.

Exclude new notes, custom tasks, task reopening, lead deletion, contact-address collection, external messages, authentication accounts, revised priority rules, frontend code and deployment. Do not add new automation on stage changes or completion.

## Approaches

1. **Focused extension of the existing API — selected by Jordan.** Add three mutation operations, additive schema/model changes, tests and API documentation. This preserves the tested foundation and enables frontend work with limited additional scope.
2. **Broader CRM backend.** Include notes, custom tasks, reopening and further automation. More capable, but delays the first interactive frontend and introduces unrequested product decisions. Deferred.

## Proposed contracts

All paths below are relative to `/api/demo`. Mutations require the existing session cookie and returned CSRF header/token. Successful mutations return 200 JSON and `Cache-Control: no-store`. Ownership always comes from the session, never a request field.

### PUT /leads/{id}

Replace the editable business fields with a complete form submission:

```json
{
  "name": "Alex Morgan",
  "company": "Demo Services",
  "serviceInterest": "BUSINESS_AUTOMATION",
  "source": "REFERRAL",
  "estimatedValue": 7500.00,
  "contactPreference": "EMAIL"
}
```

Use existing creation validation, enum rules and whitespace normalization. The first five fields are required; omitted/null `contactPreference` clears it. Reject unknown fields, including stage, priority, ownership, identifiers and timestamps. Preserve the existing stage, identity, ownership and creation time. Return the complete updated `LeadResponse`.

Recalculate priority using `PriorityPolicy`. A business-field change records one `LEAD_UPDATED` event; a priority change additionally records one `PRIORITY_ASSIGNED` event explaining the threshold. Do not create another follow-up or reset its due date. A normalized submission identical to stored values returns the existing lead without changing timestamps or recording events.

### PATCH /leads/{id}/stage

```json
{ "stage": "QUALIFIED" }
```

Accept exactly one required enum field: `NEW`, `CONTACTED`, `QUALIFIED`, `WON`, `LOST`. Return the complete updated `LeadResponse`. Record one `LEAD_STAGE_CHANGED` event describing old and new stages. Setting the current stage is a successful no-op.

### PATCH /tasks/{id}/status

```json
{ "status": "COMPLETED" }
```

Accept exactly one required field whose only supported target is `COMPLETED`; reject `OPEN`, null and unknown values as `400 INVALID_REQUEST`. OPEN tasks become COMPLETED with a UTC `completedAt`. Return the complete `TaskResponse`, extended with nullable `completedAt`. Repeating completion returns the original completion timestamp without another event or timestamp change.

Record one `FOLLOW_UP_COMPLETED` event associated with the task's lead. Preserve task identity, rule key, description, due date and creation time. `GET /tasks` continues to return both OPEN and COMPLETED tasks, preserving due-time/ID ordering; the frontend can derive next action from OPEN tasks and join their lead IDs to lead names.

## Approved design defaults

These defaults were proposed by the author and included in Jordan's approval of the written design:

- Stage movement is allowed between any of the five values, including moving back from WON/LOST. There is no terminal-stage restriction in this demonstration.
- WON/LOST does not complete or cancel an existing task. Completion does not move a lead's stage.
- Actual changes to lead fields, stage or associated task completion update the lead's `updatedAt` and `lastActivityAt` using the operation's timestamp. No-ops preserve both.
- Mutations are serialized under the existing workspace row lock. Concurrent distinct edits use last committed write semantics; there is no new optimistic-version API. Full field replacement and the separate stage endpoint preserve each other's fields.
- An update event identifies the changed field names rather than retaining a complete before/after payload. Stage events include the transition; completion events identify the task.
- Task completion is one-way in this iteration. Completing a task after its due date is allowed while the workspace is active.

## Security, errors and transaction behavior

Preserve the 24-hour absolute workspace expiry, 100-lead creation capacity, session bootstrap, strict JSON, CSRF and workspace-scoped access. Existing workspace mutations remain possible at the lead capacity limit. Never bootstrap a new workspace implicitly during a mutation.

Use the existing safe Problem Details contract (`status`, `detail`, `message`, `code`, `fieldErrors`). Invalid payloads/UUIDs return 400, missing/expired workspaces 401, invalid CSRF 403, inaccessible leads 404 `LEAD_NOT_FOUND`, inaccessible tasks 404 `TASK_NOT_FOUND`. Foreign-workspace objects must be indistinguishable from absent objects. Retain the existing security filter ordering where missing CSRF can take precedence over a workspace error.

Each operation is one service-layer transaction: lock workspace, read the injected clock after acquiring the lock, verify expiry, load the scoped target, validate/apply the change, write activity and return the result. Use the same workspace-first locking order as creation and cleanup. All new target reads/updates include workspace ownership; task completion derives its lead association from the persisted scoped task. Check affected row counts. Any failure rolls back target changes and events together.

Keep current SLF4J/Logback logging conventions, local log times, sanitized user strings, method/action logs and safe error responses. Announce successful changes only after commit, including lead name/ID, stage transition or completed task ID as applicable. Distinguish no-op retrieval from a new change; never log false completion after rollback. Do not log session cookies, CSRF tokens or full request bodies. Catch exceptions only to handle or add useful context, then propagate; do not swallow failures or return false success.

## Schema and implementation constraints

- Add a new forward-only Flyway migration; do not change applied V1/V2 or require deleting the persistent database.
- Extend task status constraints to OPEN/COMPLETED, add nullable `completed_at`, and enforce consistency: OPEN has no completion timestamp; COMPLETED has one. Existing tasks remain OPEN with null completion time.
- Extend activity constraints for `LEAD_UPDATED`, `LEAD_STAGE_CHANGED`, `FOLLOW_UP_COMPLETED`, retaining existing values. Existing H2 checks are unnamed; inspect their actual metadata and select a deterministic migration strategy rather than assuming generated names are stable.
- Update domain records, DTO mapping, repository row mapping, fixtures and all task constructors for the additive nullable field. Preserve existing field names and UTC serialization.
- Keep application runtime queries in `sqlqueries.props`, JDBC named bindings, startup query validation and existing package conventions. No JPA or new persistence framework.
- Update activity ordering if necessary so multiple events from one operation have deterministic, meaningful order. Preserve initial lead creation's creation/priority/follow-up order, and place an edit event before its priority reassignment at the same operation time.

## Acceptance evidence

The backend agent must verify:

1. Valid edits persist and survive a restart; field validation matches creation, priority boundaries are correct, and stages/tasks remain intact after editing.
2. Every supported stage can be selected; current-stage requests are no-ops. Stage changes do not trigger task generation or cancellation.
3. Completion is persisted and visible in task retrieval, with one timestamp and event across repeated or concurrent completion calls.
4. All mutations enforce session, CSRF, ownership and absolute expiry. An operation that waits for a workspace lock rechecks the time after acquiring it. Nonexistent and foreign IDs have equivalent errors.
5. Rollback leaves no partial changes, events or committed-success log messages. No-ops preserve timestamps/events. Cleanup cannot race a mutation into orphaned records.
6. Migration upgrades a populated V1/V2 database without data loss, also works from an empty database, and enforces the new constraints.
7. Existing first-iteration behavior continues to pass. Run `./mvnw -o -B verify` when dependencies are cached; use the normal Maven workflow if additional dependencies genuinely need downloading. Use isolated databases, not the user's running demo database.

Prefer focused tests around these behaviors and existing test helpers; do not merely test method wiring. Leave test design and exact class decomposition to the implementer.

## Backend agent deliverables

Read the companion plan at `docs/superpowers/plans/2026-11-13-smart-crm-mutations.md` and implement when authorized in the receiving chat; do not recreate an existing plan unless the requirements have changed. Update `docs/api/smart-crm-first-iteration.md` and README to replace the now-outdated deferrals for these operations. Include request/response examples, new activity types, no-op semantics, stage behavior, nullable completion time and the browser session/CSRF sequence.

Finish with files changed, verified commands/results, migration considerations and a concise contract handoff for the frontend agent. Note that the frontend must refresh leads, tasks and activity after successful mutations; it must not silently replay a mutation into a newly bootstrapped workspace or automatically retry an ambiguous failed create request. Do not change cookies, CORS or hosting as part of this backend handoff.
