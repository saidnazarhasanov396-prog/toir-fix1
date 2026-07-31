# PPR End-to-End Lifecycle Design

**Date:** 2026-07-30  
**Status:** Approved design  
**Repositories:** `toir-backend`, `toir-frontend`

## 1. Purpose

Implement one controlled maintenance lifecycle:

```text
Planning session
  -> named calculation variants
  -> one selected immutable variant
  -> approval of that exact variant
  -> operational PPR plan and tasks
  -> work orders generated N days in advance
  -> execution
  -> independent final acceptance
  -> completion act, evidence and signatures
  -> closure
```

The system must not permit a shortcut around approval, work-order execution, final
acceptance, or the signed completion act.

## 2. Confirmed Product Decisions

1. Draft calculations live in a separate `PprPlanningSession`; they are not
   operational `PprPlan` records.
2. A session may contain several manually named variants.
3. The user selects one variant. Approval is bound to its exact revision and hash.
4. `PprPlan` and `PprTask` records are created only after final approval.
5. `workOrderLeadDays` is configured in the maintenance regulation, for example
   `7`, and is copied into the approved task snapshot.
6. Approved tasks are executed only through their work orders.
7. A work order cannot be closed until final acceptance is accepted, a completion
   act exists, required evidence is attached, and all required signatures exist.
8. Application access remains permission-based. This project does not introduce
   hard-coded business roles.

## 3. Scope

### In scope

- Planning sessions, named variants, selection and immutable approval binding.
- Post-approval materialization of one operational PPR plan and its tasks.
- Regulation-level work-order lead time and evidence policy.
- Automatic, idempotent creation of due work orders.
- A single authoritative task/work-order lifecycle.
- Independent final acceptance, including rejection and rework.
- Structured completion-act evidence and signatures.
- Strict close-readiness validation.
- Migration and containment of legacy paths.
- Backend, frontend, integration, concurrency, and contract tests.

### Non-goals

- Automatic optimization or ranking of variants.
- Replacing the existing permission subsystem.
- A new binary-file store; existing file assets are reused.
- Rebuilding the general approval engine.
- Recalculating or deleting historical plans, tasks, work orders, or acts.

## 4. Domain Model

### 4.1 Planning session

`PprPlanningSession` is the draft and approval aggregate.

Required fields:

- `id`, `name`, `year`, `departmentId`;
- planning window and optional notes;
- `status`;
- `selectedVariantId`;
- `approvedPlanId`;
- audit/version/soft-delete fields.

Statuses:

- `DRAFT`
- `READY_FOR_SELECTION`
- `SELECTED`
- `PENDING_APPROVAL`
- `APPROVED`
- `REJECTED`
- `CANCELLED`

Only `DRAFT`, `READY_FOR_SELECTION`, and `SELECTED` sessions may change. Submission
freezes the selected revision. A rejected session may be copied into a new revision;
the rejected revision is not edited in place.

### 4.2 Named variants

`PprPlanningVariant` belongs to one session and contains:

- user-defined unique name within the session;
- monotonically increasing `revision`;
- deterministic `contentHash` and `hashVersion`;
- calculation inputs and summary metrics;
- `status`;
- immutable `PprPlanningVariantItem` rows for the current revision.

Variant statuses:

- `DRAFT`
- `CALCULATED`
- `SELECTED`
- `NOT_SELECTED`
- `SUPERSEDED`

An item snapshots every value required later to create a task:

- equipment, regulation, rule, template and department identifiers;
- historical display values;
- maintenance and trigger types;
- planned date, scheduled start/end and due date;
- task title, labor, priority and source-item key;
- resolved `workOrderLeadDays`;
- resolved required evidence types.

Changing a variant creates a new revision and complete new item set. Historical
revisions are immutable.

### 4.3 Approval binding

The approval target is `PPR_PLANNING_SESSION`. Its request stores:

- `planningSessionId`;
- `selectedVariantId`;
- `variantRevision`;
- `contentHash`;
- `hashVersion`;
- resolved route/template identity.

Every approve/reject command revalidates that binding. If selection or content has
changed, the request is stale and the command fails with
`PPR_PLANNING_APPROVAL_STALE`.

Final approval and materialization run in one transaction under the same aggregate
lock. Repeated finalization returns the existing `approvedPlanId`; it never creates
a second plan or duplicate tasks.

### 4.4 Operational plan and tasks

Final approval creates exactly one `PprPlan` and the selected variant's `PprTask`
rows. Traceability fields include:

- `PprPlan.planningSessionId`;
- `PprPlan.sourceVariantId`;
- `PprPlan.sourceVariantRevision`;
- `PprTask.sourceVariantItemId`;
- `PprTask.workOrderLeadDays`;
- `PprTask.requiredEvidenceTypes`;
- `PprTask.workOrderRequired = true`.

The operational plan is not editable in a way that changes approved scope. A change
to scope requires a controlled revision flow rather than direct task mutation.

## 5. Regulation Configuration

`MaintenanceRegulation` gains:

- `workOrderLeadDays`, integer, required, default `7`, allowed range `0..365`;
- `requiredCompletionEvidenceTypes`, a normalized set, empty by default.

An optional nullable rule-level override may be added to
`EquipmentMaintenanceRule`. Resolution is:

```text
rule override -> regulation value -> default 7
```

The resolved values are copied first into variant items and then into PPR tasks.
Changing a regulation affects future calculations only; it does not move the
work-order date or evidence rules of an already approved task.

## 6. Automatic Work-Order Generation

A scheduled service processes tasks whose generation date has arrived:

```text
generationDate = localDate(scheduledStart, businessTimezone) - workOrderLeadDays
```

A task is eligible when:

- its plan is operational and not cancelled/closed;
- the task is approved and not completed/cancelled;
- `workOrderRequired = true`;
- `generationDate <= businessDate`;
- it has no active, non-deleted work order.

Safety requirements:

- partial unique database index on active `work_orders.ppr_task_id`;
- batch query with row locking (`FOR UPDATE SKIP LOCKED`);
- reuse the canonical generated-work-order creation service;
- retry-safe transactions and stable generated source key;
- audit event and metrics for created, skipped and failed rows;
- configurable cron, timezone, batch size and system actor;
- manual “generate due now” command uses the same eligibility service.

The system never creates a work order early merely because a plan was opened.

## 7. One Execution Authority

Once tasks are materialized, their execution state is derived from the work order.
Direct task completion/cancellation endpoints reject mutation with
`PPR_TASK_MANAGED_BY_WORK_ORDER`.

Canonical synchronization:

```text
Work order CREATED/ASSIGNED  -> task APPROVED
Work order IN_PROGRESS       -> task IN_PROGRESS; plan IN_PROGRESS
Work order COMPLETED         -> task awaiting final acceptance
Acceptance REJECTED          -> work order REWORK_REQUIRED; task IN_PROGRESS
Acceptance ACCEPTED          -> work order remains COMPLETED; task IN_PROGRESS
Work order CLOSED            -> task COMPLETED; terminal closed work
All plan tasks completed     -> plan CLOSED
```

`REWORK_REQUIRED` is an explicit work-order status. Rework returns through
`IN_PROGRESS -> COMPLETED -> final acceptance`; the rejected acceptance remains in
history.

## 8. Independent Final Acceptance

The frontend must never auto-create or auto-accept final acceptance while closing a
work order.

Acceptance is a separate command and audit record. The backend uses permissions and
separation-of-duty validation; it does not hard-code job titles. At minimum, the
same actor who completed the work may not perform final acceptance unless an
explicit elevated override permission and reason are supplied.

Final acceptance captures:

- accepted or rejected result;
- remarks and quality rating;
- run-in/test result when applicable;
- defects and corrective instructions;
- actor and timestamp.

An accepted final acceptance automatically creates an idempotent draft completion
act. A rejected acceptance moves the work to rework and cannot satisfy close
readiness.

## 9. Completion Act, Evidence and Signatures

`CompletionAct` is a first-class record linked one-to-one with the work order.
Creation is idempotent and its number is generated through the existing numbering
mechanism.

`CompletionActEvidence` references the existing file asset and stores:

- evidence type: `BEFORE_PHOTO`, `AFTER_PHOTO`, `MEASUREMENT`, `DOCUMENT`,
  `REPAIR_ACT`, `STOPPAGE_ACT`, or `OTHER`;
- caption and optional captured timestamp;
- uploader and audit timestamps.

`CompletionActSignature` stores:

- signature responsibility: `PERFORMER`, `ACCEPTOR`, optionally `CUSTOMER`;
- signer, timestamp and signature method;
- immutable signed act revision/hash.

Required responsibilities are performer and acceptor. A permission may authorize a
user to sign for a responsibility, but one person cannot fill both required
responsibilities without an explicit override permission and reason.

An act becomes `SIGNED` only when:

- final acceptance is accepted;
- all regulation-required evidence types are present;
- at least one evidence item exists when the policy requires evidence;
- all required signature responsibilities are present;
- signatures reference the current immutable act content hash.

Changing act content after a signature creates a new revision and invalidates old
signatures for close-readiness purposes.

## 10. Close-Readiness Rule

The backend is authoritative. `WorkOrderService.close` must require all of:

1. work order status is `COMPLETED`;
2. latest final acceptance is `ACCEPTED`;
3. completion act exists;
4. act content is complete;
5. required evidence is present;
6. current act revision has all required signatures;
7. there are no unresolved acceptance defects;
8. linked task/plan synchronization succeeds in the same transaction.

Failures return stable machine-readable codes and the frontend renders the same
readiness checklist. The UI may guide the user, but hiding a button is never the
only enforcement.

## 11. API and User Experience

### Planning

- Session registry with status, selected variant and approved plan link.
- Session workspace with create/rename/copy/delete-draft variant actions.
- Calculation input editor and server-generated preview.
- Side-by-side summary comparison and item-level diff.
- Explicit “Select variant” action.
- Read-only approval screen showing exact revision/hash and full item snapshot.
- Final approval redirects to the resulting operational PPR plan.

### Regulation

- Numeric “Create work order in advance, days” field, default `7`.
- Evidence requirements selector.
- Validation help and resolved values in schedule preview.

### Execution

- Work-order readiness card showing generation date and lifecycle blockers.
- Separate completion, final acceptance, act editing, evidence and signing actions.
- Rework banner and acceptance history.
- Act editor/print view that uses the same persisted content.

## 12. Legacy Transition

- Existing PPR plans and tasks are tagged `LEGACY_IMPORTED` or equivalent and are
  not rebuilt.
- Existing calculations remain readable.
- The legacy direct PPR-create endpoint is unavailable for
  `MAINTENANCE_SCHEDULE` origin after the feature is enabled.
- Legacy bulk “generate work orders for plan” delegates to the due-task eligibility
  service and cannot generate future work.
- Existing closed work orders remain unchanged.
- Open legacy work orders get a compatibility readiness mode until their data is
  migrated; the mode is visible and measured, not silently inferred.
- A reconciliation report identifies missing/duplicate work orders, diverged
  task/plan statuses, accepted work without acts, and unsigned acts.

Rollout uses separate flags for planning sessions, due work-order generation, and
strict closure. Strict closure is enabled only after reconciliation and migration
of active legacy records.

## 13. Observability and Audit

Audit events are required for variant calculation, selection, submission, approval
binding failure, materialization, scheduled generation, acceptance, rework, act
revision, evidence changes, signing and closure.

Metrics include:

- due tasks scanned/created/skipped/failed;
- overdue tasks without work orders;
- stale approval attempts;
- materialization retries;
- completed work awaiting acceptance;
- accepted work awaiting signed act;
- reconciliation discrepancy counts.

## 14. Acceptance Criteria

- A session can hold two or more named variants without creating PPR tasks.
- Only one selected revision can be submitted.
- Approval of stale or changed content is rejected.
- Repeating final approval produces one plan and one task per selected item.
- A regulation value of `7` generates the work order exactly seven calendar days
  before scheduled start in the configured timezone.
- Concurrent scheduler runs cannot create duplicate work orders.
- A task cannot be completed directly.
- Completing work does not automatically accept it.
- Rejected acceptance enters rework and preserves history.
- Closing fails without accepted final acceptance, required evidence, and current
  performer/acceptor signatures.
- Successful close synchronizes the work order, task and plan.
- Existing historical records are not deleted or regenerated.
