# Repair Campaign Approval Policy Versioning Design

## Goal

Make the Repair Campaign approval contract historically explainable and safe to configure while preserving the mandatory seven-discipline route. Approval templates remain the configured route source, but a versioned canonical policy defines which Repair Campaign templates are valid.

The design also introduces an explicit, auditable separation-of-duty override: a `SYSTEM_ADMIN` may approve every step in a seven-step Repair Campaign approval, including all seven steps as the same actor. Ordinary actors remain limited to one discipline step.

## Decisions

1. A Repair Campaign lifecycle approval always contains exactly seven active role-based steps in the canonical order.
2. Repair Campaign templates are validated when created, updated, or activated. An invalid active template is rejected before it can affect approval creation.
3. Approval requests snapshot the selected template identity, template revision, canonical route hash, and policy version.
4. An ordinary actor may approve the current step when they hold either the exact configured discipline role or the Repair Campaign approval permission.
5. An ordinary actor may approve at most one discipline step and may not be the requester.
6. A `SYSTEM_ADMIN` may approve any or all seven steps, but may not approve their own request.
7. Every repeated-actor admin approval is persisted as an explicit separation-of-duty override with authority, actor, timestamp, and reason evidence.
8. Pending approvals governed by an obsolete or unprovable policy are cancelled and re-requested. Completed approvals and their historical evidence are never rewritten.

## Canonical Policy

The policy identifier is `REPAIR_CAMPAIGN_SEVEN_DISCIPLINE_V1`. It owns the following route:

1. `REPAIR_CAMPAIGN_CHIEF_MECHANIC_APPROVER`
2. `REPAIR_CAMPAIGN_PRODUCTION_APPROVER`
3. `REPAIR_CAMPAIGN_WAREHOUSE_APPROVER`
4. `REPAIR_CAMPAIGN_PROCUREMENT_APPROVER`
5. `REPAIR_CAMPAIGN_FINANCE_APPROVER`
6. `REPAIR_CAMPAIGN_HSE_APPROVER`
7. `REPAIR_CAMPAIGN_CHIEF_ENGINEER_APPROVER`

The existing `RepairCampaignApprovalRouteValidator` remains the canonical route authority. It validates configured template inputs, persisted approval steps, and terminal completion against the same policy definition.

Changing the role set, role order, ordinary separation-of-duty rule, requester exclusion, or admin override semantics requires a new policy identifier. A code deployment must not silently change the meaning of an existing policy version.

## Template Lifecycle

`ApprovalRuleService` applies Repair Campaign-specific validation whenever `targetType=REPAIR_CAMPAIGN` and `actionType=APPROVE`:

- all seven roles must be present exactly once;
- step orders must be exactly 1 through 7;
- approver IDs are not allowed for canonical discipline steps;
- missing, duplicate, unexpected, reordered, five-step, eight-step, and `SYSTEM_ADMIN`-only routes are rejected;
- an invalid template cannot be created as active, updated while active, or activated;
- inactive invalid legacy templates may remain visible for audit, but cannot become active without correction.

Template activation continues to deactivate competing active templates in the service transaction. A database constraint or equivalent serialized activation mechanism guarantees that at most one active, non-deleted `REPAIR_CAMPAIGN/APPROVE` template exists.

The runtime resolver uses only that exact active template. It does not use caller-supplied steps, a target-only template, a parent role, a generic permission fallback, or `SYSTEM_ADMIN` fallback for Repair Campaign lifecycle approval.

## Approval Provenance Snapshot

Every newly created Repair Campaign approval persists:

- `approvalTemplateId`: selected template ID;
- `approvalTemplateVersion`: optimistic template revision at selection time;
- `approvalRouteHash`: SHA-256 of the normalized target, action, policy version, ordered step numbers, ordered role codes, and approver IDs;
- `approvalPolicyVersion`: `REPAIR_CAMPAIGN_SEVEN_DISCIPLINE_V1`;
- the existing copied `approval_steps`, which remain the executable route snapshot.

`ApprovalTemplate` gains an optimistic version so concurrent updates and activation cannot produce ambiguous snapshots. The route hash is computed from normalized values and does not include display names, timestamps, SLA values, or unrelated template metadata.

Approval DTOs expose provenance fields for support and audit users without using them to grant actionability.

## Actor Eligibility

For an ordinary actor, the current step is actionable when all of the following are true:

- the approval route and provenance match the canonical policy;
- the actor is not the requester;
- the actor has the exact configured discipline role or `REPAIR_CAMPAIGN_APPROVE` permission;
- the actor has not approved another discipline step in the same approval;
- existing linked-document scope rules pass.

Possession of `REPAIR_CAMPAIGN_APPROVE` makes an actor eligible for the current discipline step; it does not remove the one-step-per-actor rule.

For `SYSTEM_ADMIN`:

- the actor may approve any current discipline step without holding that discipline role or `REPAIR_CAMPAIGN_APPROVE` explicitly;
- the actor may approve multiple steps, including all seven steps;
- the actor may not approve an approval they requested;
- normal ordering is preserved, so only the current pending step can be approved;
- every step after the actor's first approved discipline step is recorded as an explicit separation-of-duty override.

## Override Evidence

Approval step evidence records whether separation of duty was overridden and why. The persisted evidence includes:

- `sodOverride` boolean;
- `sodOverrideAuthority`, fixed to `SYSTEM_ADMIN` for this policy;
- `sodOverrideReason`;
- the existing `decidedById` and `decidedAt` fields.

The service derives override authority from authenticated server-side authorities. Clients cannot submit `sodOverride=true` or choose the override authority.

The existing decision comment supplies the human-readable override reason. A repeated-actor `SYSTEM_ADMIN` decision requires a nonblank comment. The first step approved by that admin is not marked as an override unless the actor has already approved another discipline step.

Governance history and the audit log include the policy version, step, actor, override authority, and reason. Removal of `SYSTEM_ADMIN` from the user later does not invalidate completed evidence because authority was snapshotted at decision time.

## Completion Validation

Terminal validation continues to require:

- approval status `APPROVED` and action `APPROVE`;
- exactly seven canonical active steps in order;
- every step approved with actor and timestamp evidence;
- requester exclusion for every actor;
- matching campaign status, scope version, scope hash, campaign version, and payload;
- matching approval policy version and route hash.

Actor separation is valid when either:

1. all seven actors are distinct; or
2. every repeated occurrence after an actor's first step contains valid persisted `SYSTEM_ADMIN` override evidence.

A repeated ordinary actor, missing override evidence, client-authored override evidence, an unknown override authority, or a blank override reason fails with `REPAIR_CAMPAIGN_APPROVAL_SEPARATION_OF_DUTY_FAILURE`.

This preserves strict validation for normal users while making the approved admin exception explicit and reviewable.

## Request and Re-request Flow

The request flow remains transactional:

1. Lock and validate the Repair Campaign and its scope snapshot.
2. Lock the approval target/action key.
3. Load the single active canonical template and snapshot its provenance.
4. Reconcile pending approvals for the target/action.
5. Reuse a pending approval only when scope payload, route, policy version, route hash, and template provenance are current.
6. Cancel obsolete, noncanonical, duplicate, or unprovable pending approvals with history.
7. Create exactly one current approval from the selected template.

If no active canonical template exists, the transaction fails with `REPAIR_CAMPAIGN_APPROVAL_ROUTE_NOT_CONFIGURED`. It must not persist a new approval or a campaign transition that claims a valid approval exists.

A re-request caused only by template or policy provenance change may replace the approval while preserving the current campaign scope snapshot. A stale campaign scope continues to use the existing scope-stale errors and must be rebuilt through the normal campaign lifecycle.

## Legacy Data

A forward-only migration adds nullable provenance and override-evidence columns so completed historical approvals remain readable.

For pending `REPAIR_CAMPAIGN/APPROVE` requests:

- noncanonical routes are cancelled with `NONCANONICAL_REPAIR_CAMPAIGN_ROUTE`;
- canonical routes without trustworthy template/policy provenance are cancelled with `REPAIR_CAMPAIGN_APPROVAL_POLICY_UPGRADED`;
- approval steps and existing history are preserved;
- exactly one cancellation history event is added per transitioned request;
- rerunning the data-repair logic produces no additional changes.

Affected campaigns are left in a state from which the existing request-approval command can create a replacement without fabricating historical provenance. The replacement must use the current scope snapshot only when that snapshot still passes scope validation.

Completed, rejected, cancelled, failed, and expired approvals are not rewritten or retroactively assigned template provenance.

## Errors and Actionability

- `REPAIR_CAMPAIGN_APPROVAL_ROUTE_NOT_CONFIGURED`: no active canonical template or activation attempted with an invalid route.
- `REPAIR_CAMPAIGN_APPROVAL_ROUTE_STALE`: persisted route, policy version, route hash, or provenance is incompatible with current action handling.
- `REPAIR_CAMPAIGN_APPROVAL_SCOPE_STALE` and existing detailed scope errors: campaign snapshot mismatch.
- `REPAIR_CAMPAIGN_APPROVAL_SEPARATION_OF_DUTY_FAILURE`: ordinary repeated actor, requester self-approval, or invalid admin override evidence.
- `REPAIR_CAMPAIGN_APPROVAL_POLICY_UPGRADED`: cancellation reason for a pending approval that cannot prove the current policy/template provenance.

Noncanonical or provenance-stale approvals remain visible in history and expose `actionable=false`, `stale=true`, and a stable stale reason. Direct approve, reject, return, and cancel actions remain blocked for stale records; controlled re-request performs reconciliation.

## Concurrency

- Template updates use optimistic locking.
- Template activation serializes competing activation attempts and has a database-backed one-active-template invariant.
- Approval request/re-request retains the target/action advisory transaction lock and unique pending-approval protection.
- A decision locks or version-checks the approval request so two decisions cannot approve the same current step.
- Override evidence and the step decision are persisted atomically.

## Security

- Only authenticated server authorities determine role, permission, and `SYSTEM_ADMIN` eligibility.
- Request payloads cannot assert admin status or override evidence.
- `SYSTEM_ADMIN` override does not bypass requester exclusion, campaign scope validation, route shape, step order, or current-step checks.
- `REPAIR_CAMPAIGN_APPROVE` permits action on the current step but does not grant admin multi-step override.
- Template administration and approval decisions retain their existing endpoint authorization in addition to these service-level checks.

## Testing Strategy

Focused tests cover:

- canonical template create, update, and activation;
- rejection of missing, duplicate, reordered, five-step, eight-step, approver-ID, and `SYSTEM_ADMIN`-only templates;
- one-active-template concurrency and optimistic template versioning;
- approval provenance snapshot and deterministic route hash;
- ordinary exact-role approval;
- ordinary `REPAIR_CAMPAIGN_APPROVE` permission approval;
- ordinary requester rejection and repeated-actor rejection;
- one `SYSTEM_ADMIN` approving all seven ordered steps;
- admin requester rejection;
- required override reason and server-derived override evidence;
- terminal rejection of forged, missing, or malformed override evidence;
- policy/template change cancellation and replacement;
- migration preservation and idempotency;
- concurrent request and concurrent decision behavior;
- existing scope/hash/version and noncanonical actionability regressions.

Verification uses Java 21 and includes focused Repair Campaign approval tests, `ApprovalServiceTest`, approval security tests, repository tests, migration contract/PostgreSQL tests, compile, and package.

## Out of Scope

- Making Repair Campaign lifecycle routes freely configurable to arbitrary role counts.
- Allowing ordinary actors to approve multiple discipline steps.
- Allowing any requester, including `SYSTEM_ADMIN`, to approve their own request.
- Rewriting completed historical approvals with inferred provenance.
- Frontend workflow redesign beyond exposing backend provenance or stale fields already present in DTO contracts.
