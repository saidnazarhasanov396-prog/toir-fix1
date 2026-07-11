# TOiR Stage 2 Planned Shutdown Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` to execute this plan task-by-task. Use `superpowers:test-driven-development` for every behavior change and `superpowers:systematic-debugging` for unexpected failures.

**Goal:** Deliver the production-oriented Planned Shutdown aggregate from PS-01 through PS-14: explicit scope, sources, readiness, isolation, lifecycle, approved/effective timing, shutdown-required Work Order enforcement, startup testing, closure, reporting, typed APIs, and a permission-aware detail workspace.

**Architecture:** Planned Shutdown is the operational source of truth for the shutdown window, boundary, safe-state evidence, lifecycle, startup, and downtime. Child rows retain stable UUID identities and soft-delete semantics. Every lifecycle transition locks the aggregate row, evaluates stable blocker codes, mutates timestamps atomically, writes status history, and emits audit evidence. Work Orders keep execution ownership but reference one operational shutdown and one canonical shutdown work item. Stage 2 does not implement the many-to-many Repair Campaign relationship or outbox synchronization; those are Stage 3 concerns.

**Verified dependencies:** Stage 1 commits through backend `bd6cac67` and frontend `fb914d00`; operation-specific permissions, optimistic-lock HTTP 409 mapping, fail-closed Work Order flags, deterministic generation keys, and exact money contracts are available.

**Tech Stack:** Java 24 runtime / Java 21 source, Spring Boot 3.3.5, Spring Security, Spring Data JPA, PostgreSQL/Flyway, JUnit 5/Mockito/MockMvc, React 19, TypeScript 6, TanStack Query/Table, Vitest 4, Vite 8.

## Global constraints

- Work directly on `Codex_org`; preserve unrelated concurrent changes and stage files explicitly.
- Use forward-only Flyway migrations. Never invent historical safety, approval, isolation, permit, test, or actual-time evidence.
- All safety and readiness policies fail closed and return stable blocker codes.
- Use a dedicated `PlannedShutdownStatus`; do not extend shared `PlanStatus` for the new lifecycle.
- Every aggregate mutation loads the shutdown through a pessimistic row lock and still retains optimistic `@Version` protection for detached updates.
- Do not implement Repair Campaign linking, outbox propagation, shared reservation deduplication, or cross-module reporting until Stage 3.
- Every accepted transition writes history and audit evidence in the same transaction.
- No skipped/assertion-free test counts toward a gate. Docker/PostgreSQL checks are reported as not run when unavailable.

---

### Task 1: Dedicated lifecycle and forward-only schema foundation

**Files:**
- Create: `src/main/java/com/toir/enums/PlannedShutdownStatus.java`
- Create: `src/main/java/com/toir/enums/PlannedShutdownAssetDisposition.java`
- Create: `src/main/java/com/toir/enums/PlannedShutdownWorkItemSourceType.java`
- Create: `src/main/java/com/toir/enums/PlannedShutdownReadinessSeverity.java`
- Create: `src/main/java/com/toir/enums/PlannedShutdownItemStatus.java`
- Create: `src/main/resources/db/migration/V20260711_4__planned_shutdown_core.sql`
- Create: `src/test/java/com/toir/migration/PlannedShutdownCoreMigrationContractTest.java`
- Modify: `src/main/java/com/toir/entity/PlannedShutdown.java`

**Produces:** dedicated status model; expanded aggregate metadata/timestamps; child tables for assets, work items, readiness, isolation, history, startup tests, and closure snapshots; Work Order shutdown/source columns.

- [ ] Write a migration contract test asserting every table, FK, status constraint, unique active-row rule, Work Order link, and conservative backfill.
- [ ] Run the contract test and confirm RED because the migration/enums do not exist.
- [ ] Add the dedicated statuses:
  `DRAFT, SCOPE_FORMATION, READINESS_CHECK, PENDING_APPROVAL, APPROVED, PREPARATION, SHUTDOWN_STARTED, SAFE_STATE, REPAIR_IN_PROGRESS, TESTING, STARTUP, COMPLETED, CLOSED, CANCELLED, RESCHEDULED, EMERGENCY_EXTENDED`.
- [ ] Add metadata columns: unique `code`, shutdown `type`, responsible employee, objective/notes, risk fields, approval scope version/hash, planned and approved windows, effective extension end, actual shutdown/safe-state/repair/test/startup/completion timestamps, reschedule/extension reasons, and closure version.
- [ ] Create child tables with `is_deleted`, audit timestamps, explicit FKs, ordering, and active-row uniqueness. Add `planned_shutdown_id` and `shutdown_work_item_id` to `work_orders` plus supporting indexes.
- [ ] Backfill existing rows to a non-startable compatible state without fabricating evidence; preserve existing planned windows and map old statuses conservatively.
- [ ] Map only the aggregate-root columns in `PlannedShutdown`; later tasks add child entities.
- [ ] Run migration contract and compile; commit `feat: add planned shutdown core schema`.

---

### Task 2: Metadata, detail contract, and scope boundary

**Files:**
- Create: `src/main/java/com/toir/entity/plannedshutdown/PlannedShutdownAsset.java`
- Create: `src/main/java/com/toir/repository/plannedshutdown/PlannedShutdownAssetRepository.java`
- Create: request/response records under `src/main/java/com/toir/dto/plannedshutdown/`
- Modify: `PlannedShutdownRepository.java`, `PlannedShutdownService.java`, `PlannedShutdownController.java`
- Test: `PlannedShutdownServiceTest.java`, `PlannedShutdownControllerContractTest.java`

**Produces:** typed create/detail/update/scope APIs and real STOPPED/RESERVE/RUNNING boundary ownership.

- [ ] Write failing tests for unique code, end-after-start, department/responsible validation, detail retrieval, optimistic version propagation, and equipment scope persistence.
- [ ] Write failing scope tests for duplicate active asset rows, invalid disposition, zero STOPPED/RESERVE assets before readiness, and scope edits outside mutable statuses.
- [ ] Replace create-input reuse of the response DTO with dedicated validated request records.
- [ ] Implement server-generated/validated code, explicit type/owner/risk metadata, typed detail response, locked update, and version conflict behavior.
- [ ] Implement replace/diff scope semantics with stable asset row IDs, mutually exclusive disposition per equipment, department compatibility, and audit/history evidence.
- [ ] Add endpoints: `GET /{id}`, `PUT /{id}`, `GET /{id}/assets`, `PUT /{id}/assets` with exact permissions.
- [ ] Run focused service/controller tests; commit `feat: model planned shutdown scope boundary`.

---

### Task 3: Canonical shutdown work-item sources

**Files:**
- Create: `PlannedShutdownWorkItem` entity/repository/DTOs/policy tests
- Modify: Planned Shutdown service/controller/detail DTO

**Produces:** one canonical record for MANUAL, DEFECT, PPR, WORK_ORDER, and later REPAIR_CAMPAIGN sources; deterministic source identity inside a shutdown.

- [ ] Write failing tests for source-type/source-id validation, manual item requirements, duplicate active canonical source, priority/order, asset-in-scope enforcement, and immutable source identity after approval.
- [ ] Implement `planned_shutdown_work_items` with source type/id, asset, title, priority, shutdown/isolation flags, planned duration, criticality, and active unique source key.
- [ ] Add list/add/update/remove/reorder endpoints under `/{id}/work-items` with `PLANNED_SHUTDOWN_UPDATE`.
- [ ] Ensure removal is blocked when an active generated Work Order references the item.
- [ ] Write audit evidence for source link/unlink and scope-version increments.
- [ ] Run focused tests; commit `feat: add canonical shutdown work items`.

---

### Task 4: Readiness, isolation, permits, and deterministic blocker policy

**Files:**
- Create readiness/isolation entities, repositories, DTOs, and `PlannedShutdownReadinessPolicy`
- Modify Planned Shutdown service/controller
- Test policy, service, and controller contracts

**Produces:** stable blocker codes and fail-closed readiness/safe-state evaluation.

- [ ] Write failing policy tests for: missing boundary, missing responsible actor, no work, missing critical material reservation, unassigned performer/contractor, missing production approval, missing HSE approval, missing isolation point, inactive permit, incomplete critical readiness item, stale scope approval, and outside approved window.
- [ ] Model readiness items with severity `CRITICAL/WARNING`, source, owner, due time, status, evidence/comment, and completion actor/time.
- [ ] Model isolation points with equipment/location, method, lock/tag identifier, responsible actor, status, applied/verified/released timestamps, and permit reference.
- [ ] Implement deterministic response `{canProceed, blockers:[{code,message,entityType,entityId}]}` sorted by code/entity identity.
- [ ] Add CRUD/complete/reopen readiness endpoints and isolation apply/verify/release endpoints with exact permissions.
- [ ] Require all critical items passed and every required isolation point applied+verified before safe-state confirmation.
- [ ] Run focused tests; commit `feat: enforce shutdown readiness and isolation`.

---

### Task 5: Explicit lifecycle, actual timing, reschedule, and emergency extension

**Files:**
- Create: `PlannedShutdownTransitionPolicy`, status-history entity/repository/DTO
- Modify service/controller and approval handler
- Test all legal/illegal transitions and timestamps

**Produces:** complete lifecycle through `CLOSED` plus audited reschedule/extension behavior.

- [ ] Write a table-driven RED test covering every allowed edge and representative illegal edge.
- [ ] Add blocker tests for approval, preparation, shutdown start, safe state, repair start, testing, startup, completion, close, cancel, reschedule, and extend.
- [ ] Implement one locked transition executor that validates expected source status/version, permission-level operation, required reason/evidence, and policy blockers.
- [ ] Set actual timestamps only server-side and once; derive planned/actual downtime from Instants.
- [ ] Persist status history with from/to, actor, reason, old/new effective window, scope version, correlation key, and timestamp.
- [ ] Reschedule returns the operational lifecycle to the appropriate pre-approval state and invalidates the approval snapshot. Emergency extension requires reason, new end, extension permission, and history/audit evidence.
- [ ] Route approval finalization into the explicit `PENDING_APPROVAL -> APPROVED` transition; reject stale scope approval.
- [ ] Add transition endpoints (`prepare`, `start-shutdown`, `confirm-safe-state`, `start-repair`, `start-testing`, `start-startup`, `complete`, `close`, `cancel`, `reschedule`, `extend`).
- [ ] Run lifecycle/controller tests; commit `feat: enforce planned shutdown lifecycle`.

---

### Task 6: Work Order linkage, idempotent generation, and start invariant

**Files:**
- Modify: `WorkOrder.java`, Work Order DTO/request/service/repository/controller as needed
- Modify: Planned Shutdown service/controller
- Create shutdown generation request/result/idempotency tests
- Extend `SafetyChecklistServiceTest` and `WorkOrderServiceTest`

**Produces:** canonical shutdown-required Work Order links, transactional generation, and the full fail-closed start gate.

- [ ] Write RED tests proving flagged Work Orders are blocked for: no shutdown, deleted/cancelled shutdown, ineligible status, outside effective window, stale production/HSE approvals, missing isolation, inactive permit, missing/failed checklist, critical material deficit, and missing eligible performer/contractor.
- [ ] Write RED tests for deterministic generation key, replay, duplicate source/window, concurrent duplicate prevention contract, and full rollback on one invalid item.
- [ ] Map Work Order `plannedShutdownId`/`shutdownWorkItemId` through entity, DTO, create response, and detail APIs.
- [ ] Implement generation from canonical work items with server-derived key `PS:<shutdown>:<workItem>:<windowVersion>`, active unique constraint, required `Idempotency-Key`, deterministic item ordering, and canonical replay results.
- [ ] Compose a shutdown-aware start policy with existing Work Order checklist/material/performer guards. Missing configuration must block only flagged orders; ordinary Work Orders remain compatible.
- [ ] Prevent shutdown cancellation/closure while linked Work Orders are active where policy requires.
- [ ] Run generation/start/security tests; commit `feat: link shutdown work orders and enforce safe start`.

---

### Task 7: Startup tests, production return, closure snapshot, and report

**Files:**
- Create startup-test and closure-snapshot entities/repositories/DTOs
- Create `PlannedShutdownReportService`
- Modify service/controller
- Test startup/closure/report reconciliation

**Produces:** evidence-based startup and immutable plan-fact closure.

- [ ] Write RED tests for required startup tests, failed test blocking startup, active/incomplete Work Orders blocking completion, unreleased isolation blocking closure, missing production sign-off, and repeat closure mutation.
- [ ] Model startup test definition/result, measured value/unit, acceptance criteria, performer/verifier, evidence, timestamp, and pass/fail.
- [ ] Require all mandatory tests passed, isolation released in controlled order, and production return approval before `COMPLETED`.
- [ ] Create one immutable closure snapshot containing source IDs, scope/window version, planned/actual downtime, Work Order IDs/status counts, material/cost canonical IDs, defects/results, extensions, and sign-offs.
- [ ] Derive report facts by canonical IDs and deduplicate; do not copy Repair Campaign downtime or introduce Stage 3 joins.
- [ ] Add testing/startup/closure/report endpoints and exact permissions.
- [ ] Run focused tests; commit `feat: close and report planned shutdowns`.

---

### Task 8: Approval scope snapshot, separation of duty, PBAC, and API hardening

**Files:**
- Modify approval handler/domain permissions/service/controller/security tests
- Add Planned Shutdown policy/security integration tests

**Produces:** current-scope approvals, department scope enforcement, and operation-specific API contracts.

- [ ] Write RED tests for stale scope/version approval, requester self-approval of production/HSE steps, unrelated department access, exact transition permissions, and stable 400/409 blocker payloads.
- [ ] Compute an approval scope hash from operational metadata, effective window, assets, critical work, readiness/isolation requirements, and version.
- [ ] Persist approval scope version/hash when requesting approval; invalidate it on operational mutation/reschedule.
- [ ] Enforce requester/approver separation for critical steps unless an explicit audited emergency override already exists in the permission model; do not silently bypass.
- [ ] Apply department PBAC in service queries and mutations, not only controller button visibility.
- [ ] Ensure every new controller method has an exact compile-time permission expression and security-matrix coverage.
- [ ] Run approval/security tests; commit `feat: harden planned shutdown approvals and access`.

---

### Task 9: Typed Planned Shutdown detail workspace

**Backend dependency:** Tasks 1-8 APIs are green.

**Frontend files:**
- Create: `src/modules/repairs/pages/planned-shutdown-detail-page.tsx`
- Create components under `src/modules/repairs/components/planned-shutdown-detail/`
- Create helpers/tests under `src/modules/repairs/libs/planned-shutdowns/`
- Modify: routes, API types, registry page, create dialog, permissions, and locales

**Produces:** `/planned-shutdowns/:id` with real scope, readiness, safety, lifecycle, testing, closure, and report actions.

- [ ] Write RED payload tests proving the create form sends selected equipment/scope disposition and typed metadata.
- [ ] Write RED permission/status action tests and blocker-code rendering tests.
- [ ] Add typed API records with decimal/version/timestamp fidelity and no new `any`.
- [ ] Add route and status-aware tabs: Overview, Scope Boundary, Work, Equipment, Work Orders, Materials and Performers, Readiness, Safety and Isolation, Approvals, Testing and Startup, Closure and Plan-Fact, Audit History. Defer Repair Campaign linking controls to Stage 3.
- [ ] Render backend blocker codes/messages and current optimistic version; handle 409 with reload/retry guidance.
- [ ] Gate every mutation button with exact permission and status capability; suppress empty menus.
- [ ] Keep registry paging shape aligned with backend `Page`; link rows to detail.
- [ ] Run component/helper/access tests, scoped lint, `tsc --noEmit`, and production build; commit `feat: add planned shutdown operations workspace`.

---

### Task 10: Stage 2 isolated verification, evidence, and final review

**Files:**
- Create matched `docs/audits/2026-07-11-toir-stage-2-verification.md` reports in both repositories.

- [ ] Run PS-01..PS-14 migration, repository, policy, service, controller, security, Work Order start, startup, closure, report, frontend, and build suites from detached verification worktrees so concurrent tasks cannot corrupt build artifacts.
- [ ] Run PostgreSQL/Flyway/concurrency tests when Docker is available; otherwise record the exact absence and do not claim them passed.
- [ ] Run `git diff --check`, inspect branch status/logs, and record unrelated concurrent commits separately.
- [ ] Map every PS-01..PS-14 audit row and negative scenario to named tests/evidence.
- [ ] Write and commit report-only evidence in both repositories.
- [ ] Request independent final code review. Fix all Critical/Important findings test-first and re-review until approved.
- [ ] Invoke `superpowers:writing-plans` for Stage 3 only after Stage 2 approval.

