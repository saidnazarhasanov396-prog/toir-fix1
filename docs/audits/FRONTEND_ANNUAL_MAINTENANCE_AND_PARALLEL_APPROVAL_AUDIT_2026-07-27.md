# Frontend Audit: Annual Maintenance Schedule and Parallel Approval

**Audit date:** 2026-07-27  
**Frontend repository:** `/home/tenzorsoft/Desktop/TOIR/toir-frontend`  
**Frontend branch:** `Codex_org` at `68afdf68`  
**Backend repository:** `/home/tenzorsoft/Desktop/TOIR/toir-backend`  
**Backend current branch:** `Codex_org` at `8cc1f17b` (local commit ahead of `origin/Codex_org`)  
**Specified parallel approval reference:** `bada6b1f`  
**Annual maintenance backend reference:** `01e48355`  
**Parallel approval backend reference:** `bada6b1f`

> Statuses in this document describe source-code and contract readiness only. `DONE` does not mean runtime behavior was verified. Application execution, automated tests, builds, lint, typecheck, real API calls, database checks, and manual end-to-end verification were not performed.

## 1. Audit Scope

This is a deep, read-only comparison of the current frontend against two backend feature sets:

1. Annual Maintenance Schedule / Yearly Maintenance Calendar.
2. Parallel Approval Flow (`PARALLEL_ALL` alongside legacy `SEQUENTIAL`).

The audit inspected frontend API contracts, route/menu wiring, forms, UI state, permission policies, error handling, localization, approval and PPR lifecycle logic, notification routing, current git state, relevant history, and the named backend commits/source. No production code, test, migration, or application data was changed.

Status meanings follow the requested vocabulary:

- `DONE`: the source-level frontend requirement is connected through a usable UI flow.
- `PARTIAL`: meaningful support exists, but at least one required behavior or surface is incomplete.
- `MISSING`: no usable frontend implementation was found.
- `IN_PROGRESS`: concrete unmerged/local design or implementation evidence exists.
- `BLOCKED`: the frontend cannot complete the requirement because the current contract/lifecycle prevents it.
- `BACKEND_ONLY`: the capability exists in backend behavior but has no corresponding frontend integration or presentation.
- `NOT_APPLICABLE`: excluded from scoring.
- `UNKNOWN`: evidence was insufficient.

## 2. Repository and Branch State

### Frontend

- Branch: `Codex_org`.
- HEAD: `68afdf68 docs: design maintenance schedule modal flow`.
- Tracking: `origin/Codex_org` at `f470fcb5`; local branch is ahead by one documentation-only commit.
- Working tree: clean before this audit document was created. During final verification, concurrent uncommitted test-first changes appeared in six tracked maintenance-schedule test files plus one new dialog test; they were not made or modified by this audit.
- Relevant merged commits:
  - `553526e4 merge: add annual maintenance schedule builder`;
  - `0decb912 fix: harden maintenance schedule preview state`;
  - `38120f1c feat: add parallel approval experience`;
  - `f470fcb5 merge: show approval flow types in tables`.
- Linked feature worktrees remain registered for `agent/maintenance-schedule-builder` and `agent/approval-flow-type-table-badges`, but both tip commits are ancestors of current `HEAD`; they are not unmerged production work.
- Concrete current work consists of the local approved modal-flow design in `docs/superpowers/specs/2026-07-27-maintenance-schedule-builder-modal-design.md` and concurrent uncommitted tests for the options client, modal CTA/routing, automatic preview-before-create workflow, eligibility guidance, and dialog contract. No corresponding production modal/options-client changes were present at final verification, so this remains `IN_PROGRESS`, not `DONE`.

### Backend

- Branch: `Codex_org`.
- Current HEAD: `8cc1f17b feat: filter maintenance schedule options`; the working tree is clean and the local branch is one commit ahead of `origin/Codex_org`.
- The specified parallel approval reference remains `bada6b1f`; annual maintenance hardening commit `01e48355` is immediately behind it.
- The current local backend commit was created concurrently during this read-only audit and was not made by the audit. It adds the options endpoint, eligibility selector, service wiring, and related tests. Because the frontend has no consumer for the new endpoint, it is classified as `BACKEND_ONLY` and does not increase frontend readiness.

### Repository convention

`docs/audits` already exists in the frontend repository. This report is stored there; no application source is modified.

## 3. Executive Summary

### Overall finding

Both features have substantial source-level frontend implementation, but neither is ready for complete end-to-end sign-off.

Annual Maintenance has a real builder route, complete request DTO, preview controls, loading/empty/stale states, occurrence table, anchor-mode persistence on create, plan detail enrichment, permission-aware entry points, and localized UI. Its critical blocker is lifecycle alignment: current backend `PprPlanService.create` generates tasks immediately, the builder immediately starts approval, and frontend generation controls are available before approval but hidden after approval. That is the opposite of the required business flow “create with no tasks → chief engineer reviews/edits → approve → generate tasks.” In addition, editing a builder-created plan through the standard PPR edit dialog omits `anchorMode`; backend update assigns that omitted value as `null`, converting the plan to legacy behavior.

Parallel Approval has a real template editor for `SEQUENTIAL` and `PARALLEL_ALL`, active-user parallel selection, duplicate prevention, `currentUserTaskId` targeting, backend-authoritative `allowedActions`, `/approvals/my-tasks`, cancelled task rendering, flow badges, progress, and parallel approver cards. Critical gaps remain in approval-round/resubmission support, snapshot visibility, notification navigation, numeric progress completeness, and consistent rejection-comment enforcement across all action surfaces.

### Readiness

| Feature | DONE | PARTIAL | MISSING | IN_PROGRESS | BLOCKED | BACKEND_ONLY | Score |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Annual Maintenance Schedule | 13 | 13 | 3 | 1 | 2 | 0 | **61.7%** |
| Parallel Approval | 15 | 9 | 6 | 0 | 0 | 1 | **62.9%** |

### Fully implemented source-level items

- Annual preview endpoint and real builder consumer.
- Annual date, department, scope, target, and both anchor-mode controls.
- Annual occurrence table, loading/initial/empty/stale states, current response DTO mapping, plan detail anchor display, planned-date rendering, principal frontend limits, and `ru`/`uz`/`en` keys.
- Parallel template selection and editing for both flow types.
- Parallel active-user selection and duplicate prevention.
- `flowType`, progress/action contract fields, `currentUserTaskId`, `allowedActions`, `CANCELLED`, and `/approvals/my-tasks`.
- Backend-authoritative action buttons in the main approval list/detail and RETURN suppression for parallel flows.
- Sequential fallback to `SEQUENTIAL`.

### Critical blockers

1. **Required PPR lifecycle is contradicted by current implementation.** Backend creates tasks inside `PprPlanService.create`; frontend immediately submits approval; task generation is allowed for `DRAFT`/`GENERATED` and unavailable after `APPROVED`.
2. **Annual-plan edit can erase `anchorMode`.** The standard edit form neither stores nor sends it, while backend update writes `request.anchorMode()` directly.
3. **Parallel resubmission/round UI is absent.** A terminal approval remains the selected “active” display record, so the embedded section does not expose its create button; `approvalRound` is typed but not rendered.
4. **Approval notification navigation is unreliable.** `ApprovalRequest` notifications route to `/approvals?requestId=...`, but the approvals page does not consume `requestId`; task/round identifiers are not used.
5. **Snapshot fields are invisible.** `templateId` and `templateVersion` are typed but have no production UI consumer, preventing users from confirming immutable route provenance.

### Recommendation

**No-Go for full manual end-to-end verification.** Targeted exploratory tests may begin for annual preview, template configuration, My Tasks, and basic parallel decisions, but the required annual approval/generation business flow and parallel resubmission/round/notification flows are blocked or absent.

## 4. Annual Maintenance Schedule Audit

### 4.1 API and Types

`api.previewMaintenanceSchedule` calls `POST /maintenance-schedule/preview` from `src/lib/api.ts:1229-1236`, and `MaintenanceScheduleBuilderPage` invokes it in a real mutation at `src/modules/repairs/pages/maintenance-schedule-builder-page.tsx:159-174`.

The frontend request contract at `src/types/api.ts:3062-3070` maps all current backend request fields: `fromDate`, `toDate`, `scopeType`, equipment/equipment-type IDs, optional `departmentId`, and required `anchorMode`. The response contract at `src/types/api.ts:3072-3100` exactly matches the current backend records: occurrence items plus four summary counters.

The audit brief describes richer matched-work, missing-counter-reason, and unmatched-equipment summaries. The current backend DTO `MaintenanceSchedulePreviewSummary` exposes only `equipmentCount`, `totalOccurrences`, `missingMetersCount`, and `unmatchedCount`; therefore the frontend cannot display reason or equipment identity lists without a backend contract extension.

Shared `ApiError` preserves status, raw message, code/errorCode, and validation details (`src/lib/api.ts:786-873`), but the preview panel discards the supplied error object and renders only a generic localized message (`maintenance-schedule-preview-panel.tsx:33-45`). Unknown IDs, occurrence overflow, and server validation details are consequently not actionable in the preview UI.

### 4.2 Preview UI

The builder is a real page at `/maintenance-schedule-builder`, protected by `PPR_PLAN_CREATE` or `PPR_PLAN_GENERATE` (`src/app/routes.tsx:803-812`), linked from both the sidebar (`src/components/layout/app-shell.tsx:209-216`) and PPR toolbar (`ppr-calendar-toolbar.tsx:218-222`).

The form supports:

- plan name and notes (`maintenance-schedule-builder-form.tsx:72-96`);
- start/end dates (`:98-126`);
- locked own-department or elevated enterprise/department selection (`:128-159`);
- equipment or equipment-type scope (`:161-182`);
- multi-select targets with infinite loading/search (`:184-198`);
- `CURRENT` and `RESET_TO_PLAN_START` (`:200-229`).

The preview flow has:

- duplicate-click prevention (`maintenance-schedule-builder-page.tsx:300-305`);
- loading, generic error, initial, empty, stale, and ready states (`maintenance-schedule-preview-panel.tsx:21-91`);
- summary counters and a warning banner (`maintenance-schedule-summary.tsx:7-46`);
- grouped and paginated occurrences with planned date, maintenance kind, regulation, periodicity, anchor source, labor hours, and shutdown flag (`maintenance-schedule-preview-table.tsx:26-188`).

Gaps:

- no matched-work aggregate separate from occurrence rows;
- no missing-meter reasons or affected rule/equipment identities;
- no unmatched-equipment identities;
- preview failure hides raw/structured business errors;
- warnings are visually distinct, but cannot distinguish individual recoverable diagnostics from blocking server errors.

### 4.3 PPR Plan Integration

`anchorMode` is nullable in `PprPlanPayload`, summary, and detail types (`src/types/api.ts:3108-3164`). The builder includes it in create payloads and uses the successful preview request snapshot for dates, department, scope, targets, and anchor (`maintenance-schedule-builder/model.ts:196-222`). This prevents normal form drift between preview and save.

Plan detail:

- shows mixed schedule and localized anchor mode (`ppr-plan-summary-card.tsx:13-69`);
- reconstructs the preview only when a non-null anchor, dates, and homogeneous targets exist (`maintenance-schedule-builder/detail.ts:13-38`);
- leaves legacy `anchorMode = null` plans openable, without enrichment (`detail.ts:16`, `ppr-plan-summary-card.tsx:24-30`).

Critical edit defect:

- `PprPlanFormState` has no `anchorMode` (`ppr-plan-form.ts:22-39`);
- `buildPprPlanFormFromPlan` does not retain it (`:68-101`);
- `buildPprPlanPayload` omits it (`:135-157`);
- the standard calendar edit action opens any `DRAFT` plan in that form (`ppr-calendar-page.tsx:488-492`);
- backend `applyPlanContractFields` assigns `plan.setAnchorMode(request.anchorMode())`.

Therefore editing a DRAFT builder plan through the standard UI can clear the anchor and change it into legacy preventive-maintenance semantics. This is a high-risk data/behavior regression.

### 4.4 Generation Flow

The frontend calls `POST /ppr-plans/{id}/generate` through `api.generatePprTasks` (`src/lib/api.ts:1234-1239`). Because the backend chooses the new constructor when persisted `anchorMode != null`, the generic API call is contract-compatible.

Positive evidence:

- generation button is disabled while pending (`ppr-plan-header.tsx:103-109`, `ppr-plan-table-columns.tsx:176-183`);
- planned dates are displayed from enrichment `plannedDate` or task `dueDate` (`ppr-tasks-table.tsx:200-210`);
- backend repeat generation is protected by persisted occurrence signature and row locking.

Gaps:

- task-generation mutations only invalidate queries; they do not show created/skipped counts or generation diagnostics (`ppr-plan-detail-page.tsx:139-143`, `ppr-calendar-page.tsx:333-342`);
- task generation has no feature-specific `onError`, so conflict/lock and validation errors are not surfaced by these mutations;
- backend returns `PprGenerationResult`, including counts/diagnostics (`src/types/api.ts:3167-3214`), but the UI does not consume the result;
- duplicate protection cannot be visibly confirmed without API/DB inspection.

### 4.5 Chief Engineer Review and Approval Flow

#### Required flow comparison

| Required step | Current evidence | Status |
| --- | --- | --- |
| Create plan with no tasks | Frontend calls `createPprPlan`; backend `PprPlanService.create` immediately calls `generatorService.generateForPlan`. Builder then compares returned task count with preview (`maintenance-schedule-builder-page.tsx:203-230`). | BLOCKED |
| Separate chief-engineer review | Plan detail embeds `ApprovalSection` (`ppr-plan-detail-page.tsx:473-478`), but builder immediately starts approval after create (`maintenance-schedule-builder-page.tsx:203-213`). | PARTIAL |
| Chief engineer edits before approval | DRAFT edit exists under `PPR_PLAN_UPDATE` (`ppr-plan-table-columns.tsx:200-208`), but generated builder plans move to `GENERATED`, for which edit is hidden and backend update accepts only DRAFT. Standard edit also clears anchor. | PARTIAL |
| Chief engineer approves | Submission uses centralized approval; actions use approval permissions and backend action flags. | PARTIAL |
| Generate tasks only after approval | Frontend `canShowPprTaskGeneration` allows only `DRAFT`/`GENERATED`; backend generator rejects other states. After approval the task-generation button disappears. | BLOCKED |

The permission named for chief-engineer plan approval is `PPR_PLAN_APPROVE` (`ppr-calendar-access.ts:5-17`). Approval decision buttons additionally use generic approval access plus backend `allowedActions`. The builder commit gate requires create plus generate or approve permission (`maintenance-schedule-builder/access.ts:17-32`).

Current generation is manual from plan menus/details **before** approval, while creation also performs automatic backend generation. Approval itself does not trigger task generation. The expected business flow is therefore absent as an end-to-end sequence even though its individual plan, approval, and generation building blocks exist.

### 4.6 Access Control

Positive evidence:

- route and sidebar guards use permissions and shared wildcard/system-admin semantics (`routes.tsx:791-812`, `app-shell.tsx:201-216`, `access-control.ts:11-44`);
- regular users are locked to their own department; missing department creates a blocking state (`maintenance-schedule-builder/access.ts:14-34`, page `:345-361`);
- elevated users can choose enterprise or department scope;
- PPR list/detail/task/stat requests delegate authoritative visibility and department filtering to backend;
- action buttons are permission- and status-gated (`ppr-calendar-access.ts:5-19`, `ppr-plan-lifecycle.ts:22-45`).

Gaps:

- route/menu guards do not explain why a direct URL is forbidden beyond the generic unauthorized page;
- detail 403/404 handling is a generic failed-load empty state with raw `Error.message` (`ppr-plan-detail-page.tsx:429-436`), so intentionally hidden drafts are not clearly distinguished from missing resources;
- list, task, and statistics screens rely on backend filtering rather than displaying an explicit scope indicator;
- a user with only `PPR_PLAN_GENERATE` can open and preview the builder but cannot commit, which is safe but potentially confusing.

### 4.7 Validation and Error Handling

Frontend validation in `maintenance-schedule-builder/model.ts:128-175` covers:

- required/ordered dates;
- inclusive maximum of 366 calendar days;
- required target scope;
- maximum 1,000 selected targets;
- scope mismatch;
- required/valid anchor mode;
- department lock/required state.

IDs are trimmed, deduplicated, and empty values removed before sending (`model.ts:103-105`, `:177-190`). This prevents duplicate/null/empty list entries through the normal UI. Unknown IDs and occurrence overflow remain backend validations, which is appropriate, but the generic preview error prevents useful recovery. Missing counters and unmatched items are warning counts only.

All builder and parallel flow labels inspected have keys in Russian, Uzbek, and English (`src/i18n/locales/{ru,uz,en}.json`). Maintenance enum display uses localized keys rather than raw enum names. Server raw messages may still appear in create/update/detail toasts, while preview hides them entirely.

### 4.8 Requirement Matrix

| Requirement | Backend capability | Frontend evidence | Status | Gap | Business impact | Recommended next action |
| --- | --- | --- | --- | --- | --- | --- |
| A-01 Preview endpoint and consumer | POST preview | `api.ts:1229-1236`; builder page `:159-174` | DONE | Runtime not verified | Real user flow exists | Manually verify request/response |
| A-02 Request DTO completeness | Dates, scope, IDs, department, anchor | `types/api.ts:3062-3070`; `model.ts:177-190` | DONE | None in current DTO | Contract aligned | Keep generated contract tests |
| A-03 Current response DTO mapping | Items + four counters | `types/api.ts:3072-3100` | DONE | Richer brief fields are absent from backend DTO | Current JSON is retained | Extend both contracts if details are required |
| A-04 Business error mapping | 400/403/overflow/unknown ID | `ApiError` retains detail; preview panel ignores it | PARTIAL | Generic preview error | User cannot correct business input efficiently | Render `ApiError.rawMessage`/field details via localized mapping |
| A-05 Required controls | All form inputs | builder form `:72-229` | DONE | Runtime not verified | Preview can be configured | Manual test both anchors/scopes |
| A-06 Loading/initial/empty/stale | Stateless preview | preview panel `:21-91` | DONE | No retry action | Clear basic states | Add retry preserving form |
| A-07 Occurrence display | Calculated occurrences | preview table `:26-188` | DONE | Runtime not verified | Main preview value is visible | Manual data verification |
| A-08 Equipment summary | Equipment count | summary `:7-29` | DONE | Count only | Basic scope visibility | Keep |
| A-09 Matched-work summary | Matched maintenance work | Rows show kind/regulation | PARTIAL | No aggregate by kind/work | Review is slower at scale | Add matched-work aggregates |
| A-10 Missing counters | Missing active-meter detection | `missingMetersCount` warning | PARTIAL | No reasons/affected assets | Remediation target unknown | Extend backend DTO and render reason list |
| A-11 No applicable rules | Unmatched equipment | `unmatchedCount` warning | PARTIAL | No equipment identities | Cannot fix configuration from UI | Return/render unmatched equipment list |
| A-12 Warning vs blocking | Diagnostics + rejected input | Warning banner and generic error | PARTIAL | Diagnostics not structured | Users cannot prioritize fixes | Separate non-blocking diagnostics and blocking error cards |
| A-13 Anchor in create/types | Nullable plan anchor | `types/api.ts:3121,3139`; builder payload `model.ts:219` | DONE | None for create | New constructor can be selected | Manual persistence check |
| A-14 Anchor in update/legacy preservation | Nullable anchor, null = legacy | Standard edit omits anchor; backend overwrites | MISSING | Builder plan can lose anchor | High-risk semantic corruption | Add builder-specific edit or preserve immutable anchor snapshot |
| A-15 Plan detail/enrichment | Re-preview and plan DTO | summary card/detail helper | PARTIAL | No edit-safe builder detail; enrichment can fail | Review exists but is incomplete | Add explicit builder-plan edit/repreview flow |
| A-16 Preview/save snapshot consistency | Same constructor inputs | builder saves `previewRequest` | DONE | Runtime not verified | Prevents normal form drift | Keep fingerprint/snapshot design |
| A-17 Generic generation selects new backend path | Non-null anchor constructor | `generatePprTasks`; persisted anchor | DONE | UI does not explain constructor | Correct API path exists | Label generated schedule behavior |
| A-18 Generation result counts | created/skipped/diagnostics | Typed result, unused by task generation mutations | MISSING | No visible count/result | Duplicate generation cannot be evidenced | Toast/result panel with created/skipped/reasons |
| A-19 Repeat/double-click safety | Dedup + row lock | Pending disable; backend dedup | PARTIAL | No visible duplicate outcome | Safe backend behavior is opaque | Show result and disable per plan ID |
| A-20 Lock/conflict errors | Concurrent protection | No task-generation `onError` | MISSING | Conflicts not explained | Operators may retry blindly | Add localized conflict/error toast |
| A-21 Generated planned dates | Planned date persisted | task table `:200-210` | DONE | Runtime mapping not verified | Calendar occurrence remains visible | Compare preview vs task date manually |
| A-22 No tasks immediately on create | Required business rule | Backend create auto-generates | BLOCKED | Contract contradicts rule | Required review-before-generation impossible | Decide/fix backend lifecycle before frontend changes |
| A-23 Chief engineer review/edit | Draft update + approval | Edit exists only DRAFT; builder auto-submits | PARTIAL | Generated builder plans not editable; anchor loss | Reviewer cannot safely adjust plan | Create dedicated pre-approval builder edit |
| A-24 Separate approval stage | Central approval | Auto-start + `ApprovalSection` | PARTIAL | No deliberate handoff step | Review timing is ambiguous | Stop auto-submit or make business rule explicit |
| A-25 Generate only after approval | Required sequence | UI/backend generate only pre-approval | BLOCKED | Opposite status gate | Critical process violation | Move generation trigger to approval finalization or post-approval action |
| A-26 Status action gating | Backend status rules | `ppr-plan-lifecycle.ts:8-45` | PARTIAL | Gates reflect current backend, not required flow | UI faithfully exposes wrong lifecycle | Align after lifecycle decision |
| A-27 Route/menu/admin/wildcard | Access policies | routes/menu/access helpers | PARTIAL | Mixed preview/commit permission can confuse | Entry may lead to read-only dead end | Explain access state or tighten entry condition |
| A-28 Department/draft visibility and 403/404 | Backend filtering/404 | Locked department; generic detail error | PARTIAL | No distinct 403/404 UX/scope indicator | Support diagnosis is difficult | Add status-aware not-found/forbidden presentation |
| A-29 Principal frontend limits | 366 days, 1,000, duplicates/nulls | validation/normalization in `model.ts` | DONE | Selector-scale UX not verified | Prevents common bad requests | Manually boundary-test |
| A-30 Unknown ID/overflow/server diagnostics | Backend-only validations | Generic preview error | PARTIAL | No mapped reason | Cannot recover from large/invalid request | Map known backend error codes/messages |
| A-31 Localization | ru/uz/en and enums | locale files; localized enum lookup | DONE | Server raw messages remain possible | Core UI is localized | Add business-error key map |
| A-32 Modal/options redesign | Approved local design plus concurrent uncommitted test-first changes | local `68afdf68` spec; six modified tests and one new dialog test | IN_PROGRESS | No corresponding production modal/options endpoint client changes at final verification | Current page will be replaced | Implement only after lifecycle rule is resolved |

### 4.9 Readiness Score

Scored rows: 32; no `NOT_APPLICABLE` rows.

```text
DONE        13 × 1.00 = 13.00
PARTIAL     13 × 0.50 =  6.50
IN_PROGRESS  1 × 0.25 =  0.25
MISSING      3 × 0.00 =  0.00
BLOCKED      2 × 0.00 =  0.00
--------------------------------
Total points             = 19.75
Readiness = 19.75 / 32 × 100 = 61.7%
```

## 5. Parallel Approval Audit

### 5.1 API and Types

The frontend contract supports:

- `ApprovalFlowType = "SEQUENTIAL" | "PARALLEL_ALL"`;
- step `CANCELLED` and `approvalRound`;
- request `flowType`, `approvalRound`, `templateId`, `templateVersion`;
- `totalApprovers`, approved/pending/rejected/cancelled counts;
- `currentUserTaskId` and `allowedActions`;
- template flow type/version;
- `/approvals/my-tasks`.

Evidence: `src/types/api.ts:6543-6701` and `src/lib/api.ts:6072-6271`.

No naming mismatch was found against current backend DTOs. The main problem is consumption: `approvalRound`, `templateId`, and `templateVersion` have no production UI usage outside types. Counts other than approved/total are not presented numerically.

### 5.2 Template UI

The approval rules screen exposes both flow types (`approval-rules-page.tsx:757-775`).

Sequential:

- role steps can be added, reordered, and removed (`:848-978`);
- existing templates without `flowType` default to `SEQUENTIAL` (`approval-rule-form.ts:100-126`);
- unsupported explicit-user sequential templates are preserved read-only rather than silently rewritten.

Parallel:

- only active users are queried and filtered (`approval-rules-page.tsx:194-203`);
- multiple users can be checked (`:794-845`);
- `toggleParallelAssignee` prevents duplicates and renumbers steps (`approval-rule-form.ts:58-80`);
- payload validation requires non-empty, unique USER assignees and forbids roles (`:174-189`, `:232-243`);
- switching flow type replaces stale steps with the correct empty/default representation (`approval-rules-page.tsx:766-775`).

Version is retained and sent for edit, but not shown to the administrator. Backend error keys are partially localized through `toApprovalRuleAdministratorErrorKey`; unmatched error payload shapes fall back to a generic save failure.

### 5.3 Submission Flow

Documents start approval through `POST /approvals/start` (`api.ts:6196-6208`) from `CreateApprovalDialog` and embedded `ApprovalSection`. The start dialog shows the target document type and action, but it does not show the selected backend template, flow type, or template version before submission (`create-approval-dialog.tsx:50-171`).

The frontend does not recalculate route steps from the mutable template after start. Runtime screens render the approval request snapshot supplied by backend, which is correct. However, because template snapshot identifiers are invisible, users cannot prove which immutable template/version was used.

### 5.4 Approval Detail and Progress

The standalone detail shows:

- request title, target type, process status, start/completion time;
- parallel badge;
- approved/total progress;
- every step/approver with decision, timestamp, and comment through `ApprovalHistory`.

Evidence: `approval-detail-page.tsx:181-287`, `approval-history.tsx:159-303`.

Gaps:

- no numeric pending/rejected/cancelled totals;
- no request or step `approvalRound`;
- no template ID/version;
- no visual grouping of old vs current rounds;
- no explicit “current round” label;
- no automatic polling/live refresh; progress changes appear after local mutation invalidation or navigation/reload.

### 5.5 Allowed Actions

`isApprovalActionAllowed` prefers backend `allowedActions` and only falls back to legacy `canApprove/canReject/canCancel` when absent (`approval-ui-policy.ts:5-15`). Parallel task targeting uses `currentUserTaskId` (`constants.ts:15-21`, `runtime-route.ts:119-160`). RETURN is explicitly forbidden in frontend for `PARALLEL_ALL` (`approval-ui-policy.ts:25-30`).

Loading/double-submit protection exists on list/detail/embedded actions. Stale/invalid embedded routes are disabled through runtime integrity and actionable flags. The standalone detail relies on `allowedActions` but does not run the full runtime-integrity check.

Rejection comment:

- the main approval list dialog and standalone detail use `isApprovalDecisionSubmitDisabled`, requiring a comment for REJECT (`approval-decision-policy.ts:3-9`);
- embedded `ApprovalSection` only requires a comment for RETURN, not REJECT (`approval-section.tsx:632-650`).

Thus mandatory rejection comments are inconsistent across production surfaces.

### 5.6 My Tasks

`ApprovalsPage` defaults to PENDING with no search and calls `GET /approvals/my-tasks` (`approvals-page.tsx:28-72`). It provides pagination, action buttons, status tabs, document links, and error state.

Gaps:

- the page/menu is named general “Approvals,” not clearly “My Tasks”;
- entering search or choosing another status switches to the global `/approvals` endpoint, so search does not filter “my tasks”;
- completed/cancelled tabs are not the user’s task history; they are the broader approval list subject to backend access;
- no feature-specific empty-state component is rendered when the page is empty;
- navigation for targets that lack a detail mapping falls back to list-level pages.

### 5.7 Notifications

Frontend notification routing recognizes `entityType = "ApprovalRequest"` but routes to `/approvals?requestId={id}` (`notification-routing.ts:12-17`). `ApprovalsPage` never reads `requestId`, so the intended request is not focused or opened. The router does not use approval task ID or `approvalRound`.

Backend fan-out to all parallel approvers, stale cancellation after another user rejects, and old-round notification semantics are therefore `BACKEND_ONLY` from a UI integration perspective. A backend-provided `metadata.actionPath` is honored, which can mitigate the issue only if every approval notification supplies a correct path.

### 5.8 Rejection Flow

| Step | Frontend evidence | Status |
| --- | --- | --- |
| Parallel process active | Flow badge/progress/step cards render | DONE |
| One approver selects REJECT | `allowedActions` + `currentUserTaskId` target decision | DONE |
| Comment mandatory | List/detail enforce; embedded section does not | PARTIAL |
| Process becomes REJECTED | Mutation invalidates approval queries and status badge supports REJECTED | PARTIAL |
| Remaining tasks become CANCELLED | CANCELLED is typed and rendered in history/progress | DONE |
| Other approvers lose actions | Backend `allowedActions`/My Tasks drives buttons; refresh required | PARTIAL |
| UI refreshes progress/status | Local mutation invalidation exists; no polling for other sessions | PARTIAL |

### 5.9 All-Approve Flow

Independent per-user task targeting is correctly based on `currentUserTaskId`. Progress shows approved/total and step decisions; the final backend response can switch the process to APPROVED and remove actions via `allowedActions`. However, another user’s approval does not automatically update an already-open page, and pending/rejected/cancelled counts are not shown numerically. This flow is `PARTIAL` pending real multi-session verification.

### 5.10 Resubmission and Approval Rounds

Backend supports new `approvalRound` on resubmission, but production frontend does not expose a reliable resubmit path:

- `ApprovalSection` defaults `allowCreate = true`, but it shows the create button only when `active` is null (`approval-section.tsx:214-224`, `:431-447`);
- `findDisplayApprovalForTarget` returns the newest matching terminal approval when no pending request exists (`approval-target.ts:67-87`);
- therefore a rejected/returned request remains “active” for display and suppresses the create button;
- `approvalRound` has no production consumer;
- historical approvals show date and status only, not round number, steps, or snapshot;
- no verification ensures `currentUserTaskId` belongs to the displayed round.

This blocks APR-MAN-11 and APR-MAN-12 from the current UI.

### 5.11 Sequential Regression

Sequential support remains present:

- missing flow type normalizes to `SEQUENTIAL` (`approval-flow-type.ts:11-31`, `approval-rule-form.ts:117`);
- runtime validation keeps one current step based on `currentStep` (`runtime-route.ts:162-194`);
- RETURN is available only when backend legacy `canReturn` is true and flow is not parallel (`approval-ui-policy.ts:25-30`);
- existing role-step template editing is retained;
- CANCELLED/RETURNED decisions are rendered.

Runtime regression is not verified because execution was explicitly skipped.

### 5.12 Requirement Matrix

| Requirement | Backend capability | Frontend evidence | Status | Gap | Business impact | Recommended next action |
| --- | --- | --- | --- | --- | --- | --- |
| B-01 Flow type contracts | SEQUENTIAL/PARALLEL_ALL | `types/api.ts:6547` | DONE | Runtime not verified | Contract can deserialize both | Manual contract test |
| B-02 Round/snapshot/progress/action fields | Enriched response | `types/api.ts:6659-6669` | DONE | Some fields unused | No data loss at type boundary | Add UI consumers |
| B-03 My Tasks API | GET `/approvals/my-tasks` | `api.ts:6130-6140` | DONE | Pending-only params only | Endpoint integrated | Keep |
| B-04 Flow selection | Both template modes | rules page `:757-775` | DONE | No explanatory comparison panel | Admin can choose mode | Add concise flow guidance |
| B-05 Sequential steps | Ordered role route | rules page `:848-978` | DONE | Runtime not verified | Legacy authoring preserved | Regression test manually |
| B-06 Parallel assignees | One task per unique active user | active-user query + toggle helper | DONE | Page fetch capped at 500 users | Valid common-case configuration | Add server search/pagination for large tenants |
| B-07 Switch flow clears stale data | Different step models | rules page `:766-775` | DONE | Unsaved values are discarded without warning | Prevents invalid mixed payload | Optional confirmation on destructive switch |
| B-08 Safe template edit/version | Optimistic version | form retains/sends version | PARTIAL | Version hidden; some legacy sequential templates read-only | Conflict is hard to diagnose | Display version and refresh action |
| B-09 Template validation errors | Duplicate/inactive/version checks | local validation + error map | PARTIAL | Not every backend payload maps to localized key | Admin may see generic failure | Map `ApiError.errorCode/rawMessage` consistently |
| B-10 Submission shows template/flow | Snapshot chosen at start | start dialog shows target/action only | MISSING | No pre-submit route visibility | Requester cannot confirm route | Add read-only route preview endpoint/UI |
| B-11 No mutable-template dependency | Snapshot at request start | runtime renders request steps/flow | DONE | Snapshot identifiers invisible | Runtime route itself is immutable | Keep request-driven rendering |
| B-12 Detail approvers/timestamps/comments | Parallel tasks | detail + history cards | PARTIAL | Round/snapshot/counter gaps | Review lacks full provenance | Add metadata/progress card |
| B-13 Full progress counts | Five progress counts | only approved/total numeric | PARTIAL | Pending/rejected/cancelled not numeric | Harder to assess rejection/cancellation | Render all response counts |
| B-14 Current task and allowed actions | Per-user task/actions | constants/runtime/ui-policy | DONE | Embedded/standalone policies differ slightly | Correct task can be targeted | Consolidate one decision policy |
| B-15 Approve/reject visibility | Server-authorized decisions | list/detail/section buttons | DONE | Runtime not verified | Prevents obvious unauthorized actions | Multi-user manual test |
| B-16 Mandatory rejection comment | Parallel reject requires comment | list/detail yes; embedded no | PARTIAL | Inconsistent surface | Embedded reject causes avoidable 400 | Reuse decision policy in `ApprovalSection` |
| B-17 RETURN forbidden in parallel | Backend rejects RETURN | `canReturnApproval` | DONE | None found | Invalid action hidden | Keep |
| B-18 Stale/double-submit/conflict | Locks/unique constraints | pending disable, integrity checks, error handlers | PARTIAL | No unified 409 presentation/polling | Concurrent user may see stale screen | Normalize conflict UX and refetch |
| B-19 My Tasks route/menu/pagination | Pending personal tasks | `/approvals`, API switch, DataTable | DONE | Label is generic | Usable queue exists | Rename/subtitle as My Tasks when pending |
| B-20 My Tasks filters/empty/history | Queue UX | status/search switches to global list | PARTIAL | Not true personal filtering/history; no explicit empty state | User can misinterpret scope | Keep my-tasks endpoint for search/history or separate screens |
| B-21 Notification target navigation | Notify every parallel approver | `/approvals?requestId=` not consumed | MISSING | No task/round focus | User may land on wrong list/state | Route directly to `/approvals/{id}` and validate current task/round |
| B-22 Full rejection refresh | REJECTED + cancel peers | invalidations and CANCELLED rendering | PARTIAL | No cross-session live refresh; embedded comment bug | Other approvers can see stale actions until refresh | Poll/refetch on focus and on 409 |
| B-23 Full all-approve refresh | Final approve after everyone | step decisions/progress/final status supported | PARTIAL | No live update and incomplete numeric counts | Pending page can lag final decision | Add polling/event invalidation |
| B-24 Resubmission | New round after reject/return | create button suppressed by terminal display record | MISSING | No explicit resubmit path | Rejected documents cannot restart in UI | Add resubmit action tied to document lifecycle |
| B-25 Approval round display | `approvalRound` snapshot | typed only | MISSING | Current round invisible | Cannot prove isolation | Render request/step round |
| B-26 Template snapshot display | template ID/version | typed only | MISSING | Provenance invisible | Auditability gap | Show template name/ID/version from request |
| B-27 Old-round separation | Old tasks inactive | history shows only approval date/status | MISSING | No grouped old-round steps | Users may confuse decisions | Group histories by round and mark inactive |
| B-28 Sequential regression | Legacy/default sequential | fallback/runtime/RETURN policy | DONE | Runtime not verified | Existing flows remain represented | Manual regression scenario |
| B-29 CANCELLED task status | Peer cancellation | type/history/progress rendering | DONE | Runtime not verified | Rejection result can be displayed | Multi-user rejection test |
| B-30 Notification fan-out and stale-round semantics | Backend sends to all | no round-aware frontend mapping | BACKEND_ONLY | UI does not consume semantics | Notification evidence cannot be completed | Define notification metadata contract |
| B-31 Flow badges in lists/entities | Snapshot flow type | badge in approval/template/entity tables | DONE | None found | Users can distinguish route mode | Keep |

### 5.13 Readiness Score

Scored rows: 31; no `NOT_APPLICABLE` rows.

```text
DONE         15 × 1.00 = 15.00
PARTIAL       9 × 0.50 =  4.50
MISSING       6 × 0.00 =  0.00
BACKEND_ONLY  1 × 0.00 =  0.00
--------------------------------
Total points              = 19.50
Readiness = 19.50 / 31 × 100 = 62.9%
```

## 6. Current In-Progress Work

| Evidence | Current implementation state | Classification | Expected completion work |
| --- | --- | --- | --- |
| Frontend HEAD `68afdf68`, approved modal design, plus concurrent uncommitted tests covering options API, modal CTA/deep link, automatic preview-before-create, eligibility copy, legacy redirect, and dialog contract | Current production still uses separate `/maintenance-schedule-builder`, generic equipment APIs, manual preview, preview table, summary, and nested commit dialog; no matching production changes were present at final verification | IN_PROGRESS | Implement the production modal entry, query-param deep link, eligibility options client, automatic preview-before-create, and legacy redirect/menu removal, then verify the tests |
| Backend local commit `8cc1f17b`: `MaintenanceScheduleController`, new `MaintenanceScheduleOption`, new `MaintenanceScheduleEligibilitySelector`, service wiring, and tests | Committed backend implementation for the options/eligibility portion of the approved modal design; frontend has no `/maintenance-schedule/options` API function or consumer | BACKEND_ONLY; frontend remains MISSING | Review/publish backend commit, then add typed frontend client, infinite option loading, eligibility counts, and modal consumption |
| Linked `agent/maintenance-schedule-builder` and `agent/approval-flow-type-table-badges` worktrees | Their commits are already merged into current HEAD | DONE, not in progress | Remove/prune worktrees only as a separate maintenance task; not part of this audit |
| Relevant source TODO/FIXME/disabled-placeholder scan | No concrete feature TODO/FIXME or permanently disabled production control was found for these features | NOT_APPLICABLE | Track missing work from this audit rather than inferring from placeholders |

The approved modal design explicitly preserves automatic task generation inside plan creation. That design conflicts with the business flow required by this audit. Resolve the lifecycle decision before implementing the modal, otherwise the new UX will harden the wrong sequence.

## 7. Backend-Only Capabilities

### Annual Maintenance

- operational-equipment selection;
- effective-rule selection;
- manual-only/usage-only exclusion;
- deterministic sorting;
- responsible-department then location-department fallback;
- unknown ID, duplicate/null ID, horizon, scope-size, and occurrence-overflow rejection;
- planned-date deduplication signature;
- `SELECT ... FOR UPDATE` plan locking;
- repeat-generation protection;
- backend PPR status/department visibility enforcement.
- local backend commit `8cc1f17b` adds `/maintenance-schedule/options` and a shared eligibility selector after the specified audit references; it is not integrated by the frontend.

Frontend either depends on these behaviors without exposing them or shows only aggregate results. They require real API/database verification.

### Parallel Approval

- database locking and uniqueness protection for concurrent decisions;
- final document transition only after all approvals;
- automatic cancellation of remaining tasks after one rejection;
- approval task creation/notification fan-out to every parallel approver;
- approval-round increment and old-round isolation;
- flow/template/version snapshot persistence;
- migration default of `SEQUENTIAL`.

The frontend has partial representations for most decision results, but notification fan-out and round/snapshot semantics are not integrated as user-visible features.

## 8. Missing Frontend Work

Prioritized missing work:

1. Preserve `anchorMode` and builder semantics in plan edit.
2. Implement the agreed annual lifecycle after resolving backend business behavior.
3. Display task-generation created/skipped/diagnostic results and conflicts.
4. Add parallel resubmission and current/old round presentation.
5. Display template snapshot and complete progress counts.
6. Fix approval notification deep links and make them task/round aware.
7. Apply mandatory rejection comments and one unified stale/conflict policy to every approval surface.
8. Render structured maintenance preview error details and diagnostic identities/reasons.
9. Separate “My Tasks” from the global approval list and add a real empty state.

## 9. Critical and High-Risk Findings

| Severity | Finding | Evidence | Risk |
| --- | --- | --- | --- |
| Critical | Annual create/approve/generate lifecycle contradicts required business flow | backend `PprPlanService.create`; frontend builder `:203-213`; lifecycle helper `:8-45` | Tasks exist before review, and post-approval generation is impossible |
| Critical | Standard edit clears annual `anchorMode` | `ppr-plan-form.ts`; backend `setAnchorMode(request.anchorMode())` | Builder plan can silently change constructor/meaning |
| High | Parallel resubmission suppressed by terminal record selection | `approval-section.tsx:431-447`; `approval-target.ts:67-87` | Rejected workflow cannot start a new round |
| High | Approval round and snapshot fields unused | only `types/api.ts` references | Users cannot prove current round or immutable route |
| High | Notification approval deep link is not consumed | `notification-routing.ts:12-17`; `approvals-page.tsx` | Approvers may open wrong request/round |
| High | Embedded reject allows blank comment | `approval-section.tsx:632-650` | Preventable backend error and inconsistent policy |
| High | Generation result/error is invisible | PPR mutations only invalidate | Duplicate/concurrent generation cannot be operationally evidenced |
| Medium | Preview errors are generic | preview panel ignores `error` | Unknown IDs/overflow/configuration errors are hard to fix |
| Medium | Rich missing-counter/unmatched details unavailable in current backend DTO | four-field backend summary | Frontend cannot guide remediation |

## 10. Manual Test Preconditions

- A deployed frontend built from the audited `Codex_org` source and backend at/after the named commits.
- Isolated non-production database with audit/log access.
- Browser network inspector and server log access.
- Users:
  - system admin or wildcard;
  - regular user with a department;
  - regular user without a department;
  - chief engineer with `PPR_PLAN_UPDATE`, `PPR_PLAN_APPROVE`, and generic approval decision permissions;
  - PPR generator with `PPR_PLAN_GENERATE`;
  - at least three active parallel approvers;
  - one inactive user;
  - one user without approval permission.
- Annual data:
  - operational equipment with effective calendar rules;
  - equipment with a missing active meter/counter;
  - equipment without applicable rules;
  - equipment outside the user’s department;
  - a legacy PPR plan with `anchorMode = null`;
  - enough seeded rules/equipment to exercise limits where practical.
- Parallel data:
  - editable approval template target;
  - approvable documents for parallel and sequential routes;
  - document that can be rejected and later resubmitted;
  - notification delivery enabled for all parallel approvers.
- Database queries or backend admin inspection must confirm task counts, statuses, approval rounds, snapshot IDs/versions, and old-round isolation. The frontend alone does not expose all required evidence.

## 11. Manual Test Scenarios

### Annual Maintenance Schedule

| Scenario | Preconditions / role / data | Navigation and exact UI actions | Expected API call | Expected UI result | Expected business/database result | Screenshot evidence | Pass / fail / blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| PPR-MAN-01 — CURRENT preview | Admin or department user with builder access; operational equipment with calendar rule | Open `/maintenance-schedule-builder`; enter name/dates; select department/scope/equipment; select CURRENT; click Calculate | `POST /api/v1/maintenance-schedule/preview` with `anchorMode=CURRENT` | Loading then counters and occurrence rows with dates/anchor source | No plan/tasks written by preview | `PPR_MAN_01_01_INPUT.png`, `PPR_MAN_01_02_PREVIEW_RESULT.png` | Pass if payload and rows match backend calculation; fail on wrong scope/date/anchor |
| PPR-MAN-02 — RESET_TO_PLAN_START preview | Same equipment, known plan-start cadence | Repeat with RESET_TO_PLAN_START | Preview with reset anchor | Rows show localized reset behavior and plan-start anchor source | No writes | `PPR_MAN_02_01_INPUT.png`, `PPR_MAN_02_02_PREVIEW_RESULT.png` | Pass if dates match reset calculation; fail if same as CURRENT when data should differ |
| PPR-MAN-03 — Missing counters | Equipment/rule requiring active counter but none exists | Select affected target; Calculate | Preview | `missingMetersCount > 0` and warning banner | No task created for usage-only/missing-meter calculation per backend rules | `PPR_MAN_03_01_WARNING.png` | Pass for count/banner; fail if omitted. Reason identity cannot be verified in UI: frontend gap |
| PPR-MAN-04 — Equipment without applicable rules | Operational equipment with no applicable effective calendar rule | Preview that target | Preview | Empty or reduced occurrences; `unmatchedCount > 0` | No occurrence/task for unmatched equipment | `PPR_MAN_04_01_UNMATCHED.png` | Pass for count; affected equipment identity is not exposed: frontend gap |
| PPR-MAN-05 — Unknown equipment ID | Valid account; unknown UUID | No normal UI action can select an unknown UUID | Would require manipulated preview request | Expected backend 400 cannot be initiated through UI | No writes | None | **BLOCKER:** selector prevents unknown IDs and audit rules prohibit direct API manipulation; test as API contract, not frontend E2E |
| PPR-MAN-06 — Create without immediate tasks | Valid non-empty preview; creator permission | Preview; click Create/submit confirmation; open created plan detail | `POST /ppr-plans`, then `/approvals/start` | Required: created plan with zero tasks | Required: no PPR tasks until approval | `PPR_MAN_06_01_COMMIT.png`, `PPR_MAN_06_02_PLAN_CREATED_NO_TASKS.png` | Expected to fail on current backend because create generates tasks; critical blocker |
| PPR-MAN-07 — Chief engineer review/edit | DRAFT builder plan; chief engineer update permission | Open `/ppr-calendar?view=table`; plan actions → Edit; change period/target/anchor; save; re-open detail | `PATCH /ppr-plans/{id}` preserving anchor | Review/edit controls available and preview invalidated/refreshed | Plan remains DRAFT, no tasks; anchor preserved | `PPR_MAN_07_01_EDIT.png`, `PPR_MAN_07_02_UPDATED_PREVIEW.png` | **BLOCKER:** generated builder plan is not editable and standard edit omits anchor |
| PPR-MAN-08 — Chief engineer approval | Review-complete plan and pending approval task | Open plan detail Approval section or `/approvals`; approve | `/approvals/{id}/steps/{taskId}/approve` | Plan/process shows APPROVED; actions refresh | Approval audit record finalized; no tasks yet per required flow | `PPR_MAN_08_01_PENDING.png`, `PPR_MAN_08_02_APPROVED.png` | Pass only if correct user/task; business no-task condition is blocked by current create behavior |
| PPR-MAN-09 — Generate after approval | APPROVED annual plan; generator permission | Open plan detail; click Generate tasks | `POST /ppr-plans/{id}/generate` | Created/skipped counts and tasks with planned dates | One task per occurrence, deduplicated | `PPR_MAN_09_01_APPROVED_PLAN.png`, `PPR_MAN_09_02_GENERATION_RESULT.png`, `PPR_MAN_09_03_TASKS_GENERATED.png` | **BLOCKER:** button is hidden and backend rejects APPROVED status |
| PPR-MAN-10 — Duplicate generation | Existing annual plan with generated tasks | Trigger generate twice sequentially | Two generate calls | Second result should show created 0/skipped duplicates | Task count unchanged; no duplicate signatures | `PPR_MAN_10_01_FIRST_RESULT.png`, `PPR_MAN_10_02_SECOND_RESULT.png` | Backend can be checked, but frontend does not show result counts: partial/blocker for PM evidence |
| PPR-MAN-11 — Department access | Regular users in two departments plus no-department user | Open builder/list/detail under each account; attempt other-department direct URL | Preview/list/detail endpoints | Own scope only; no-department user blocked/403; inaccessible draft shown as not found | No cross-department reads/writes | `PPR_MAN_11_01_OWN_SCOPE.png`, `PPR_MAN_11_02_NO_DEPARTMENT.png`, `PPR_MAN_11_03_HIDDEN_DRAFT.png` | Pass if backend scope holds; note frontend generic 404/403 presentation |
| PPR-MAN-12 — Legacy plan without anchorMode | Existing legacy plan with `anchorMode=null` | Open list/detail; edit allowed fields; save | GET/PATCH plan with null legacy anchor | Detail opens without builder enrichment; legacy schedule labels remain | Legacy generator behavior preserved | `PPR_MAN_12_01_LEGACY_DETAIL.png`, `PPR_MAN_12_02_LEGACY_EDIT.png` | Pass if no forced anchor and no crash |
| PPR-MAN-13 — Validation and limits | Builder access; large target dataset | Test end before start; 367 inclusive days; empty target; then 366 days and up to practical selection limit | No call for invalid local forms; preview for valid boundary | Inline validation; disabled commit without fresh non-empty preview | Backend rejects >1,000/null/duplicate/overflow if reached | `PPR_MAN_13_01_DATE_ERROR.png`, `PPR_MAN_13_02_SCOPE_ERROR.png`, `PPR_MAN_13_03_LIMIT_ERROR.png` | Pass for local boundaries; overflow and unknown/null IDs need API-level tests |

### Parallel Approval

| Scenario | Preconditions / role / data | Navigation and exact UI actions | Expected API call | Expected UI result | Expected business/database result | Screenshot evidence | Pass / fail / blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| APR-MAN-01 — Create PARALLEL_ALL template | Approval admin; 3 active users | `/approvals/rules` → Templates → Create; choose target/action/PARALLEL_ALL; check users; Save | `POST /approval-templates` with flow and USER steps | Parallel badge and assignee count/list | Template persisted with version and unique users | `APR_MAN_01_01_PARALLEL_TEMPLATE.png`, `APR_MAN_01_02_TEMPLATE_SAVED.png` | Pass if response/list match |
| APR-MAN-02 — Duplicate approver validation | Editable parallel template | Toggle same user repeatedly; attempt crafted duplicate only if supported by test harness | Normal UI payload contains unique IDs | User appears once; save remains valid | Backend also rejects duplicate IDs | `APR_MAN_02_01_UNIQUE_SELECTION.png` | Pass UI prevention; direct duplicate rejection is API-level |
| APR-MAN-03 — Inactive approver validation | One inactive user | Open parallel editor and search/inspect list | `GET /users?status=ACTIVE...` | Inactive user absent | Backend rejects inactive user if bypassed | `APR_MAN_03_01_ACTIVE_ONLY.png` | Pass if absent; backend bypass test is API-level |
| APR-MAN-04 — Submit document | Active parallel template and eligible document | Open document detail Approval section; Send for approval; confirm | `POST /approvals/start` | New pending approval and parallel badge after refresh | Request snapshots flow/template/version; all tasks created same round | `APR_MAN_04_01_SUBMIT.png`, `APR_MAN_04_02_PARALLEL_PENDING.png` | Pass request creation; selected template/flow is not shown before submit: frontend gap |
| APR-MAN-05 — All approvers receive tasks | Three approver accounts | Sign in as each; open `/approvals` default Pending | `GET /approvals/my-tasks` per user | Same document appears with each user’s allowed actions | Three separate PENDING tasks; notifications to all | `APR_MAN_05_01_USER_A_TASK.png`, `APR_MAN_05_02_ALL_TASKS_VISIBLE.png` | Pass only with per-user API/DB and notification evidence |
| APR-MAN-06 — Partial progress | Active parallel request | User A approves; user B reloads detail | Step approve then GET approval/my-tasks | Approved/total increments; document remains PENDING | Only A task approved; others pending | `APR_MAN_06_01_USER_A_APPROVES.png`, `APR_MAN_06_02_PARTIAL_PROGRESS.png` | Pass if no premature document approval |
| APR-MAN-07 — Final APPROVED | All but last already approved | Last approver approves | Final step approve | Process/document APPROVED; action buttons gone | Every task approved once; finalizer executed once | `APR_MAN_07_01_LAST_PENDING.png`, `APR_MAN_07_02_LAST_APPROVAL.png`, `APR_MAN_07_03_FINAL_APPROVED.png` | Pass if no duplicate action remains |
| APR-MAN-08 — Mandatory rejection comment | Pending parallel user task | Open Reject dialog; leave blank; attempt submit; then enter reason and submit | No call while blank; reject call with comment | Blank submit disabled on list/detail; rejection succeeds with text | Comment persisted; process rejected | `APR_MAN_08_01_COMMENT_REQUIRED.png`, `APR_MAN_08_02_REJECTED.png` | Embedded document `ApprovalSection` currently allows blank submit: fail on that surface |
| APR-MAN-09 — One rejection cancels peers | Three pending users | User A rejects with comment; reload B/C My Tasks and detail | Reject + subsequent GETs | REJECTED status; peer actions disappear; CANCELLED cards shown | One REJECTED, remaining PENDING tasks become CANCELLED | `APR_MAN_09_01_REJECT.png`, `APR_MAN_09_02_PEER_NO_ACTION.png`, `APR_MAN_09_03_REJECTED_AND_CANCELLED.png` | Pass after cross-session refresh; stale open pages require manual reload |
| APR-MAN-10 — RETURN unavailable | Active parallel request | Inspect list, detail, embedded Approval section | GET only | No RETURN control | Backend would reject RETURN | `APR_MAN_10_01_NO_RETURN.png` | Pass if absent everywhere |
| APR-MAN-11 — Resubmission creates new round | Rejected document eligible for resubmit | Open document detail and look for resend/resubmit | Expected `/approvals/start` | Required new pending round label | `approvalRound` increments; new tasks only | `APR_MAN_11_01_REJECTED_DOCUMENT.png`, `APR_MAN_11_02_NEW_APPROVAL_ROUND.png` | **BLOCKER:** current embedded section suppresses create/resubmit when terminal request exists |
| APR-MAN-12 — Old round isolation | Two-round document | Open approval detail/history; inspect both rounds; decide current task | Current-round GET/decision | Old round visible but inactive and separated | Old tasks cannot affect new result | `APR_MAN_12_01_ROUND_HISTORY.png`, `APR_MAN_12_02_CURRENT_ROUND_ACTION.png` | **BLOCKER:** no round display/grouping and no frontend resubmit path |
| APR-MAN-13 — My Tasks | User with pending tasks and another user’s task | `/approvals`; paginate; search; switch tabs | Pending/no-search uses my-tasks; search/status uses global approvals | Personal pending queue, actions, document links, error/empty behavior | Only actionable current-user tasks in my-tasks response | `APR_MAN_13_01_MY_TASKS.png`, `APR_MAN_13_02_EMPTY_OR_PAGE.png` | Partial: search/status cease to be personal task filters |
| APR-MAN-14 — Notification navigation | Notifications delivered to every approver | `/notifications`; open approval notification for each user | Notification list/read; route navigation | Correct current request/task/round opens | Notification targets current round and actionable task | `APR_MAN_14_01_NOTIFICATION.png`, `APR_MAN_14_02_NAV_TARGET.png` | Likely fail unless backend supplies `metadata.actionPath`; fallback query param is not consumed |
| APR-MAN-15 — Sequential regression | Legacy/no-flow template and sequential document | Create/start sequential request; approve first; verify next; RETURN when backend allows | Start/step approve/return | One active step at a time; default sequential badge; return available only when allowed | Legacy route/order preserved | `APR_MAN_15_01_SEQUENTIAL_PENDING.png`, `APR_MAN_15_02_NEXT_STEP.png`, `APR_MAN_15_03_RETURN.png` | Pass only after full regression run |
| APR-MAN-16 — Concurrent/stale decision | Same parallel task open in two sessions or duplicate tabs | Submit approve/reject nearly simultaneously; retry stale tab | Competing step decision calls | One success; other gets localized conflict and actions refresh | One terminal decision; no duplicate finalization | `APR_MAN_16_01_CONCURRENT.png`, `APR_MAN_16_02_STALE_CONFLICT.png` | Partial: locking is backend; conflict UX is not uniformly specialized |

## 12. Screenshot Evidence Plan

Minimum PM-ready evidence:

- Annual: input plus result for both anchors; missing-counter warning; unmatched warning; created-plan task state; chief-engineer edit/approval; generation result and tasks; duplicate result; department denial; legacy detail; validation boundaries.
- Parallel: saved parallel template; each approver’s My Tasks entry; partial and final progress; blank-reject validation; rejected plus cancelled peer tasks; absent RETURN; new round and old-round separation; notification landing; sequential next-step behavior; stale conflict.

Use the exact names listed in each scenario. Do not capture duplicate screenshots when one image proves multiple low-risk assertions. Never treat a screenshot of code or an API client definition as runtime evidence.

## 13. Recommended Implementation Order

1. **Resolve and document the annual business lifecycle.** Decide whether task generation occurs on plan create, approval finalization, or a post-approval user action. Align backend status rules first.
2. **Prevent anchor loss immediately.** Hide standard edit for builder plans or implement an anchor-preserving builder edit flow with fresh preview.
3. **Align PPR UI actions to the decided lifecycle.** Update review/edit/approve/generate gates and show created/skipped/diagnostic results plus conflicts.
4. **Implement parallel resubmission and round model.** Add explicit resubmit, current-round selection, old-round grouping, and current-task validation.
5. **Expose approval provenance/progress.** Show flow type, all counts, approval round, template ID/name/version, and immutable snapshot information.
6. **Fix notification deep links.** Prefer `/approvals/{approvalId}` with current task/round metadata; consume backend action paths consistently.
7. **Unify decision validation/error handling.** Reuse mandatory reject-comment, stale-integrity, loading, and 409-refresh policies in list, detail, and embedded section.
8. **Improve annual diagnostics.** Extend backend preview DTO if reason/equipment lists are required, then render structured warnings and business errors.
9. **Implement the approved modal/options redesign** only after steps 1-3, revising the design where it currently assumes immediate generation.
10. **Run the manual plan** with multi-user real data and collect the named screenshots plus DB/audit evidence.

## 14. Final Go / No-Go Recommendation

### Annual Maintenance Schedule

**No-Go** for the required end-to-end business flow. Preview-only exploratory testing is reasonable, but creation/review/approval/post-approval generation cannot satisfy the stated process in the current backend/frontend lifecycle. The anchor-loss edit defect also makes chief-engineer editing unsafe.

### Parallel Approval

**Conditional Go** for template creation, My Tasks, first-round approve/reject, and sequential regression. **No-Go** for resubmission, approval-round isolation, snapshot auditability, and notification navigation until the missing UI paths are implemented.

### Combined PM recommendation

**No-Go for full manual end-to-end sign-off.** Begin only the non-blocked targeted scenarios, clearly marking them exploratory and retaining all API/DB evidence. Do not report either feature as complete from source presence alone.

## 15. Verification Status

- Source code inspection: completed
- Git history inspection: completed
- Application execution: not run/skipped by user instruction
- Automated tests: not run/skipped by user instruction
- Build/lint/typecheck: not run/skipped by user instruction
- Real API verification: not run
- Database verification: not run
- Manual end-to-end verification: pending

No application source code, tests, or migrations were modified by this audit. Only this audit Markdown document was created by the audit. The final working tree also contains concurrent uncommitted maintenance-schedule test changes that were not created or altered by the audit.
