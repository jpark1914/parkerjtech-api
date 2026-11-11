# Parker J Tech API

Backend for Parker J Tech's Smart CRM interactive demo, the first project in the planned Automation Lab at [parkerjtech.com](https://parkerjtech.com).

The demo will show how small businesses can organize incoming leads, automate follow-up tasks, and track a sales pipeline. Visitors will interact with fictional data in isolated, temporary workspaces.

## Project status

Repository setup only. The application has not been scaffolded, and there are no build or run commands yet. The architecture and features below describe the intended implementation.

## Planned stack

- Java 21 and Spring Boot
- REST APIs with Spring Data JPA
- File-backed H2 for initial persistence
- Flyway for schema migrations
- JUnit and Mockito for backend testing
- A React frontend integrated with the Parker J Tech portfolio

PostgreSQL hosted by Supabase is a future migration target. Deployment on the existing EC2 instance is subject to verifying available resources and infrastructure.

## Planned MVP

- Create, edit, search, and list fictional leads.
- Track New, Contacted, Qualified, Won, and Lost pipeline stages.
- Assign priorities using transparent, deterministic rules.
- Generate follow-up, proposal, and onboarding tasks without duplicates.
- Record manual actions and automation results in an activity timeline.
- Simulate 48 hours passing to demonstrate inactivity rules.
- Reset a visitor's workspace and expire it after 24 hours.
- Show basic workspace metrics if included in the final MVP scope.

The initial demo will use rules rather than an LLM. Any simulated notifications will be clearly identified; real outbound email is outside the initial scope.

## First implementation milestone

Build a small vertical slice:

1. Create a fictional lead through the API.
2. Persist the lead in H2 within the visitor's workspace.
3. Assign its priority and generate a follow-up task.
4. Record the automation in an activity event.
5. Display the resulting state in React.

## Planned domain model

| Entity | Purpose |
| --- | --- |
| DemoWorkspace | Visitor session, expiration, and simulated time |
| Lead | Contact details, pipeline stage, priority, and estimated value |
| Task | Manual or automatically generated work associated with a lead |
| ActivityEvent | Chronological record of actions and rule outcomes |

All reads and writes will be scoped to a workspace, with ownership enforced by the server.

## Proposed API

These routes are illustrative and are not implemented yet.

```http
GET   /api/demo/leads
POST  /api/demo/leads
PATCH /api/demo/leads/{id}
GET   /api/demo/tasks
GET   /api/demo/activity
GET   /api/demo/metrics
POST  /api/demo/simulate
POST  /api/demo/reset
```

## Deployment direction

Serve the React frontend and proxy same-origin `/api` requests to Spring Boot through the existing web server. Keep the backend bound to localhost and persist H2 data outside build artifacts. Verify EC2 capacity, HTTPS, session handling, request limits, and cleanup behavior before public deployment.

## Decisions to confirm before implementation

- Existing React repository, framework, styling, and deployment workflow
- EC2 capacity and reverse-proxy configuration
- Per-visitor 24-hour expiration versus a fixed global reset
- Final MVP analytics scope, public routes, and consultation destination

Never commit credentials, local database files, or real customer data.
