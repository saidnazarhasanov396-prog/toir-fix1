# Parallel-All Approval Design

## Goal

Extend the existing approval framework with two runtime flow types:

- `SEQUENTIAL`: preserve the current ordered-step behavior.
- `PARALLEL_ALL`: create one personal task per configured active user and approve the request only after every task in the current round is approved.

The implementation keeps `ApprovalRequest`, `ApprovalStep`, `ApprovalTemplate`, and `ApprovalHistory` as the canonical engine. It does not create a second approval subsystem or rename persisted tables.

## Existing Architecture

- `ApprovalTemplate` and `ApprovalTemplateStep` define an active route by `targetType` and `actionType`.
- `ApprovalRequest` is the runtime approval instance for a business document and action.
- `ApprovalStep` is the persisted runtime assignment and decision record.
- `ApprovalHistory` and the universal audit service preserve status changes.
- `ApprovalService` creates/reuses requests, applies decisions, locks lifecycle domains, and invokes finalization.
- `ApprovalActionExecutor` dispatches terminal work to document-specific `ApprovalActionHandler` implementations.
- `ApprovalScopeService` is the authorization boundary for linked-document scope and step eligibility.
- The frontend reads backend-computed action flags and renders templates, inbox rows, details, progress, and history.

## Persisted Model

### ApprovalTemplate

Add:

- `flowType`: non-null `SEQUENTIAL` or `PARALLEL_ALL`, default `SEQUENTIAL`.
- optimistic `version`: configuration version used for runtime provenance.

For `SEQUENTIAL`, template steps retain their current ordered role/user assignment contract.

For `PARALLEL_ALL`, every active template step must contain exactly one explicit `approverId`; roles are forbidden, the list must be nonempty, user IDs must be unique, and every selected user must exist, be active, and not be deleted.

### ApprovalRequest

Add the immutable-at-runtime snapshot:

- `flowType`;
- `approvalRound`;
- `templateId`;
- `templateVersion`.

`approvalRound` is the next positive ordinal for the same `(targetType, targetId, actionType)`. A repeated start while a compatible request is pending reuses it. A resubmission after a terminal request creates a new `ApprovalRequest` with `max(previous approvalRound) + 1`.

### ApprovalStep

Add `approvalRound` and the `CANCELLED` decision.

The database enforces:

- existing unique `(request_id, step_number)`;
- unique `(request_id, approval_round, approver_id)` when an explicit approver exists.

Existing rows are backfilled with `flowType=SEQUENTIAL` and `approvalRound=1`.

## Route Snapshot

Route resolution returns a value containing `flowType`, template identity/version, and frozen assignments. Request creation never rereads the template after this value is produced.

Runtime decisions use only `ApprovalRequest` and its `ApprovalStep` rows. Editing or replacing the template cannot change an existing request.

The project has department scoping but no tenant/company entity on users or approval templates. Parallel assignees therefore use active/non-deleted user validation, while access to the business document continues through `ApprovalScopeService`.

## Start and Resubmission

Under the existing target/action transaction lock:

1. Validate the document through existing module services.
2. Reuse a compatible pending request when present.
3. Resolve and validate the active template.
4. Allocate the next `approvalRound`.
5. Persist the request snapshot.
6. Persist every task with the same round.
7. Keep module-owned pending-status transitions in their existing domain services.
8. Record history/audit.
9. Notify the current sequential assignee or every parallel assignee.

The current unique pending-request index and target/action lock remain the idempotency backstop.

## Decisions and Concurrency

Every decision locks `ApprovalRequest` with `FOR UPDATE`. Lifecycle targets preserve the current domain-row-first lock order.

### Sequential

The existing current-step behavior remains unchanged, apart from shared rejection-comment validation, round metadata, and DTO fields.

### Parallel-All Approve

1. Resolve the requested step or the authenticated user's task.
2. Require the task to belong to the authenticated user, current request, and current round.
3. Require request and task to be `PENDING`.
4. Mark the task `APPROVED` with actor, timestamp, and comment.
5. Recalculate only tasks in the current round.
6. If any task is pending, keep the request pending.
7. If every task is approved, mark the request approved and invoke the existing terminal action exactly once in the same transaction.

### Parallel-All Reject

Reject requires a nonblank comment. The selected task becomes `REJECTED`; all other pending tasks in the current round become `CANCELLED`; the request becomes `REJECTED`; existing document handlers perform the terminal rejection action.

### Return

`returnToStep` rejects `PARALLEL_ALL` with the stable conflict code `PARALLEL_APPROVAL_RETURN_NOT_SUPPORTED`. Backend action metadata excludes `RETURN`, and the frontend never renders it.

Repeated decisions return a controlled conflict. The request lock, persisted `executed` flag, and `executeOnce` contract prevent duplicate finalization.

## API Contract

Keep existing endpoints and add `GET /api/v1/approvals/my-tasks`.

Extend request/template DTOs with:

- `flowType`;
- `approvalRound`;
- template provenance;
- `totalApprovers`, `approvedCount`, `pendingCount`, `rejectedCount`;
- `currentUserTaskId`;
- `allowedActions`.

Step DTOs expose `approvalRound`; their existing `decision`, actor, timestamp, and comment fields represent task status and evidence.

Legacy `canApprove`, `canReject`, and `canCancel` remain for compatibility and are derived from `allowedActions`.

## Frontend

- Template form adds a flow-type selector.
- Sequential mode retains the ordered-step editor.
- Parallel mode uses active users from `/users?status=ACTIVE`, supports multiple selection, and prevents duplicates and an empty selection.
- Inbox and detail views show a parallel badge and `approvedCount / totalApprovers` progress.
- Detail/history renders all approver tasks, including cancelled tasks.
- Decision calls use `currentUserTaskId`, never the sequential `currentStep` heuristic for parallel routes.
- Actions are shown only when present in backend `allowedActions`.
- All new strings are added to Russian, Uzbek, and English locale files.

## Tests

Backend tests cover template validation, route snapshots, N-task creation, partial and final approval, first rejection cancellation, actor isolation, resubmission rounds, historical-task isolation, template immutability, controlled repeat decisions, return rejection, callback exactly-once, and concurrent final approvals.

Frontend tests cover form payloads/validation, parallel runtime integrity and progress, action-task selection, badges/cards, and preservation of sequential behavior.

## Rollout

The Flyway migration is additive and forward-safe:

1. Add nullable/defaulted columns and `CANCELLED` decision compatibility.
2. Backfill existing templates, requests, and steps as sequential round 1.
3. Add non-null constraints and indexes.
4. Deploy backend before frontend.
5. Existing sequential routes continue unchanged.

Rollback is application-level: stop creating parallel templates and deploy the previous application while retaining additive columns. Completed parallel data must not be deleted or rewritten.
