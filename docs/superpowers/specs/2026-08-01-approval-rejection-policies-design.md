# Approval Rejection Policies Design

## Goal

Add configurable rejection behavior to the existing unified approval flow while leaving document editing and unrelated approval capabilities unchanged.

The supported policies are:

- `TERMINATE` for both `SEQUENTIAL` and `PARALLEL_ALL`;
- `RETURN_TO_PREVIOUS_STEP` for `SEQUENTIAL`;
- `RETURN_TO_INITIATOR` for both flow types;
- `MAJORITY` for `PARALLEL_ALL`.

Existing templates and requests without an explicit policy behave as `TERMINATE`.

## Scope

This change includes only policy configuration, policy-aware runtime transitions, the minimal request/step history needed to preserve decisions across retries, and the frontend controls required to configure and execute those transitions.

It does not include:

- document corrections from the rejection dialog;
- `CORRECT_AND_RETURN` or target-field whitelists;
- document snapshots or field diffs;
- rejection-reason dictionaries;
- force approve/reject/cancel;
- cycle limits or cycle-based escalation;
- SLA or inactivity escalation changes;
- new document-edit permissions;
- unrelated approval or domain-lifecycle refactoring.

## Existing Architecture

- `ApprovalTemplate` and `ApprovalTemplateStep` define a route.
- `ApprovalRequest` snapshots runtime flow metadata and owns mutable current state.
- `ApprovalStep` is the current projection of each assignment and decision.
- `ApprovalHistory` stores append-only request-level events.
- `ApprovalService` locks requests, authorizes actors, applies decisions, notifies participants, and invokes terminal domain handlers.
- `ApprovalRuleService` validates and persists templates.
- The frontend uses backend-provided `allowedActions` and renders shared approval controls through `ApprovalSection`.

The implementation extends these components rather than introducing a second workflow engine.

## Persistence Model

### Policy snapshot

Add `ApprovalRejectionPolicy` with:

- `TERMINATE`;
- `RETURN_TO_PREVIOUS_STEP`;
- `RETURN_TO_INITIATOR`;
- `MAJORITY`.

Add non-null `rejection_policy` columns to `approval_templates` and `approval_requests`, both defaulting to `TERMINATE`. Existing rows are backfilled before the non-null constraint is applied.

The policy is copied from the resolved template into `ApprovalRequest` at creation. Runtime behavior never rereads the template, so editing a rule cannot change an in-flight request.

### Rework state

Add `REWORK` to `ApprovalStatus`. It is nonterminal, non-actionable for approvers, and actionable only through requester resubmission.

Existing analytics and pending-task queries keep their current definitions. `REWORK` is not silently counted as `PENDING` or `REJECTED`.

### Append-only decision evidence

Extend `approval_history` additively with nullable policy-event fields:

- `step_id`;
- `step_number`;
- `approval_round`;
- `decision`.

Every approve or reject writes a history event before any current-step projection is reset. Existing history rows remain valid because the new fields are nullable.

`ApprovalStep` remains the current runtime projection. This avoids a second task table while ensuring that prior approvals and rejections are not lost when a step is reopened or a new round begins.

## Template Validation

Policy and flow type must form one of these combinations:

| Flow type | Allowed policies |
|---|---|
| `SEQUENTIAL` | `TERMINATE`, `RETURN_TO_PREVIOUS_STEP`, `RETURN_TO_INITIATOR` |
| `PARALLEL_ALL` | `TERMINATE`, `RETURN_TO_INITIATOR`, `MAJORITY` |

`MAJORITY` requires a nonempty odd number of configured assignments. Backend validation is authoritative; frontend validation provides immediate feedback.

Changing `flowType` in the frontend resets `rejectionPolicy` to `TERMINATE` so an invalid combination cannot remain selected.

## Runtime Transitions

### TERMINATE

The current behavior is preserved exactly.

- Sequential rejection marks the current step rejected, marks the request `REJECTED`, and runs the existing rejection finalizer.
- Parallel rejection marks the actor's task rejected, cancels remaining pending tasks, marks the request `REJECTED`, and runs the existing rejection finalizer.
- A nonblank rejection comment remains mandatory.

### RETURN_TO_PREVIOUS_STEP

This applies only to sequential requests.

When step `N > 1` rejects:

1. Validate the current actor and mandatory comment using the existing decision path.
2. Append the rejected decision for step `N` to history.
3. Reopen step `N - 1` by clearing only its current projection fields and setting it to `PENDING`.
4. Reset step `N` to a pending current projection so it can be reached again after step `N - 1` is approved.
5. Leave steps before `N - 1` approved and later steps unchanged.
6. Set `currentStep` to `N - 1`, keep request status `PENDING`, and do not call a terminal rejection handler.
7. Notify the reopened assignee through the existing notification service.

The previous approval at step `N - 1` remains in append-only history even though its current projection is reopened.

When step 1 rejects, the transition automatically follows `RETURN_TO_INITIATOR` because no previous step exists.

### RETURN_TO_INITIATOR

This applies to both flow types.

On rejection:

1. Append the rejecting decision to history.
2. Set request status to `REWORK` and clear `completedAt`.
3. Expose no approver decision actions.
4. Expose `RESUBMIT` only to the authenticated requester.
5. Notify the requester using the existing approval notification infrastructure.
6. Do not call a terminal rejection handler.

`POST /api/v1/approvals/{id}/resubmit` is requester-only. It locks the request, requires `REWORK`, increments `approvalRound`, resets all current step projections to `PENDING`, stamps the new round on the steps, restores the flow-specific current position (`1` for sequential, `0` for parallel), and changes the request to `PENDING`.

Resubmission uses the request's persisted route and policy snapshot. It does not reread the current template or mutate the target document.

### MAJORITY

This applies only to parallel requests.

- Approve and reject both complete only the authenticated user's current-round task.
- Rejection requires a nonblank comment but does not cancel other tasks.
- The request remains `PENDING` until every current-round task is decided.
- After the last vote, the request is `APPROVED` when approved votes are strictly greater than half of all configured tasks; otherwise it is `REJECTED`.
- The existing terminal handler runs exactly once with the aggregate outcome.
- Every vote is appended to history, including rejected votes when the aggregate result is approved.

## API Contract

Extend template DTOs and payloads with:

- `rejectionPolicy`, defaulting to `TERMINATE` when absent.

Extend approval request DTOs with:

- `rejectionPolicy`;
- existing `approvalRound` remains authoritative;
- `allowedActions` may now include `RESUBMIT`.

Extend history DTOs with the nullable policy-event fields listed in the persistence section.

Add:

- `POST /api/v1/approvals/{id}/resubmit`.

Existing approve, reject, return, cancel, list, history, and template endpoints keep their paths and legacy fields.

The existing manual `/return` endpoint remains unchanged. It is not repurposed as rejection-policy behavior.

## Authorization

- Existing step eligibility and linked-document scope checks remain authoritative for approve/reject.
- `RESUBMIT` requires the current authenticated principal to match the persisted requester ID; scope-admin status alone does not impersonate the requester.
- No new permission constant is introduced.
- Domain edit authorization is unchanged and outside this feature.

## Notifications

Reuse `NotificationService` and the existing approval notification channel.

- `RETURN_TO_PREVIOUS_STEP` notifies the reopened step assignee.
- `RETURN_TO_INITIATOR` notifies the requester.
- Resubmission notifies the first sequential assignee or all parallel assignees through the existing `notifyCurrentStep` path.

No new delivery channel, SLA event, or escalation workflow is added.

## Frontend

### Rule form

Add policy radio cards to the existing approval-rule dialog.

- Sequential cards: Terminate, Return to previous step, Return to initiator.
- Parallel cards: Terminate, Return to initiator, Majority.
- Each card uses the supplied RU/EN/UZ policy copy in an accessible tooltip.
- `MAJORITY` shows an inline odd-participant validation error.
- Policy values are included in template create/update payloads.

No document-field configuration or progressive disclosure for correction fields is rendered.

### Runtime controls

- Existing reject dialog and mandatory-comment behavior are retained.
- `REWORK` uses a warning/amber status treatment rather than rejection red.
- Approve/reject controls are hidden while `REWORK`.
- A visible resubmit button is rendered only when backend `allowedActions` contains `RESUBMIT`.
- The status transition result is announced through an `aria-live` region.

Existing step cards, approval progress, cancel behavior, manual return UI, and unrelated document screens are otherwise unchanged.

## Error Handling

Backend returns stable conflicts for:

- policy incompatible with flow type;
- even or empty `MAJORITY` assignment count;
- resubmission by a non-requester;
- resubmission outside `REWORK`;
- repeated or stale task decisions.

Frontend maps these errors through the existing approval action error/toast path and never locally overrides backend action flags.

## Transaction and Concurrency

All decision and resubmission paths preserve the existing request `FOR UPDATE` locking and lifecycle-domain lock order.

History append, step projection changes, request transition, terminal handler execution, audit logging, and notification persistence occur in the same transaction. Existing `executed`/`executeOnce` behavior remains the exactly-once backstop for aggregate terminal actions.

## Testing

Backend TDD coverage includes:

- null/legacy policy defaults to `TERMINATE`;
- template policy/flow validation;
- policy snapshot immutability;
- unchanged sequential and parallel termination behavior;
- previous-step rejection, first-step fallback, and repeat approval;
- rework transition, authorization, round increment, and resubmission;
- majority partial voting, approved majority, rejected majority, and exactly-once finalization;
- mandatory rejection comments for every policy;
- request-lock concurrency around the final majority vote;
- migration constraints and backfill contract.

Frontend TDD coverage includes:

- rule-form defaults, payload mapping, flow reset, and invalid combinations;
- odd-participant validation for majority;
- policy card accessibility and translated copy;
- `REWORK` runtime integrity and action visibility;
- requester-only resubmit rendering based on backend actions;
- preservation of existing terminate, return, cancel, progress, and history behavior.

## Rollout

The migration is additive and backfills both templates and requests to `TERMINATE`. Backend deploys before frontend so old clients continue sending payloads without `rejectionPolicy` safely.

Rollback is application-level: stop selecting non-default policies and deploy the previous application while retaining additive policy/history columns. Policy-generated historical events are never deleted or rewritten.
