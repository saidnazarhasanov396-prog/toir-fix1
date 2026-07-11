# TOiR Business Logic Remediation Design

**Date:** 2026-07-11

**Scope:** Implement the complete remediation roadmap from `TOIR_BUSINESS_LOGIC_INTEGRATION_AUDIT_2026-07-11.md` across `toir-backend` and `toir-front`, one verified stage at a time.

## Goal

Turn Planned Shutdown and Repair Campaign from partially connected records into two distinct, production-oriented business aggregates with enforceable safety, authorization, lifecycle, source-of-truth, Work Order, material, approval, reporting, and audit invariants.

## Delivery Strategy

Work proceeds as five vertical stages. Every stage includes forward-only database migrations, backend domain rules, REST contracts, frontend integration, automated tests, and documentation. A stage is complete only when its targeted tests, builds, migration checks, negative scenarios, and diff review pass. Once a stage passes, implementation continues to the next stage without another approval request.

Because the program spans independent subsystems, each stage receives its own detailed implementation plan and commit sequence. Later-stage plans may depend only on verified interfaces delivered by earlier stages.

Pre-existing dirty changes were preserved in separate baseline commits before remediation work:

- Backend: `cf92dd46`
- Frontend: `557b9394`

## Domain Ownership

### Planned Shutdown owns

- planned shutdown window and actual shutdown/startup timestamps;
- asset/area boundary and each asset's `STOPPED`, `RESERVE`, or `RUNNING` disposition;
- readiness items, isolation points, permits, safe-state confirmation, startup tests, and production return approval;
- shutdown lifecycle, reschedule/extension reason, actual downtime, and closure snapshot.

### Repair Campaign owns

- campaign objective, type, owner, priority, stages, work-item prioritization, dependencies, resource plan, planned budget, and closure narrative;
- campaign-level readiness derived from canonical Work Orders, WMS reservations, performers, contractors, approvals, and budget records;
- campaign progress and cost summaries, never duplicate copies of execution or inventory facts.

### Work Order owns

- one executable unit of work, its assignee, tasks, planned and actual execution timestamps, safety checklist, material usage, results, and completion evidence;
- canonical links to its source work item, campaign/stage, and required shutdown.

### Supporting sources of truth

- WMS owns stock, reservation, issue, and return facts.
- Approval owns approval requests, steps, actors, comments, timestamps, and optimistic versions.
- Actual Cost/Budget ledger owns monetary actuals and commitments.
- Audit Log owns immutable change evidence.
- Transactional outbox owns reliable cross-aggregate propagation.

## Relationship Cardinality

- A Planned Shutdown may link zero or more Repair Campaigns.
- A Repair Campaign may link zero or more Planned Shutdowns.
- Both aggregates may exist independently.
- `planned_shutdown_campaigns` implements the many-to-many association with an active-row uniqueness constraint.
- Each shutdown-required Work Order identifies exactly one operational Planned Shutdown through `planned_shutdown_id` and one canonical source through `shutdown_work_item_id` when created from shutdown scope.
- A campaign work item that requires multiple windows produces separate executable Work Orders per window; the canonical work item remains one record and prevents accidental duplicates within the same window.

## Lifecycle Models

### Planned Shutdown

Use a dedicated `PlannedShutdownStatus`, not the shared `PlanStatus` used by PPR:

`DRAFT -> SCOPE_FORMATION -> READINESS_CHECK -> PENDING_APPROVAL -> APPROVED -> PREPARATION -> SHUTDOWN_STARTED -> SAFE_STATE -> REPAIR_IN_PROGRESS -> TESTING -> STARTUP -> COMPLETED -> CLOSED`

Side transitions are `CANCELLED`, `RESCHEDULED`, and `EMERGENCY_EXTENDED`. A transition policy maps source, target, required fields, blockers, and required permission. Every accepted transition writes `planned_shutdown_status_history` in the same transaction.

### Repair Campaign

Use:

`DRAFT -> SCOPE_FORMATION -> RESOURCE_CHECK -> PENDING_APPROVAL -> APPROVED -> PREPARATION -> IN_PROGRESS -> COMPLETED -> CLOSING -> CLOSED`

Side transitions are `SUSPENDED` and `CANCELLED`. Approved scope, stages, budget, links, or critical work changes invalidate the approval snapshot and return the campaign to `PENDING_APPROVAL` or `SCOPE_FORMATION` according to the mutation type.

## Safety and Start Policy

All safety checks are fail-closed.

A Work Order with `requires_shutdown` or `requires_isolation` can enter `IN_PROGRESS` only when:

1. `planned_shutdown_id` is present;
2. shutdown status is `SAFE_STATE` or `REPAIR_IN_PROGRESS`;
3. current time is within the effective approved window, including an audited extension;
4. production and HSE approval facts are current for the approved scope version;
5. required isolation points and permits are active;
6. a safety checklist exists and all critical items passed;
7. required critical materials are reserved;
8. an eligible performer or approved contractor is assigned.

Missing configuration is a blocker, not an implicit pass. The service returns stable blocker codes for UI display and testing.

## Idempotency and Concurrency

- Generated Work Orders carry a server-generated `generation_key` derived from source work item, stage, equipment, and shutdown window.
- A partial unique index on active `generation_key` rows prevents sequential and concurrent duplicates.
- Bulk generation accepts an `Idempotency-Key`, records the result, and returns the prior canonical result for a replay.
- Bulk creation is transactional; any item failure rolls back all new Work Orders and idempotency output.
- Planned Shutdown and Repair Campaign entities receive optimistic `@Version` columns.
- Approval entities retain their existing optimistic versions.
- Material reservations use a canonical `(work_order_id, requirement_id, spare_part_id)` uniqueness rule so campaign and shutdown views cannot reserve twice.

## Financial Model

- Replace campaign/stage request, entity, DTO, calculation, and database `double precision` money fields with `BigDecimal` backed by `numeric(19,4)`.
- Add ISO-4217 `currency_code`, defaulted from configured system currency for existing rows.
- Actual cost remains ledger-derived; campaign/stage cached totals are removed or treated as rebuildable projections.
- Cross-currency posting is rejected unless an immutable approved FX snapshot is supplied.
- Budget overrun creates an approval requirement and blocks campaign closure until resolved.

## Authorization

Add explicit permissions for:

- Planned Shutdown create, update scope, request approval, approve, prepare, confirm safe state, start repair, test, start up, close, cancel, reschedule, and extend;
- Repair Campaign create, update scope, manage resources, request approval, approve, start, suspend, complete, close, cancel, and generate Work Orders.

Controllers use `@PreAuthorize`; services also enforce department scope and separation of duty. Approval fallback permissions become domain-specific rather than `WORK_ORDER_APPROVE`. The requester cannot approve their own critical production/HSE approval step unless an explicit emergency override permission is used and audited.

## Data Model Additions

Forward-only Flyway migrations introduce:

- expanded `planned_shutdowns` metadata, actual timestamps, version, risk/extension fields, and dedicated status constraint;
- `planned_shutdown_assets`;
- `planned_shutdown_work_items`;
- `planned_shutdown_readiness_items`;
- `planned_shutdown_isolation_points`;
- `planned_shutdown_status_history`;
- `planned_shutdown_startup_tests`;
- `planned_shutdown_campaigns`;
- Work Order shutdown/source/generation fields and indexes;
- Repair Campaign owner, priority, objective, version, currency, approval-scope version, and closure fields;
- `repair_campaign_work_items`, dependencies, resource assignments, and closure evidence;
- outbox events and idempotency records where an existing generic table cannot be reused.

Migrations backfill existing records conservatively. Historical rows that cannot satisfy new operational fields remain non-startable until explicitly remediated; migrations do not invent safety evidence.

## API Design

Existing endpoints remain compatible where feasible. New APIs are grouped under:

- `/api/v1/planned-shutdowns/{id}` for detail, scope, readiness, safety, approvals, transitions, Work Orders, tests, startup, closure, and report;
- `/api/v1/repair-campaigns/{id}` for work items, dependencies, materials, resources, risks, approvals, transitions, closure, and report;
- bulk generation endpoints require idempotency keys and return canonical created/existing item results;
- transition failures return structured blocker codes and human-readable messages;
- update conflicts return HTTP 409 with the current optimistic version.

DTOs are typed; frontend code does not use `any` for these new contracts. Pagination response shapes match backend `Page` responses.

## Frontend Design

### Planned Shutdown workspace

Add `/planned-shutdowns/:id` with status-aware tabs:

- Overview;
- Scope Boundary;
- Work;
- Equipment;
- Campaigns and Work Orders;
- Materials and Performers;
- Readiness;
- Safety and Isolation;
- Approvals;
- Testing and Startup;
- Closure and Plan-Fact;
- Audit History.

The create form sends the selected equipment/scope data to a real backend contract. Buttons are permission-aware but backend authorization remains authoritative.

### Repair Campaign workspace

Extend the existing detail page with Materials, Resources, Schedule/Dependencies, Risks, Files, Closure, and Dashboard views. Actions render blocker reasons from backend policy responses. Approval-invalidating edits display the required reapproval outcome before submission.

## Reporting

- Shutdown report derives campaign, Work Order, material, cost, defect, and downtime facts by canonical IDs.
- Campaign report references shutdown downtime rather than copying it.
- Work Order, reservation, cost, and defect aggregations deduplicate by canonical primary key.
- Closure produces an immutable snapshot with source IDs and scope version for later reconciliation.

## Extended Capabilities

After core invariants pass:

- deterministic configurable risk scoring;
- overrun detector and escalation notifications;
- dependency DAG validation and critical-path calculation;
- lessons learned and repeated-defect analytics;
- operational dashboards based on the canonical reporting read model.

## Error Handling and Audit

- Domain policy violations use stable codes and 400/409 responses.
- Unauthorized operations return 403.
- Concurrency conflicts return 409.
- Every lifecycle, approval-invalidating mutation, override, generation, link/unlink, reschedule, extension, safe-state, startup, and closure operation writes actor, timestamp, old/new snapshot, scope version, and correlation/idempotency key.
- Outbox delivery is retryable and idempotent; failed delivery never silently changes the source aggregate transaction.

## Test Strategy

Implementation follows test-driven development. Each behavior starts with a failing test, then the minimal implementation, then refactoring.

Required layers:

- migration contract tests and PostgreSQL/Testcontainers integration tests;
- repository uniqueness/concurrency tests;
- domain service transition and blocker tests;
- controller authorization and API contract tests;
- frontend payload, permission, action-state, and component tests;
- Playwright E2E for the critical shutdown-to-repair-to-startup path where the existing harness supports it.

All 25 negative scenarios from the audit are mapped to named automated tests. No skipped or assertion-free test counts toward a stage gate.

## Stage Gates

### Stage 1 - Safety and data integrity

Deliver RBAC, fail-closed safety foundation, approved-only/idempotent generation, concurrency foundations, and monetary precision/currency. Gate on all new P0 negative tests, targeted backend/frontend suites, build, and migration contracts.

### Stage 2 - Planned Shutdown core

Deliver the full shutdown aggregate, scope, readiness, isolation, lifecycle, timing, Work Order linkage, startup, closure, and API. Gate on PS-01 through PS-14 service/controller/integration tests.

### Stage 3 - Repair Campaign and cross-module integration

Deliver campaign work items, resources, materials, dependencies, approval invalidation, PS relationships, status/date propagation, and uniqueness. Gate on RC and X negative/concurrency tests.

### Stage 4 - UI, reporting, and analytics

Deliver both operational workspaces, closure/reporting read models, plan-fact UI, and E2E paths. Gate on frontend tests, build, relevant E2E, and reconciliation tests.

### Stage 5 - Extended capabilities

Deliver risk, overrun escalation, critical path, lessons learned, and advanced dashboards. Gate on deterministic formula fixtures, notification/outbox tests, and full regression suites.

## Completion and Publishing

After Stage 5:

1. write `docs/audits/2026-07-11-toir-business-logic-remediation-completion.md` in both repositories with commits, migrations, APIs, UI, tests, remaining limitations, and updated coverage;
2. run final backend and frontend verification;
3. confirm clean worktrees and review commit history;
4. push both local `Codex_org` branches to `origin/Codex_org`;
5. report exact pushed commit IDs and test results.
