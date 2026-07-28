# Annual Maintenance Schedule Approval-First Task Materialization

**Date:** 2026-07-28
**Status:** Approved design
**Repositories:** `toir-backend`, `toir-frontend`
**Source audit:** `docs/audits/TOIR_BUSINESS_LOGIC_CURRENT_STATE_DELTA_AUDIT_2026-07-28.md`

## 1. Purpose

Annual Maintenance Schedule calculations must approve a deterministic calculated work scope before real `PprTask` entities exist.

For new calculations:

1. create or recalculate an immutable, versioned calculation snapshot;
2. bind approval to the exact calculation revision and content hash;
3. allow the current approval-step engineer to amend the calculation through a dedicated command;
4. reject stale approval decisions;
5. materialize `APPROVED` tasks atomically and exactly once on final approval.

This design closes the current F-03 approval-content-integrity gap, selects the F-06 automatic task-release model, and removes pre-approval task materialization for new Annual Maintenance Schedule calculations.

## 2. Scope

### In scope

- Versioned relational calculation snapshot items.
- Monotonic calculation revisions and versioned deterministic SHA-256 hashing.
- Exact revision/hash/route/context approval binding.
- Dedicated review-amend and return-for-rework commands.
- `CALCULATED` plan status and backend-derived effective lifecycle.
- Atomic and idempotent final task materialization.
- `APPROVED` task creation without individual second approval.
- Additive list/detail DTO capabilities and blocked-reason codes.
- Additive migrations with deterministic legacy backfill.
- Backend and frontend tests written but not executed.
- A production enablement gate for F-01 date semantics.

### Non-goals

- Work-shift or calendar implementation.
- F-01 date-formula correction itself.
- F-04 completion orchestration.
- F-05 cancel/postpone Work Order and reservation propagation.
- F-08 diagnostics expansion.
- F-09 Master Console permissions.
- F-10 WMS/Finance product boundaries.
- Notification navigation changes.
- Conversion or regeneration of existing legacy calculations and tasks.
- Unrelated approval-engine or security refactoring.

## 3. Compatibility Modes

`PprPlan` gains a `materializationMode` dimension:

- `LEGACY_MATERIALIZED`
- `APPROVAL_FIRST`

Every row existing before these migrations is deterministically backfilled to `LEGACY_MATERIALIZED`.

Legacy behavior remains unchanged:

- existing task IDs, statuses, and relations are preserved;
- no legacy snapshot items are required;
- no task is deleted, recreated, or rematerialized;
- legacy approval uses the existing generator/finalizer path;
- existing `GENERATED` plans are not converted to `CALCULATED`;
- legacy calculations do not receive review-amend capabilities;
- manual PPR CRUD and approval behavior remains unchanged.

Only `MaintenanceScheduleCalculationService` may explicitly create a new Annual Maintenance Schedule plan with `APPROVAL_FIRST`. No new legacy Annual Maintenance Schedule calculation creation path is exposed.

## 4. Persisted Status Dimensions

### Plan status

`PlanStatus.CALCULATED` is added. `GENERATED` keeps its current meaning and is not used by approval-first calculations.

Approval-first lifecycle:

```text
DRAFT -> CALCULATED -> effective PENDING_APPROVAL -> APPROVED
```

`PENDING_APPROVAL` is not persisted as `PlanStatus`. It is derived when an actionable approval request exists and its revision/hash binding matches the current calculation.

After a successful snapshot:

- `plan.status = CALCULATED`;
- `taskMaterializationStatus = NOT_MATERIALIZED`;
- active `PprTask` count is zero.

### Materialization status

`TaskMaterializationStatus` values:

- `NOT_APPLICABLE`
- `NOT_MATERIALIZED`
- `MATERIALIZED`

V1 does not persist a materialization-failed status. Technical database or infrastructure exceptions roll back the transaction and preserve the previous materialization state. A persistent failure state may be introduced only with a separately designed recovery and retry workflow.

### Effective calculation lifecycle

The backend computes, and the frontend consumes:

- `DRAFT`
- `CALCULATED`
- `PENDING_APPROVAL`
- `RETURNED`
- `REJECTED`
- `APPROVED`

`SUPERSEDED` is an approval-request history status, not a current calculation lifecycle.

Effective lifecycle precedence is:

1. `APPROVED`;
2. `PENDING_APPROVAL`;
3. `RETURNED` or `REJECTED`;
4. `CALCULATED`;
5. `DRAFT`.

An active approval request counts as `PENDING_APPROVAL` only when its persisted revision/hash binding matches the current calculation. Creating a new content revision clears the current-lifecycle effect of older returned or rejected requests; those outcomes remain visible only in approval history.

## 5. Relational Snapshot Model

### Table

`maintenance_schedule_calculation_items` is the materialization source of truth.

Required columns:

- `id`
- `plan_id`
- `calculation_revision`
- `source_item_key`
- `source_item_key_version`
- `equipment_id`
- `regulation_id`
- `maintenance_rule_id`
- `template_id`
- `maintenance_type`
- `trigger_type`
- `trigger_discriminator`
- `cycle_ordinal`
- `planned_date`
- `scheduled_start`
- `scheduled_end`
- `due_date`
- `normative_labor_hours`
- `priority`
- `department_id`
- historical equipment code/name
- historical regulation/rule/template display values required for approval history
- deterministic task-title snapshot
- `created_at`

Unique constraint:

```text
(plan_id, calculation_revision, source_item_key)
```

Foreign keys do not cascade delete. `plan_id`, equipment, regulation, rule, template, and task traceability use `RESTRICT` or the project-equivalent soft-delete-safe policy.

### Immutability

The snapshot repository exposes only:

- insert items for a new revision;
- read items for an exact revision.

There are no application update/delete commands. Old revision rows are never overwritten or deleted by edit/recalculate.

Database update/delete rejection triggers should be used if they can be introduced without conflicting with migration and test conventions. Otherwise repository-level immutability and integration contracts enforce the boundary.

## 6. Source Item Identity

`sourceItemKeyVersion` begins at `1`.

Version 1 uses canonical business coordinates:

- equipment ID;
- source regulation/rule/template identity;
- trigger type and discriminator;
- maintenance type;
- planned occurrence date/time;
- deterministic cycle ordinal derived from the rule anchor and interval.

The key does not depend on:

- UI order;
- array index;
- database iteration order;
- the item position within one preview response.

Adding an independent earlier occurrence must not renumber unrelated existing source keys. A key algorithm change introduces a new key version; historical keys are not recomputed.

## 7. Calculation Revision and Content Hash

New approval-first calculation:

```text
calculationRevision = 1
calculationContentHashVersion = 1
```

Every content-changing edit/recalculation increments the revision exactly once and inserts a complete new revision snapshot.

Content includes at least:

- plan name and notes;
- start/end period;
- scope and enforced department;
- sorted selected equipment/equipment types/regulations;
- anchor/first-date rules;
- normalized immutable snapshot items;
- scheduled dates, labor, priority, rule/template references, and other task-relevant fields.

`MaintenanceScheduleContentHasher` is a pure component. Each hash version has a separate canonical serializer.

Version 1:

- normalizes nulls, strings, numbers, IDs, dates, and timestamps explicitly;
- sorts IDs;
- sorts items by `sourceItemKey`;
- includes the hash schema version;
- computes lowercase SHA-256.

Task-relevant approved display content can participate in the hash. Localized/decorative labels do not, so translation changes cannot stale an approval.

Old request hashes are verified using the serializer matching `request.calculationContentHashVersion`. They are not recomputed with the newest serializer.

## 8. PprPlan Fields and Constraints

Additive fields:

- `materialization_mode`
- `task_materialization_status`
- `calculation_revision`
- `calculation_content_hash`
- `calculation_content_hash_version`
- `materialized_revision`
- `materialized_task_count`

Database defaults:

- `materialization_mode = LEGACY_MATERIALIZED`
- `task_materialization_status = NOT_APPLICABLE`

Required checks:

- `APPROVAL_FIRST` is allowed only when `origin = MAINTENANCE_SCHEDULE`;
- approval-first revision is at least 1;
- approval-first content hash is non-null and SHA-256-shaped;
- approval-first hash version is at least 1;
- `MATERIALIZED` requires a non-null materialized revision and non-negative count;
- `NOT_MATERIALIZED` requires a null materialized revision;
- materialized revision cannot exceed current calculation revision;
- legacy rows do not require revision/hash fields.

## 9. PprTask Traceability

`ppr_tasks.source_calculation_item_id` is a nullable foreign key to the snapshot item table.

A partial unique index on non-null values guarantees at most one task per snapshot item.

The foreign key uses `ON DELETE RESTRICT`. Materialized task existence therefore prevents deletion of its approved source item.

Generated approval-first task fields are copied directly from the approved snapshot:

- equipment;
- regulation/rule/template relationship;
- scheduled start/end;
- due date;
- normative labor;
- priority;
- department;
- task title;
- plan relationship.

No task is reconstructed by parsing a human title or localized message.

## 10. Approval Request Binding

Additive nullable `ApprovalRequest` fields:

- `calculation_revision`
- `calculation_content_hash`
- `calculation_content_hash_version`
- `resolved_route_fingerprint`
- `requester_context_fingerprint`
- `resolution_code`
- `superseded_by_request_id`

Legacy approval requests may keep these fields null.

The current schema is canonical for existing approval metadata and must be reused:

- route binding uses existing `template_id` and `template_version`;
- terminal return reason, actor, and timestamp use existing `last_return_comment`, `last_returned_by`, and `last_returned_at`;
- technical/business failure detail uses existing `failure_reason`.

No duplicate approval-template or return-resolution columns are created. `resolution_code` is the only new typed terminal-reason discriminator because the current schema has no canonical typed equivalent.

### Route and context fingerprints

The route fingerprint covers the resolved route, flow type, ordered/parallel step identities, template ID, and template version.

The requester context fingerprint is deterministic from enforced identifiers:

- requester user ID;
- enforced department/data scope;
- tenant/site context when available;
- approval action;
- applicable permission/scope-policy version.

Names and localized labels are excluded.

### Reuse

A pending request is reusable only when all of these match:

- target type and ID;
- action;
- revision;
- hash and hash version;
- template ID/version;
- resolved route fingerprint;
- requester context fingerprint;
- `status = PENDING`.

Any mismatch creates a new request/round. Rejected, returned-for-rework, cancelled, approved, failed, expired, or superseded requests are never reused.

## 11. Approval Status and Resolution

`ApprovalStatus.SUPERSEDED` is added as a global terminal status.

A superseded request:

- is not active or actionable;
- is excluded from pending, SLA, escalation, expiry, and cancellation jobs;
- rejects approve/reject/return commands;
- retains prior step decisions for audit;
- can link to a replacement request through nullable `supersededByRequestId`.

Typed supersession resolution codes include:

- `REVIEW_AMEND`
- `STALE_SCOPE`
- `NEW_REVISION`
- `ROUTE_CHANGED`

`failureReason` records failure details. `resolutionCode` records why a request became terminal. They are not interchangeable.

Global `ApprovalStatus.RETURNED` is not added. The current generic return-to-step flow remains non-terminal and keeps the same request `PENDING`.

Typed cancellation resolution codes include:

- `USER_CANCELLED`
- `RETURNED_FOR_REWORK`
- `SYSTEM_CANCELLED`
- `TARGET_DELETED`

`RETURNED_FOR_REWORK` is excluded from ordinary cancellation KPI semantics and is rendered as a domain-specific returned lifecycle.

## 12. Active Request Invariant and Locking

A partial unique index applies only to revision-bound pending requests:

```text
unique(target_type, target_id, action_type)
where status = 'PENDING'
  and calculation_revision is not null
  and is_deleted = false
```

New approval-first requests always persist `action_type = 'APPROVE'`; the index uses that real stored column and no derived “effective action” expression. This does not retroactively affect legacy pending duplicates.

All Annual Maintenance Schedule mutation/finalization flows use the same transaction-scoped target/action advisory lock key:

- approval start;
- regular update/recalculate/delete;
- review-amend;
- terminal return;
- reject;
- final approve;
- stale handling;
- materialization retry.

When a request exists, entity lock order is:

1. `ApprovalRequest FOR UPDATE`;
2. `PprPlan FOR UPDATE`;
3. snapshot/materialization/task state.

When no request exists:

1. target/action advisory lock;
2. `PprPlan FOR UPDATE`;
3. pending request recheck;
4. new request creation.

## 13. Service Boundaries

### `MaintenanceScheduleSnapshotService`

- Converts preview results to immutable task-relevant snapshot items.
- Generates versioned deterministic source keys.
- Inserts one complete revision.
- Reads an exact revision.

### `MaintenanceScheduleContentHasher`

- Pure deterministic canonical serialization and hashing.
- Routes verification by hash version.

### `MaintenanceScheduleCalculationService`

- Orchestrates create, regular edit/recalculate, delete, review-amend, and return-for-rework.
- Enforces advisory locking, status, active approval, RBAC, and department PBAC.
- Branches legacy and approval-first behavior explicitly.

### `MaintenanceScheduleApprovalBindingService`

- Resolves route/template/context.
- Locks and binds approval to exact revision/hash/fingerprints.
- Implements complete reuse matching.

### `MaintenanceScheduleMaterializationService`

- Performs stale verification.
- Loads the exact approved revision.
- Verifies idempotency integrity.
- Creates tasks atomically.
- Delegates legacy calculations to the existing finalizer.

## 14. Command Flows

### Create

1. Enforce authenticated actor and department scope.
2. Preview and reject an empty result.
3. Create plan explicitly as `APPROVAL_FIRST`.
4. Set revision 1.
5. Insert immutable snapshot items.
6. Compute and persist hash/version.
7. Set plan `CALCULATED` and `NOT_MATERIALIZED`.
8. Return zero tasks and capability DTO.

### Regular edit/recalculate

1. Acquire advisory lock.
2. Lock plan and recheck pending request.
3. Require approval-first editable lifecycle.
4. Validate expected revision.
5. Recalculate.
6. Insert revision `n + 1` items.
7. Compute and persist new hash/version.
8. Keep zero tasks and set `CALCULATED`.

### Approval start

1. Preserve generic `POST /approvals`.
2. Detect approval-first Annual Maintenance Schedule.
3. Lock using the canonical key/order.
4. Recompute/verify current hash.
5. Resolve route/template/context fingerprints.
6. Reuse only an exact matching pending request.
7. Otherwise create a new round bound to revision/hash/version/fingerprints.

### Review amend

Endpoint:

```text
PUT /api/v1/maintenance-schedule/calculations/{id}/review-amend
```

Requirements:

- authenticated principal is authoritative;
- current active approval-step assignee;
- approve and update permissions;
- department PBAC;
- expected revision and current approval identity;
- mandatory amendment reason;
- client command ID.

Transaction:

1. Reserve/lock command ID.
2. Acquire advisory and entity locks.
3. Validate actor, revision, request, and scope.
4. Mark old request `SUPERSEDED` with `REVIEW_AMEND`.
5. Preserve old request and step decisions.
6. Insert revision `n + 1` snapshot.
7. Compute the new hash.
8. Create a new approval request/round from route start.
9. Link old request to the new request.
10. Audit revision/hash/request transitions.

Sequential and parallel routes both restart completely.

### Return for rework

Endpoint:

```text
POST /api/v1/maintenance-schedule/calculations/{id}/return-for-rework
```

Requirements:

- authenticated current approver;
- department PBAC and permission;
- mandatory reason;
- expected approval request ID/round;
- client command ID.

Transaction:

- request becomes `CANCELLED`;
- resolution is `RETURNED_FOR_REWORK`;
- reason, actor, and timestamp are persisted;
- plan remains/becomes `CALCULATED`;
- materialization remains `NOT_MATERIALIZED`;
- revision and snapshot do not change;
- no task is created;
- requester receives a distinct return-for-rework notification;
- an audit event is written.

Next submit creates a new request/round.

### Reject

Reject creates no task and leaves the approval-first calculation editable with `NOT_MATERIALIZED`. The rejected request is not reused. A content-changing edit increments revision; a resubmit creates a new round.

## 15. Final Approval and Materialization

Before success side effects, the handler:

1. locks request, plan, and materialization state;
2. validates request revision/hash/hash version against the current plan;
3. loads exact revision items;
4. verifies materialization state.

### Stale outcome

On mismatch:

- request becomes `SUPERSEDED`;
- `resolutionCode = STALE_SCOPE`;
- `failureReason = PPR_CALCULATION_APPROVAL_SCOPE_STALE`;
- plan and materialization metadata do not change;
- no task, approved audit, notification, event, or downstream action occurs.

The transactional service returns a committed typed outcome. After the service proxy commits, the controller maps it to HTTP 409. `REQUIRES_NEW`, `noRollbackFor`, and exception-before-commit patterns are not used.

### Successful materialization

In one transaction:

1. validate revision/hash;
2. load exact revision items;
3. verify no conflicting materialization;
4. create one `PprTask` per item;
5. set every task `APPROVED`;
6. bind every task to `sourceCalculationItemId`;
7. set plan `APPROVED`;
8. set `MATERIALIZED`;
9. set materialized revision;
10. store exact materialized task count;
11. store transactional audit/outbox records only after state is consistent.

`approvedRevision` is not a separate database field. For approval-first calculations it is derived from `materializedRevision`.

External notifications and events are published only after commit through the repository’s canonical outbox or after-commit mechanism. A rolled-back materialization cannot emit an externally visible approval success.

Bulk Work Order generation already accepts `APPROVED` tasks and therefore requires no individual task approval.

### Idempotent finalization

Same-revision success is returned only if:

- `materializedRevision == request.calculationRevision`;
- status is `MATERIALIZED`;
- metadata count equals snapshot item count;
- exactly one task exists per snapshot item;
- no task belongs to another revision;
- plan is `APPROVED` or a valid later lifecycle status.

Otherwise:

- `PPR_CALCULATION_MATERIALIZATION_INCONSISTENT`; or
- `PPR_CALCULATION_ALREADY_MATERIALIZED_FOR_DIFFERENT_REVISION`.

Technical persistence failures roll back all task and plan changes.

## 16. Command Idempotency

`maintenance_schedule_command_results` stores stable outcomes for review-amend and return-for-rework.

Fields:

- `id`
- globally unique `client_command_id`
- `calculation_id`
- `command_type`
- `request_fingerprint`
- `request_fingerprint_version`
- `actor_id`
- `approval_request_id`
- `previous_revision`
- `resulting_revision`
- `resulting_approval_request_id`
- `result_code`
- compact non-sensitive result metadata
- `created_at`
- `completed_at`

The versioned fingerprint includes:

- command type;
- calculation ID;
- expected revision/request/round;
- normalized configuration;
- normalized reason;
- schema version.

JSON property order does not affect it.

Same command ID and fingerprint returns the committed stable result, rehydrated with current capability DTOs. Same ID with another fingerprint returns `PPR_CALCULATION_IDEMPOTENCY_KEY_REUSED`. A different actor/calculation cannot learn the stored result.

Command reservation, domain mutation, and completed result commit in one transaction. Concurrent retries create at most one revision/round.

Retention is documented and configurable. Large payloads, localized text, and sensitive context are not stored as result metadata.

## 17. API Contracts

Existing endpoints remain:

- calculation list/detail/create/update/delete;
- preview;
- generic `POST /approvals`.

No generic `POST /ppr-plans` fallback is introduced.

### Calculation list/detail

Additive fields:

- revision;
- shortened content-hash fingerprint;
- hash version;
- lifecycle status;
- materialization status;
- materialized task count/revision;
- active/latest approval request ID;
- approval round/status;
- approved revision;
- resolution code and last reason;
- current step ID/name;
- `isCurrentApprover`;
- pending timestamp;
- generated Work Order count;
- capabilities and machine-readable blocked reasons.

Capabilities:

- `canEdit`
- `canReviewAmend`
- `canReturnForRework`
- `canSubmit`
- `canDelete`

Blocked reasons include:

- `ACTIVE_APPROVAL_EXISTS`
- `NOT_CURRENT_APPROVER`
- `ALREADY_MATERIALIZED`
- `LEGACY_CALCULATION`
- `DEPARTMENT_SCOPE_DENIED`
- `INVALID_LIFECYCLE_STATUS`

The list query batch-loads/aggregates approval, step, task, and Work Order metadata. It does not issue per-row approval queries.

### Review-amend response

- calculation ID;
- previous/current revision;
- previous/active approval request ID;
- approval round/status;
- lifecycle/materialization status;
- content-hash fingerprint;
- reset decision count;
- `messageCode = APPROVAL_DECISIONS_RESET_AFTER_AMENDMENT`.

Localized text is produced by the frontend.

### Typed 409 errors

Distinct codes:

- `PPR_CALCULATION_APPROVAL_SCOPE_STALE`
- `PPR_CALCULATION_REVISION_CONFLICT`
- `PPR_CALCULATION_MATERIALIZATION_INCONSISTENT`
- `PPR_CALCULATION_ALREADY_MATERIALIZED_FOR_DIFFERENT_REVISION`
- `PPR_CALCULATION_IDEMPOTENCY_KEY_REUSED`
- `APPROVAL_REQUEST_SUPERSEDED`
- `NOT_CURRENT_APPROVER`
- `ACTIVE_APPROVAL_EXISTS`

Stale response metadata:

- approval request ID;
- submitted/current revision;
- shortened submitted/current hash fingerprint;
- current active approval request ID;
- typed retry action.

Retry actions:

- `RELOAD_CURRENT_REVISION`
- `OPEN_ACTIVE_APPROVAL`
- `RESUBMIT_CALCULATION`
- `CONTACT_ADMIN`

Full hashes and canonical payloads are never exposed in list or error responses.

## 18. Frontend

The routed calculation registry and preview flow remains canonical.

The frontend trusts backend lifecycle, capabilities, and block reasons rather than re-deriving permissions.

### Actions

- `CALCULATED`: edit, recalculate, delete, submit.
- Pending creator: edit/delete disabled with explanation.
- Pending current approver: review-amend and return-for-rework.
- `REJECTED` or effective `RETURNED`: edit, recalculate, resubmit.
- `APPROVED` plus `MATERIALIZED`: edit/delete disabled, task count and real navigation.
- Legacy: no review-amend, existing behavior preserved.

Review-amend reuses calculation form/preview components but has a distinct title, mandatory reason, revision/round warning, and dedicated submit label. It calls only the review-amend endpoint.

Return-for-rework uses a separate reason dialog and never invokes generic approval return.

Approval panels show calculation revision, bound revision, round, approved revision, approval/lifecycle/materialization status, task count, and superseded/return reasons.

Superseded approval views disable decision actions and link to the current revision/active approval.

### Error and cache handling

`ApiError` carries typed stale metadata.

On stale 409:

- disable old actions;
- do not retry the mutation;
- invalidate exact calculation list/detail, approval detail/by-target, and plan-task keys;
- reload current revision;
- link to active approval when present;
- show typed recovery UI rather than a network-error toast.

Canonical query-key factories replace reliance on broad invalidation:

- calculation list;
- calculation detail;
- approval detail;
- approvals by target;
- PPR tasks by plan.

Approved calculation navigation is shown only for real task and Work Order destinations returned by the backend.

Unknown future lifecycle/status values use a safe fallback badge.

## 19. Security and Audit

- Authenticated principal is authoritative; actor IDs in request bodies are ignored/not accepted.
- RBAC and department PBAC are enforced server-side for every command.
- Review-amend and return-for-rework require the current active approver.
- Review-amend requires both approve and update authority.
- Expected revision/request/round prevents stale UI overwrite.
- Generic requester self-approval remains a separate F-07 task, but all new commands and final decisions bind actor to the authenticated principal.

Audit events use:

- calculation/plan: `ppr_plan`, `PPR_PLAN` or canonical maintenance-schedule module;
- materialized tasks: `ppr_task`, `PPR_TASK`.

Audit metadata includes actor, old/new revision and hash fingerprints, request IDs, superseded request ID, reason, generated task count, and materialization result.

## 20. Migrations

The existing untracked `V20260728_3__add_mixed_trigger_handling_to_ppr_plans.sql` is not renamed, edited, or merged.

The current working tree already reserves `V20260728_3`; use these exact next versions:

- `V20260728_4`: plan/approval additive fields, enums/status constraints, expand/backfill/default/constrain;
- `V20260728_5`: snapshot items and task traceability;
- `V20260728_6`: command idempotency and revision-bound active-request indexes.

Each migration follows:

1. expand nullable fields;
2. deterministic backfill;
3. safe defaults;
4. NOT NULL/CHECK constraints;
5. indexes and foreign keys.

No migration:

- creates legacy snapshots;
- changes existing task status/ID/relation;
- rematerializes tasks;
- converts existing `GENERATED` plans;
- forces legacy revision/hash values.

## 21. Deployment and F-01 Gate

`CALCULATED` and `SUPERSEDED` are unknown to old application nodes. Rolling deployment must:

1. deploy additive schema support;
2. deploy code that can read the new values to every node;
3. enable approval-first creation only after all nodes are compatible.

The new flow is protected by a server feature flag/capability.

The feature flag defaults to `false`. While disabled:

- create/update commands cannot create a new `APPROVAL_FIRST` record;
- the backend does not silently fall back to creating a new legacy Annual Maintenance Schedule calculation;
- frontend approval-first creation/review-amend actions are hidden or disabled from the server capability;
- existing legacy records remain readable and keep their existing behavior.

F-01 is not fixed by this work. Approval-first production enablement remains off until:

- F-01 date semantics are corrected; or
- PM explicitly authorizes a limited feature-flagged rollout.

Architecture and tests can be delivered while the feature remains disabled.

## 22. Test-First Coverage

Tests are written before production changes but are not executed in this task.

### Backend

- Legacy backfill and safe defaults.
- Manual plans cannot accidentally become approval-first.
- New calculation is explicit approval-first, `CALCULATED`, and zero-task.
- Mode/origin and materialization constraints.
- Immutable old revisions and duplicate source-key rejection.
- Source-key stability under input reordering and independent occurrence insertion.
- Hash ordering, version routing, and localization exclusion.
- Approval binding and exact reuse fingerprints.
- One active revision-bound request.
- Superseded exclusion from action/SLA/escalation.
- Review-amend/return authorization, mandatory reasons, revision conflicts, and response contracts.
- Same-key command retry, conflicting reuse, actor isolation, and concurrent single mutation.
- Stale state commits before HTTP 409 and emits no success side effects.
- External success notifications/events are emitted only after commit.
- Exact same-revision integrity verification.
- Concurrent finalization creates one task set.
- Transaction failure rolls back tasks and command result.
- Legacy finalizer and manual PPR regression.
- Bounded list query count.

### Frontend

- Capability-driven lifecycle/actions and blocked reason tooltips.
- Zero-task calculated display and separate materialization status.
- Review-amend/return reasons and dedicated endpoints.
- Stable command ID per user action and retry.
- New action creates a new command ID.
- Typed idempotency and stale recovery.
- Exact query invalidation and no stale mutation retry.
- Superseded approval navigation.
- Legacy mode and manual PPR regression.
- Feature-disabled behavior.
- Safe unknown-status fallback.
- No generic PPR-plan fallback.

### Migration tests for a later authorized verification

- Empty database.
- Production-like legacy data.
- Legacy pending duplicates.
- Existing generated calculations/tasks.
- Soft-deleted source entities.
- Concurrent uniqueness.

## 23. Verification Constraint

The implementation task must not run:

- Maven;
- backend tests;
- Testcontainers;
- Vitest;
- npm test/build;
- runtime;
- Flyway against a database;
- manual database commands.

Final reporting must say:

- tests written/updated;
- static source and diff reviewed;
- execution **not run / skipped by user instruction**.

It must not claim that tests passed, the build is green, migrations executed successfully, or the TDD cycle completed.

## 24. Acceptance Criteria

- New calculation create/edit/recalculate creates zero `PprTask` rows.
- New snapshot revision is immutable and deterministic.
- Approval binds to exact revision, hash version, route, and requester context.
- Review-amend creates a new revision and request; the old request is superseded.
- Terminal return creates no task and yields editable effective `RETURNED`.
- Stale finalization persists supersession and returns typed 409.
- Final approval creates exactly one `APPROVED` task per approved snapshot item.
- Duplicate/concurrent finalization cannot create duplicate tasks.
- Bulk Work Order generation accepts the materialized tasks.
- Legacy and manual flows remain unchanged.
- Approval-first production enablement remains gated until F-01 is resolved or explicitly accepted.
