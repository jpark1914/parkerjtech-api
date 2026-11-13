# Reusable Spring Boot backend standard: design

Date: 2026-11-13. Status: written design awaiting approval. The two-file skill approach was selected by Jordan; do not create the final skill until this design is approved.

## Purpose

Capture Jordan's established backend conventions so a future agent can start another Spring Boot API without rediscovering the package organization, JDBC approach, configuration, error handling and action logging decisions. This is a reusable standard grounded in `parkerjtech-api`, not a copy of the CRM's business rules.

## Approaches and choice

- **Selected:** a concise `SKILL.md` with a linked `references/backend-template.md`. Instructions stay easy to find; the detailed POM/YAML/package examples can be read when needed.
- **Alternative:** one Markdown skill containing all examples. Fewer files, but longer instructions on every invocation. Jordan selected the reference-backed structure.

## Files and discovery

Create under `/Users/jordan_home_drive/Desktop/Dev/AISkillFolder/`:

```text
spring-boot-backend-standard/
  SKILL.md
  references/
    backend-template.md
```

Before any skill creation or update, check this library for an existing relevant skill; update it when the purpose matches rather than creating a duplicate. This instruction applies to future skill work, not just backend standards.

Create `/Users/jordan_home_drive/Desktop/Dev/AGENTS.md` with a short instruction to check `AISkillFolder` first for skill creation/updates and to consult this backend standard when starting a Spring Boot backend. If the file exists at implementation time, preserve and extend its current instructions. This makes the preference discoverable for projects under Desktop/Dev; the custom library is not automatically a globally installed skill. Do not silently copy or register it under another global directory. Other workspaces can invoke it by its absolute path.

## Required content

`SKILL.md` has valid name/description frontmatter, applies to creating or restructuring Spring Boot/JDBC APIs for Jordan, and links the detailed reference. State these defaults while allowing a new project's explicit requirements to override them:

- Java 21, Maven wrapper, Spring Boot MVC, JDBC with `NamedParameterJdbcTemplate`, named parameters and `src/main/resources/sqlqueries.props`; no automatic switch to JPA.
- Responsibility packages `controller`, `model`, `service`, `repository`, `utils`, `config`, `exception`. Explain that MVC controllers return JSON views/representations consumed by a separate frontend; no server-side template engine is required.
- Thin controllers, model/DTO validation, service-owned business rules/transaction boundaries, repositories owning scoped JDBC access and row mapping, utilities for focused cross-cutting helpers, configuration for framework wiring.
- Base/local/test YAML profiles, environment-driven datasource credentials, Flyway schema ownership, isolated test databases and a local-only H2 console when H2 is used.
- Spring Boot's SLF4J/Logback, standard ANSI colors, configurable local log time, UTC persistence/API times, action/sub-action logs, safe error responses and post-commit success logs. Handle/log exceptions where useful and propagate them; do not scatter try/catch blocks that swallow errors.
- Problem Details with stable code and field errors, safe client messages, strict input binding, parameterized SQL and appropriate transaction/integration checks.

`references/backend-template.md` supplies a clean example POM, base/local/test YAML, the package/resource/test tree, a short request-to-database flow and operational commands. Label the source project's Java/Spring Boot versions as an observed example rather than an evergreen latest-version claim. Retain versions in an existing project; verify compatibility from official documentation when selecting dependencies for a genuinely new project. Do not include unused POM metadata or unnecessary Lombok/build plumbing.

Distinguish the core baseline from optional features: database engine, persistent sessions, authentication, cookie/CSRF policy, console support and cleanup scheduling depend on the new app. Never impose anonymous access, CRM entities, priority thresholds, 100-lead capacity, 24-hour TTL or the CRM's broad 3xx/4xx stack-trace policy on every backend. Where cookie sessions are selected, retain CSRF protection, HttpOnly/Secure production cookies and an explicit local HTTP override.

## Validation and boundaries

Use the skill-authoring guidance to validate naming/frontmatter/references and exercise a realistic new-backend request against the finished reference. Check that it retains Jordan's JDBC/package/logging conventions without copying CRM-specific rules. Do not scaffold or install dependencies for a new backend merely to create this documentation.

This deliverable includes the skill files and the Desktop/Dev discovery instruction only. No application code changes, library Git initialization, global configuration edits, publishing or external actions are included.
