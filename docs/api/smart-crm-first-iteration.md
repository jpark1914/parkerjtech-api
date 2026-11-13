# Smart CRM API: first iteration

Base path: `/api/demo`. Use fictional data only. The API sends no external messages.

## Bootstrap and cookies

`GET /api/demo/session` creates or recovers an anonymous workspace. Retain the session cookie and returned CSRF token. Example response (values are illustrative):

```json
{
  "workspaceId": "db350a40-8d45-4ddd-85ed-f2379dc64f8e",
  "createdAt": "2026-11-12T12:00:00Z",
  "expiresAt": "2026-11-13T12:00:00Z",
  "csrfToken": "example-only",
  "csrfHeaderName": "X-CSRF-TOKEN"
}
```

Complete bootstrap before parallel domain requests. Ownership comes from the server-side session; the returned workspace ID cannot select another workspace. Separate cookie jars/browsers are independent; tabs sharing a cookie share data.

Send the cookie on subsequent requests. For mutations also send the token under the returned header name. After 401, bootstrap again before retrying. An expired mutation never silently creates a workspace. Cookies are HttpOnly, SameSite=Lax, persistent for up to 24 hours, and Secure except in local/test HTTP profiles. Workspace expiry is absolute even if activity refreshes the cookie/idle deadline.

## Routes

| Method | Route | Successful result |
| --- | --- | --- |
| GET | `/session` | 200, workspace metadata and CSRF token |
| POST | `/leads` | 201, created lead and Location header |
| GET | `/leads` | 200, `{ "items": [...] }` |
| GET | `/leads/{id}` | 200, one lead |
| GET | `/tasks` | 200, `{ "items": [...] }` |
| GET | `/activity` | 200, `{ "items": [...] }` |

Visitor responses use `Cache-Control: no-store`. Domain routes require an active workspace. An inaccessible lead returns the same 404 as a nonexistent lead.

## Lead creation

```json
{
  "name": "Fictional Sam",
  "company": "Demo Company",
  "serviceInterest": "BUSINESS_AUTOMATION",
  "source": "REFERRAL",
  "estimatedValue": 5000,
  "contactPreference": "EMAIL"
}
```

- Name and company are trimmed, required, and limited to 100 and 150 characters respectively.
- Service interest: `WEBSITE`, `CUSTOM_APPLICATION`, `BUSINESS_AUTOMATION`.
- Source: `WEBSITE`, `SOCIAL_MEDIA`, `REFERRAL`, `OTHER`.
- Estimated value is a JSON number in USD between 0 and 1,000,000 inclusive, with at most two decimal places. Calculations use BigDecimal and decimal database storage.
- Contact preference is optional/null, or `EMAIL`/`PHONE`. No actual contact address or phone number is collected.
- Unknown fields and client-supplied workspace, ID, stage, priority or timestamps are rejected. Enums use names, not numeric ordinals.

The lead response includes the input fields plus `id`, `stage`, `priority`, `createdAt`, `updatedAt`, and `lastActivityAt`; it omits workspace ownership. UUIDs are strings, timestamps are ISO 8601 UTC, and monetary values serialize as JSON numbers with at most two decimal places.

All new leads start NEW. Priority is HIGH for value >= 5000, MEDIUM for value >= 1000 and < 5000, and LOW below 1000. One transaction creates the lead, one OPEN task with rule key `INITIAL_FOLLOW_UP` due 24 hours after creation, and three events: `LEAD_CREATED`, `PRIORITY_ASSIGNED`, `FOLLOW_UP_CREATED`.

Task responses contain `id`, `leadId`, `description`, `dueAt`, `status`, `automationRuleKey`, `createdAt`. Activity contains `id`, `leadId`, `type`, `description`, `createdAt`. Descriptions explain the priority threshold and that no message was sent. Lists are scoped and bounded by the 100-lead capacity. Leads sort newest first then ID; tasks by due time then ID; activity chronologically, with a lead's equal-time events in creation/priority/follow-up order.

Repeated POSTs create additional leads. Initial-task deduplication is separate from HTTP request idempotency. A task's due date can outlast its temporary workspace; this iteration demonstrates generation only.

## Errors

Problem Details JSON includes `status`, safe `detail`, a client-ready `message` matching the detail, stable `code`, and `fieldErrors` (empty when not applicable). For example, an invalid enum returns a message explaining the invalid value and a field error listing accepted values. Internal exception messages and stack traces stay in server logs.

| Status | Code | Meaning |
| --- | --- | --- |
| 400 | `INVALID_REQUEST` | Invalid payload, enum, UUID, value or unknown field |
| 401 | `WORKSPACE_REQUIRED` | Bootstrap missing, invalid or expired |
| 403 | `CSRF_INVALID` | Missing/invalid token on a mutation |
| 404 | `LEAD_NOT_FOUND` | Lead unavailable in the current workspace |
| 409 | `WORKSPACE_LIMIT_REACHED` | Workspace has 100 leads |
| 500 | `INTERNAL_ERROR` | Unexpected failure, without SQL/stack details |

Unknown routes and unsupported methods retain 404/405 with `INVALID_REQUEST`; other MVC client errors retain their standard status. CSRF runs before controller workspace checks: a POST without a session/token may return 403; a domain GET without a session returns 401. Invalid enum values return the field name and accepted enum names in `fieldErrors`, without echoing the submitted value. Exception stack traces remain in the server logs and are not returned to clients.

## Curl walkthrough

Start the local backend using the README command, then run:

```bash
bash scripts/smoke-api.sh
```

This executable walkthrough uses `curl -c`/`-b` with temporary cookie jars, extracts token/header through Python 3, creates a fictional lead, fetches the results, and verifies a second visitor sees empty lists and 404 for the first visitor's lead. Cookie files are deleted on exit. It targets `http://localhost:8080` unless `CRM_BASE_URL` is supplied. Use a local demonstration backend; the script creates real persisted demo records.

## Lifecycle and React integration

Expiry is 24 hours after workspace creation, checked on domain requests. Cleanup runs every 15 minutes in batches of up to 100 workspaces, deleting child records before the parent under a workspace lock. Expired access is denied before physical cleanup. Spring Session cleans its own expired records. Bootstrap after expiry starts empty.

The future React app should proxy `/api` for same-origin requests, including during development. Bootstrap, retain cookie/token, then refresh leads/tasks/activity after creation. Keep tokens in client memory and refresh through bootstrap as needed. CORS stays closed by default; inspect `jordan-portfolio` before configuring an explicit origin. No React files were changed.

Public launch needs separate hosting, HTTPS, rate-limit and request-limit verification. Editing, transitions, time simulation, reset, seeding, metrics and outbound notifications remain deferred.
