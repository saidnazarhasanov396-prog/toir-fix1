# Template-Driven Lifecycle Approval Design

## Purpose

Revise Repair Campaign and Planned Shutdown approval behavior so the active approval template is the source of truth when a new runtime approval is created, while persisted `approval_steps` are the immutable execution route for an existing `ApprovalRequest`.

This design applies only to:

- `REPAIR_CAMPAIGN / APPROVE`
- `PLANNED_SHUTDOWN / APPROVE`

It removes fixed Repair Campaign seven-role and Planned Shutdown Production/HSE route assumptions without changing unrelated approval target types.

## Business Contract

The creation flow is:

1. Resolve the exact active `approval_template` for target type and action.
2. Validate and order its active `approval_template_steps`.
3. Freeze those steps for the request operation.
4. Capture the domain approval scope and transition the domain to `PENDING_APPROVAL`.
5. Snapshot the frozen route into `approval_request` and `approval_steps`.
6. Execute step decisions strictly against persisted runtime steps.
7. When every runtime step is approved, mark the request approved and finalize the domain lifecycle in the same transaction.

Template changes after runtime creation do not rewrite, cancel, or invalidate an existing pending approval. New templates apply only to newly created runtime approvals.

An approval-relevant domain mutation is a separate event. It cancels the pending approval, preserves its request, steps, and history, clears the domain approval snapshot, resets the domain to its correct readiness state, and requires an explicit re-request. The next request uses the active template at that time.

## Scope and Non-Goals

The implementation introduces one focused shared `LifecycleApprovalRoutePolicy` used only by the two lifecycle approval pairs above.

This task does not introduce:

- a universal approval policy registry;
- ApprovalRequest template ID or version provenance columns;
- a broad approval-engine migration;
- historical runtime-step rewrites;
- fixed step counts or fixed business role lists;
- an explicit-user template editor in the frontend.

Persisted `approval_steps` are the accepted immutable route snapshot for this task. They preserve the executable assignment and order but do not preserve template ID/version provenance.

## Component Boundaries

### LifecycleApprovalRoutePolicy

The shared policy owns neutral, typed validation for template routes and runtime routes. It is enabled only when target/action is `REPAIR_CAMPAIGN / APPROVE` or `PLANNED_SHUTDOWN / APPROVE`.

It owns:

- active-template step structure validation;
- ordered template-step validation;
- persisted runtime-step structure validation;
- current-step actionability checks;
- requester approve/reject exclusion;
- one-approved-step-per-actor enforcement;
- runtime completion validation;
- stale/non-actionable runtime classification.

It does not own domain status, scope, payload, or lifecycle transitions. It returns neutral reasons which domain services map to stable domain errors.

### DefaultApprovalRouteResolver

For the two lifecycle pairs, the resolver queries exact `targetType + actionType` active, non-deleted templates. It never uses the target-only lookup, generic permission fallback, or automatic `SYSTEM_ADMIN` fallback.

Resolution cardinality is:

- zero exact active templates: `NO_ACTIVE_TEMPLATE`;
- one exact active template: validate and freeze its ordered steps;
- multiple exact active templates: `MULTIPLE_ACTIVE_TEMPLATES`.

The resolver does not silently choose the newest template.

### ApprovalService

`ApprovalService` owns:

- approval target/action locking after the domain row lock;
- pending request discovery and compatible reuse;
- runtime request and step creation from a frozen route;
- scope-stale pending cancellation;
- step decisions;
- actionability flags;
- requester and distinct-actor enforcement through the shared policy;
- terminal request status changes;
- invocation of domain finalization.

It does not compare an existing pending runtime route with the current active template.

### Domain Services and Policies

Repair Campaign and Planned Shutdown retain separate domain policies and services. They own:

- domain row locking;
- allowed status validation;
- scope version and scope hash validation;
- payload/snapshot validation;
- readiness transitions;
- scope mutation behavior;
- final lifecycle transitions;
- domain-specific error mapping.

Active templates are never consulted during preparation or finalization of an existing runtime approval.

## Template Validation

A valid active lifecycle template has at least one active step and satisfies all of the following:

- step order is contiguous and exactly `1..N`;
- step order is deterministic;
- no duplicate or nonpositive order exists;
- each step has exactly one assignment:
  - nonblank `approverRole`, or
  - non-null explicit `approverId`;
- both-null and both-present assignments are invalid;
- duplicate explicit approver IDs are invalid because the route cannot satisfy distinct-actor approval;
- repeated roles are valid because different eligible actors may satisfy them at runtime;
- the template target/action is the exact lifecycle pair;
- the template is active and not deleted.

Template validity does not depend on current counts of users assigned to a role. It does not require a particular route length or role list. `SYSTEM_ADMIN` is a valid configured role.

Template create, update, and activation run the same shared structural validation. Service logic prevents a second active exact template. A database backstop enforces the same invariant.

## Runtime Snapshot Validation

Runtime validation uses only the `ApprovalRequest`, persisted `approval_steps`, and the current domain scope snapshot.

A structurally valid runtime route requires:

- at least one non-deleted persisted step;
- contiguous step numbers exactly `1..N`;
- no duplicate, gap, or nonpositive step number;
- exactly one role or explicit approver assignment per step;
- target/action matching the lifecycle pair;
- request status, `currentStep`, and step decisions forming a consistent state;
- every approved step having a decision actor;
- no approved step decided by the requester;
- no actor appearing as the approved actor on more than one step.

Runtime route stale means malformed or internally inconsistent persisted runtime data. It does not mean disagreement with the current active template, a fixed role count, or a fixed role order.

## Separation of Duty and Eligibility

For the two lifecycle pairs only:

- the requester cannot approve or reject any runtime step;
- one actor may approve at most one step in the same ApprovalRequest;
- only the current ordered pending step is actionable;
- an actor must match the persisted explicit approver ID or configured role;
- an actor who approved an earlier step cannot approve another step;
- reject requires current-step eligibility and requester exclusion but does not inherit the repeated-approved-actor restriction;
- cancellation is a separate domain permission and lifecycle decision.

`SYSTEM_ADMIN` may act when the persisted step explicitly configures `SYSTEM_ADMIN`. A wildcard permission does not replace an unrelated configured role for either lifecycle target. Requester and distinct-actor restrictions still apply to `SYSTEM_ADMIN`.

The policy does not apply these separation-of-duty rules to unrelated target types.

## Transaction and Locking Contract

Domain request, mutation, cancellation, re-request, and finalization flows use a consistent lock order:

1. lock the domain row;
2. acquire the approval target/action lock.

Domain services and `ApprovalService` participate in one Spring `REQUIRED` transaction. Runtime creation and finalization do not use `REQUIRES_NEW`.

### New Request Order

Within one transaction:

1. Lock the domain row.
2. Validate the domain version and readiness state.
3. Calculate the prospective approval scope and payload.
4. Acquire the approval target/action lock.
5. Check for a compatible pending runtime approval.
6. If no reusable pending approval exists, resolve the exact active template.
7. Validate and freeze its ordered steps.
8. Write the domain approval snapshot and `PENDING_APPROVAL` status.
9. `saveAndFlush` the domain.
10. Create the `ApprovalRequest` and `approval_steps` from the frozen route without querying the template again.

Any failure rolls back domain and approval changes.

### Pending Reuse

Reuse requires:

- the domain is consistently `PENDING_APPROVAL`;
- target/action and target ID match;
- the persisted route is structurally valid;
- the persisted payload matches the current domain approval scope;
- the pending request is otherwise actionable.

A compatible pending request is reused even when no active template exists, multiple active templates exist, or the template was edited, deactivated, or replaced. Template resolution occurs only when a new runtime approval is required.

A pending approval found while the domain is in a readiness state is a data inconsistency and is not silently reused.

### Scope Mutation

An approval-relevant mutation runs under the same lock order and transaction. It:

- cancels the current pending approval;
- records cancellation history;
- preserves the ApprovalRequest and steps;
- clears the domain approval scope snapshot;
- increments or otherwise updates the domain scope version;
- returns the domain to its appropriate scope-formation or readiness state;
- does not create a replacement approval.

## Decision and Terminal Ordering

Approve processing is ordered as follows:

1. Validate request status and runtime route integrity.
2. Resolve the persisted current step.
3. Validate actor eligibility, requester exclusion, and distinct-actor approval.
4. Verify every prior step is approved and has valid decision evidence.
5. Mark the current step approved with actor, time, and comment.
6. Verify every persisted runtime step is now approved.
7. Mark the ApprovalRequest `APPROVED`.
8. Invoke Repair Campaign or Planned Shutdown domain finalization.
9. Commit the step, request, history, and domain lifecycle change together.

If domain finalization fails, the final step decision, request terminal status, history, and domain status all roll back.

Reject and cancel flows remain domain-specific and do not invoke successful runtime completion finalization.

## Repair Campaign Finalization

Repair Campaign finalization retains:

- campaign status is `PENDING_APPROVAL`;
- target/action is `REPAIR_CAMPAIGN / APPROVE`;
- `scopeVersion` equals `approvalScopeVersion`;
- the current computed scope hash equals `approvalScopeHash`;
- the approval payload belongs to the current campaign snapshot;
- the runtime request is `APPROVED`;
- every persisted runtime step is approved;
- requester exclusion and distinct approved actors are satisfied.

It removes:

- the fixed seven-step count;
- the fixed seven-role list and order;
- `completedSevenDisciplineRoute` and equivalent checks;
- fixed role-hash validity requirements;
- rejection of a one-step `SYSTEM_ADMIN` route solely because of its shape.

A valid one-step runtime approval can finalize the campaign.

## Planned Shutdown Finalization and Preparation

Planned Shutdown request approval continues to capture its scope snapshot, transition to `PENDING_APPROVAL`, flush, and create or reuse the runtime ApprovalRequest atomically.

Preparation and finalization select the newest deterministic approved runtime request matching:

- `PLANNED_SHUTDOWN / APPROVE`;
- the current shutdown ID;
- the current scope payload and snapshot;
- a structurally valid persisted runtime route;
- all persisted steps approved.

Historical approvals for another scope are ignored and cannot satisfy preparation. Production/HSE-specific evidence extraction, role checks, blockers, and errors are removed. Preparation and finalization do not consult the active template.

## Error Mapping

Shared neutral validation reasons are mapped at domain boundaries:

- `NO_ACTIVE_TEMPLATE`:
  - `REPAIR_CAMPAIGN_APPROVAL_TEMPLATE_NOT_CONFIGURED`, or
  - `PLANNED_SHUTDOWN_APPROVAL_TEMPLATE_NOT_CONFIGURED`;
- `MULTIPLE_ACTIVE_TEMPLATES`: `MULTIPLE_ACTIVE_TEMPLATES`;
- malformed or empty template: `APPROVAL_TEMPLATE_STEPS_INVALID`;
- malformed or inconsistent persisted runtime route: domain route-stale `409`;
- incomplete historical/runtime completion: domain incomplete-approval `409`;
- scope or payload mismatch: existing domain scope-stale/snapshot mismatch errors;
- decision-time wrong actor, requester, or wrong current step: access-denied/current-step errors.

Route-stale guidance describes malformed or inconsistent persisted runtime data, never disagreement with a fixed route count or the current template.

## Backend Actionability

Backend flags remain authoritative:

- `canApprove` requires a pending, structurally valid runtime route, valid current scope, current-step role or explicit-ID eligibility, requester exclusion, and no prior approved step by the actor;
- `canReject` requires a pending, structurally valid runtime route, valid current scope, current-step role or explicit-ID eligibility, and requester exclusion;
- `canCancel` is computed separately from domain permission and lifecycle rules.

Local admin or wildcard permissions never replace an unrelated configured lifecycle role.

## Frontend Runtime Rendering

Repair Campaign and Planned Shutdown detail pages render only the persisted ApprovalRequest DTO and its runtime steps. They do not read the current template to display an existing request. `allowCreate=false` remains set for domain-owned lifecycle creation.

The UI sorts a copied steps array by `stepNumber` and never mutates query-cache arrays.

Valid progress requires:

- nonempty steps;
- contiguous step numbers `1..N`;
- `totalSteps` absent or equal to `steps.length`;
- a `currentStep` consistent with request status.

Missing or inconsistent runtime data renders route unavailable and disables actions. The UI does not invent a total of one or synthesize missing steps.

Status behavior is:

- `PENDING`: render current progress and only backend-authorized actions;
- `APPROVED`: render completed history without an actionable current step;
- `REJECTED` and `CANCELLED`: render non-actionable history.

Approve, reject, and cancel controls each require both local permission and the corresponding backend flag. Local `SYSTEM_ADMIN` or wildcard permission cannot override backend false.

## Frontend Template Administration

The template UI continues supporting ordered variable `1..N` role steps. Frontend validation is UX assistance only; backend validation and cardinality are authoritative.

This task does not add an explicit-user selector. If an existing template contains an explicit approver step, the role-only editor must not erase or convert it. Unsupported steps or the complete template are preserved and made read-only until an explicit-user editor exists.

Multiple active lifecycle templates are displayed as an administrator configuration error.

## Localization

EN, RU, and UZ guidance is added or updated for:

- `REPAIR_CAMPAIGN_APPROVAL_TEMPLATE_NOT_CONFIGURED`;
- `PLANNED_SHUTDOWN_APPROVAL_TEMPLATE_NOT_CONFIGURED`;
- `APPROVAL_TEMPLATE_STEPS_INVALID`;
- `MULTIPLE_ACTIVE_TEMPLATES`;
- existing Repair Campaign and Planned Shutdown scope-stale codes;
- redefined route-stale codes.

Production/HSE-specific Planned Shutdown approval guidance is replaced with generic runtime approval language.

## Database Backstop

A focused forward-only migration enforces at most one active, non-deleted exact template for:

- `REPAIR_CAMPAIGN / APPROVE`;
- `PLANNED_SHUTDOWN / APPROVE`.

The duplicate preflight and partial unique index use identical `COALESCE(action_type, 'APPROVE')` normalization. The preflight does not modify duplicates. It raises an explicit SQL exception containing target type, normalized action, and duplicate count.

The migration does not rewrite template data, runtime requests, or runtime steps. Inactive, deleted, and unrelated templates remain unaffected.

## Test Strategy

Every behavior change follows red-green-refactor TDD.

### Shared Policy Tests

Parameterized tests cover 1, 2, 7, and arbitrary N-step routes; empty routes; duplicate, gap, and nonpositive order; assignment XOR; repeated roles; duplicate explicit IDs; invalid current step; request/status inconsistency; missing decision actor; requester participation; repeated approved actor; current-step eligibility; and terminal completion.

### Resolver and Template Administration Tests

Tests cover zero, one, and multiple exact active templates; exact action matching; inactive/deleted exclusion; no fallback; ordered freezing; variable route counts; controlled unique-violation conflicts; and multiple-active prevention.

### Pending and Concurrency Tests

Tests prove:

- compatible reuse with no active template;
- compatible reuse with multiple active templates;
- edit/deactivation does not alter pending steps;
- template resolution occurs only for new runtime creation;
- scope change cancels and preserves history;
- concurrent request approval creates at most one pending request;
- concurrent template activation leaves at most one active exact template.

### Decision and Domain Tests

Tests separately prove requester approve/reject denial, repeated-actor approve denial, reject eligibility without the repeated-approved-actor rule, separate cancellation permission, one-step Repair Campaign completion, seven-step Repair Campaign completion, two-step Planned Shutdown completion, arbitrary N-step Planned Shutdown completion, and deterministic current-scope Planned Shutdown evidence selection.

A real transactional Spring integration test forces domain finalization failure and verifies rollback of the final step decision, ApprovalRequest terminal status, history, and domain lifecycle status.

### PostgreSQL Migration Tests

Real PostgreSQL tests cover:

- clean migration success;
- `NULL` and `APPROVE` normalization;
- duplicate active migration failure with the explicit exception;
- inactive and deleted duplicates allowed;
- unrelated targets unaffected;
- post-migration duplicate or concurrent activation rejected;
- no existing template data rewritten.

### Frontend Tests

Parameterized fixtures cover 1, 2, 7, and arbitrary N-step rendering; missing steps; inconsistent totals; invalid current step; backend false actionability; pending route preservation after template change; variable template payloads; and explicit-approver data preservation/read-only behavior.

## Verification and Release Acceptance

Backend verification uses Java 21 and includes focused shared-policy, resolver, ApprovalService, Repair Campaign, Planned Shutdown, template administration, transaction integration, migration contract, and real PostgreSQL tests, followed by compile and package.

Frontend verification includes focused Vitest tests, TypeScript compilation, changed-file ESLint, production build from a clean worktree containing only intended changes, and EN/RU/UZ validation.

No unexecuted command is reported as passed. Automated verification is followed by manual UI business-flow testing before release.

## Data Impact

The only database change is the focused active-template uniqueness backstop. No ApprovalRequest provenance columns are added. No active duplicate is silently changed. No historical ApprovalRequest, approval step, or history row is rewritten or deleted.

## Remaining Release Decision

Manual UI business-flow verification is required before release and must be recorded separately from automated test results.
